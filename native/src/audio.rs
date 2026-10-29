use std::collections::VecDeque;
use std::io::{Cursor, Read, Seek, SeekFrom};
use std::sync::atomic::{AtomicBool, AtomicI32, AtomicU32, AtomicU64, Ordering};
use std::sync::mpsc::{channel, Receiver, RecvTimeoutError, Sender};
use std::sync::{Arc, Mutex};
use std::time::{Duration, Instant};

use cpal::traits::{DeviceTrait, HostTrait, StreamTrait};
use symphonia::core::audio::SampleBuffer;
use symphonia::core::codecs::DecoderOptions;
use symphonia::core::errors::Error as SymError;
use symphonia::core::formats::FormatOptions;
use symphonia::core::io::{MediaSource, MediaSourceStream};
use symphonia::core::meta::MetadataOptions;
use symphonia::core::probe::Hint;

use crate::log::log;

const OUT_CH: usize = 2;
const TARGET_SECS: f32 = 1.6;
const CAP_SECS: f32 = 4.0;

pub enum Cmd {
    Play { gen: u64, url: String },
    Stop,
    Quit,
}

struct Shared {
    buf: Mutex<VecDeque<f32>>,
    playing: AtomicBool,
    loading: AtomicBool,
    gen: AtomicU64,
    volume: AtomicI32,
    out_rate: AtomicU32,
    error: Mutex<Option<String>>,
}

pub struct Audio {
    sh: Arc<Shared>,
    tx: Sender<Cmd>,
    _stream: cpal::Stream,
}

pub struct Snapshot {
    pub playing: bool,
    pub loading: bool,
    pub error: Option<String>,
}

impl Audio {
    pub fn new() -> Result<Audio, String> {
        let sh = Arc::new(Shared {
            buf: Mutex::new(VecDeque::with_capacity(48000 * 2)),
            playing: AtomicBool::new(false),
            loading: AtomicBool::new(false),
            gen: AtomicU64::new(0),
            volume: AtomicI32::new(70),
            out_rate: AtomicU32::new(48000),
            error: Mutex::new(None),
        });
        let stream = start_output(sh.clone())?;
        let (tx, rx) = channel();
        let worker_sh = sh.clone();
        std::thread::Builder::new()
            .name("airwave-audio".into())
            .spawn(move || worker(rx, worker_sh))
            .map_err(|e| e.to_string())?;
        Ok(Audio { sh, tx, _stream: stream })
    }

    pub fn play(&self, url: &str) {
        let gen = self.sh.gen.fetch_add(1, Ordering::SeqCst) + 1;
        self.sh.error.lock().unwrap().take();
        self.sh.loading.store(true, Ordering::SeqCst);
        self.sh.playing.store(false, Ordering::SeqCst);
        self.sh.buf.lock().unwrap().clear();
        log(format!("播放请求 {url}"));
        let _ = self.tx.send(Cmd::Play { gen, url: url.to_string() });
    }

    pub fn stop(&self) {
        self.sh.gen.fetch_add(1, Ordering::SeqCst);
        self.sh.loading.store(false, Ordering::SeqCst);
        self.sh.playing.store(false, Ordering::SeqCst);
        self.sh.buf.lock().unwrap().clear();
        let _ = self.tx.send(Cmd::Stop);
    }

    pub fn set_volume(&self, v: i32) {
        self.sh.volume.store(v.clamp(0, 100), Ordering::Relaxed);
    }

    pub fn snapshot(&self) -> Snapshot {
        Snapshot {
            playing: self.sh.playing.load(Ordering::Relaxed),
            loading: self.sh.loading.load(Ordering::Relaxed),
            error: self.sh.error.lock().unwrap().clone(),
        }
    }
}

impl Drop for Audio {
    fn drop(&mut self) {
        let _ = self.tx.send(Cmd::Quit);
    }
}

fn start_output(sh: Arc<Shared>) -> Result<cpal::Stream, String> {
    let host = cpal::default_host();
    let dev = host.default_output_device().ok_or("没有可用的音频输出设备")?;
    let supported = dev.default_output_config().map_err(|e| e.to_string())?;
    let rate = supported.sample_rate().0;
    let fmt = supported.sample_format();
    let channels = supported.channels() as usize;
    let config: cpal::StreamConfig = supported.into();
    sh.out_rate.store(rate, Ordering::SeqCst);
    let err = |e| log(format!("音频输出错误: {e}"));

    let stream = match fmt {
        cpal::SampleFormat::F32 => dev.build_output_stream(
            &config,
            move |data: &mut [f32], _| fill_f32(data, channels, &sh),
            err,
            None,
        ),
        cpal::SampleFormat::I16 => dev.build_output_stream(
            &config,
            move |data: &mut [i16], _| {
                let mut tmp = vec![0f32; data.len()];
                fill_f32(&mut tmp, channels, &sh);
                for (d, s) in data.iter_mut().zip(tmp) {
                    *d = (s.clamp(-1.0, 1.0) * 32767.0) as i16;
                }
            },
            err,
            None,
        ),
        cpal::SampleFormat::U16 => dev.build_output_stream(
            &config,
            move |data: &mut [u16], _| {
                let mut tmp = vec![0f32; data.len()];
                fill_f32(&mut tmp, channels, &sh);
                for (d, s) in data.iter_mut().zip(tmp) {
                    *d = ((s.clamp(-1.0, 1.0) * 0.5 + 0.5) * 65535.0) as u16;
                }
            },
            err,
            None,
        ),
        other => return Err(format!("不支持的音频格式 {other:?}")),
    }
    .map_err(|e| e.to_string())?;
    stream.play().map_err(|e| e.to_string())?;
    Ok(stream)
}

fn fill_f32(data: &mut [f32], dev_ch: usize, sh: &Shared) {
    let vol = sh.volume.load(Ordering::Relaxed) as f32 / 100.0;
    let mut b = sh.buf.lock().unwrap();
    let mut i = 0;
    while i < data.len() {
        let l = b.pop_front().unwrap_or(0.0);
        let r = b.pop_front().unwrap_or(0.0);
        if dev_ch == 1 {
            data[i] = (l + r) * 0.5 * vol;
            i += 1;
        } else {
            data[i] = l * vol;
            if i + 1 < data.len() {
                data[i + 1] = r * vol;
            }
            i += dev_ch.min(2).max(1);
            if dev_ch > 2 {
                for _ in 2..dev_ch {
                    if i < data.len() {
                        data[i] = 0.0;
                        i += 1;
                    }
                }
            }
        }
    }
}

// ---------------- 播放入口 ----------------

fn worker(rx: Receiver<Cmd>, sh: Arc<Shared>) {
    let agent = ureq::AgentBuilder::new()
        .timeout_connect(Duration::from_secs(8))
        .timeout_read(Duration::from_secs(15))
        .timeout_write(Duration::from_secs(8))
        .user_agent("Airwave/1.0 (PL-680)")
        .build();
    loop {
        match rx.recv() {
            Ok(Cmd::Play { gen, url }) => run_station(&agent, &rx, &sh, gen, &url),
            Ok(Cmd::Stop) => {}
            Ok(Cmd::Quit) | Err(_) => return,
        }
    }
}

fn run_station(agent: &ureq::Agent, rx: &Receiver<Cmd>, sh: &Arc<Shared>, gen: u64, url: &str) {
    let result = play_url(agent, rx, sh, gen, url);
    if sh.gen.load(Ordering::SeqCst) != gen {
        return;
    }
    match result {
        Ok(()) => {
            log("播放结束");
            sh.loading.store(false, Ordering::SeqCst);
            sh.playing.store(false, Ordering::SeqCst);
        }
        Err(e) => {
            log(format!("播放失败: {e}"));
            sh.loading.store(false, Ordering::SeqCst);
            sh.playing.store(false, Ordering::SeqCst);
            *sh.error.lock().unwrap() = Some(e);
        }
    }
}

fn play_url(
    agent: &ureq::Agent,
    rx: &Receiver<Cmd>,
    sh: &Arc<Shared>,
    gen: u64,
    url: &str,
) -> Result<(), String> {
    let is_hls = url.to_ascii_lowercase().contains(".m3u8");
    let resp = get(agent, url)?;
    let ctype = resp.content_type().to_ascii_lowercase();
    if is_hls || ctype.contains("mpegurl") {
        let body = resp.into_string().map_err(|e| e.to_string())?;
        return play_hls(agent, rx, sh, gen, url, &body);
    }
    let hint_ext = if ctype.contains("mpeg") && !ctype.contains("mpegurl") {
        "mp3"
    } else if ctype.contains("aac") || ctype.contains("mp4") {
        "aac"
    } else if ctype.contains("ogg") {
        "ogg"
    } else {
        url.rsplit('.').next().unwrap_or("mp3").split('?').next().unwrap_or("mp3")
    };
    let reader: Box<dyn Read + Send + Sync> = resp.into_reader();
    let source = NetSource { inner: reader, len: None };
    let mut out = Resampler::new();
    decode_stream(source, hint_ext, &mut out, sh, rx, gen, false)
}

fn get(agent: &ureq::Agent, url: &str) -> Result<ureq::Response, String> {
    match agent.get(url).call() {
        Ok(r) => Ok(r),
        Err(ureq::Error::Status(code, _)) => Err(format!("HTTP {code}")),
        Err(e) => Err(format!("网络错误：{e}")),
    }
}

struct NetSource {
    inner: Box<dyn Read + Send + Sync>,
    len: Option<u64>,
}

impl Read for NetSource {
    fn read(&mut self, buf: &mut [u8]) -> std::io::Result<usize> {
        self.inner.read(buf)
    }
}

impl Seek for NetSource {
    fn seek(&mut self, _pos: SeekFrom) -> std::io::Result<u64> {
        Err(std::io::Error::new(std::io::ErrorKind::Other, "not seekable"))
    }
}

impl MediaSource for NetSource {
    fn is_seekable(&self) -> bool {
        false
    }
    fn byte_len(&self) -> Option<u64> {
        self.len
    }
}

struct MemSource {
    inner: Cursor<Vec<u8>>,
}

impl Read for MemSource {
    fn read(&mut self, buf: &mut [u8]) -> std::io::Result<usize> {
        self.inner.read(buf)
    }
}

impl Seek for MemSource {
    fn seek(&mut self, pos: SeekFrom) -> std::io::Result<u64> {
        self.inner.seek(pos)
    }
}

impl MediaSource for MemSource {
    fn is_seekable(&self) -> bool {
        true
    }
    fn byte_len(&self) -> Option<u64> {
        Some(self.inner.get_ref().len() as u64)
    }
}

fn decode_stream<S: MediaSource + 'static>(
    source: S,
    ext: &str,
    out: &mut Resampler,
    sh: &Arc<Shared>,
    rx: &Receiver<Cmd>,
    gen: u64,
    eof_ok: bool,
) -> Result<(), String> {
    let ext = if ext == "ts" { "aac" } else { ext };
    let mss = MediaSourceStream::new(Box::new(source), Default::default());
    let mut hint = Hint::new();
    if !ext.is_empty() {
        hint.with_extension(ext);
    }
    let fmt_opts = FormatOptions { enable_gapless: true, ..Default::default() };
    let meta_opts = MetadataOptions::default();
    let probed = symphonia::default::get_probe()
        .format(&hint, mss, &fmt_opts, &meta_opts)
        .map_err(|e| format!("无法识别的流：{e}"))?;
    let mut format = probed.format;
    let track = format
        .default_track()
        .ok_or_else(|| "流里没有音频轨道".to_string())?;
    let track_id = track.id;
    let mut decoder = symphonia::default::get_codecs()
        .make(&track.codec_params, &DecoderOptions::default())
        .map_err(|e| format!("解码器初始化失败：{e}"))?;

    let mut sample_buf: Option<SampleBuffer<f32>> = None;
    let mut first = true;
    loop {
        if sh.gen.load(Ordering::SeqCst) != gen {
            return Ok(());
        }
        if !rx_ok(rx) {
            return Ok(());
        }
        let packet = match format.next_packet() {
            Ok(p) => p,
            Err(SymError::IoError(e)) if e.kind() == std::io::ErrorKind::UnexpectedEof => {
                if eof_ok {
                    return Ok(());
                }
                return Err("流已结束".into())
            }
            Err(SymError::ResetRequired) => return Err("流已重置".into()),
            Err(e) => return Err(format!("读取失败：{e}")),
        };
        if packet.track_id() != track_id {
            continue;
        }
        match decoder.decode(&packet) {
            Ok(decoded) => {
                let spec = *decoded.spec();
                if sample_buf.as_ref().map(|b| b.capacity() < decoded.capacity()).unwrap_or(true) {
                    sample_buf = Some(SampleBuffer::<f32>::new(decoded.capacity() as u64, spec));
                }
                let buf = sample_buf.as_mut().unwrap();
                buf.copy_interleaved_ref(decoded);
                let ch = spec.channels.count();
                out.push(buf.samples(), ch, spec.rate, sh);
                if first {
                    first = false;
                    log(format!("开始出声 {ext} {ch}ch {}Hz", spec.rate));
                }
                sh.playing.store(true, Ordering::Relaxed);
                sh.loading.store(false, Ordering::Relaxed);
            }
            Err(SymError::DecodeError(_)) => continue,
            Err(SymError::IoError(e)) if e.kind() == std::io::ErrorKind::UnexpectedEof => {
                if eof_ok {
                    return Ok(());
                }
                return Err("流已结束".into())
            }
            Err(e) => return Err(format!("解码失败：{e}")),
        }
    }
}

fn rx_ok(rx: &Receiver<Cmd>) -> bool {
    match rx.try_recv() {
        Ok(Cmd::Stop) => false,
        Ok(Cmd::Quit) => false,
        Ok(Cmd::Play { .. }) => false,
        Err(std::sync::mpsc::TryRecvError::Empty) => true,
        Err(std::sync::mpsc::TryRecvError::Disconnected) => false,
    }
}

// ---------------- ADTS (原生 AAC 帧) ----------------

const AAC_RATES: [u32; 13] = [
    96000, 88200, 64000, 48000, 44100, 32000, 24000, 22050, 16000, 12000, 11025, 8000, 7350,
];

struct AdtsHeader {
    object: u32,
    sf_index: u32,
    rate: u32,
    ch_cfg: u32,
    frame_len: usize,
    header_len: usize,
}

fn parse_adts(buf: &[u8], pos: usize) -> Option<AdtsHeader> {
    if pos + 7 > buf.len() || buf[pos] != 0xFF || (buf[pos + 1] & 0xF0) != 0xF0 {
        return None;
    }
    let protection_absent = buf[pos + 1] & 0x01;
    let profile = (buf[pos + 2] >> 6) & 0x03;
    let sf_index = ((buf[pos + 2] >> 2) & 0x0F) as u32;
    if sf_index >= 13 {
        return None;
    }
    let ch_cfg = (((buf[pos + 2] & 0x01) as u32) << 2) | ((buf[pos + 3] >> 6) as u32 & 0x03);
    let frame_len = (((buf[pos + 3] as usize & 0x03) << 11)
        | ((buf[pos + 4] as usize) << 3)
        | ((buf[pos + 5] as usize) >> 5)) as usize;
    let header_len = if protection_absent == 1 { 7 } else { 9 };
    if frame_len < header_len || pos + frame_len > buf.len() {
        return None;
    }
    Some(AdtsHeader {
        object: profile as u32 + 1,
        sf_index,
        rate: AAC_RATES[sf_index as usize],
        ch_cfg,
        frame_len,
        header_len,
    })
}

fn aac_channels(cfg: u32) -> Option<symphonia::core::audio::Channels> {
    use symphonia::core::audio::Channels;
    Some(match cfg {
        1 => Channels::FRONT_CENTRE,
        2 => Channels::FRONT_LEFT | Channels::FRONT_RIGHT,
        3 => Channels::FRONT_CENTRE | Channels::FRONT_LEFT | Channels::FRONT_RIGHT,
        4 => Channels::FRONT_CENTRE | Channels::FRONT_LEFT | Channels::FRONT_RIGHT | Channels::REAR_CENTRE,
        5 => Channels::FRONT_CENTRE
            | Channels::FRONT_LEFT
            | Channels::FRONT_RIGHT
            | Channels::REAR_LEFT
            | Channels::REAR_RIGHT,
        6 => Channels::FRONT_CENTRE
            | Channels::FRONT_LEFT
            | Channels::FRONT_RIGHT
            | Channels::LFE1
            | Channels::REAR_LEFT
            | Channels::REAR_RIGHT,
        7 => Channels::FRONT_CENTRE
            | Channels::FRONT_LEFT
            | Channels::FRONT_RIGHT
            | Channels::LFE1
            | Channels::SIDE_LEFT
            | Channels::SIDE_RIGHT
            | Channels::REAR_CENTRE,
        _ => return None,
    })
}

// symphonia 的 ADTS 读取器只认 0xFFF1 同步字，青蜓等源用 0xFFF9，因此自己拆帧。
fn decode_adts(
    bytes: &[u8],
    out: &mut Resampler,
    sh: &Arc<Shared>,
    rx: &Receiver<Cmd>,
    gen: u64,
) -> Result<(), String> {
    use symphonia::core::codecs::{CodecParameters, CODEC_TYPE_AAC};
    let mut pos = 0usize;
    let first = loop {
        if pos + 7 > bytes.len() {
            return Err("ADTS 帧不存在".into());
        }
        if let Some(h) = parse_adts(bytes, pos) {
            break h;
        }
        pos += 1;
    };
    let mut params = CodecParameters::new();
    params.for_codec(CODEC_TYPE_AAC).with_sample_rate(first.rate);
    if let Some(ch) = aac_channels(first.ch_cfg) {
        params.with_channels(ch);
    }
    // AudioSpecificConfig: 5 bit 对象类型 + 4 bit 采样率索引 + 4 bit 声道配置
    let asc = [
        ((first.object << 3) | (first.sf_index >> 1)) as u8,
        (((first.sf_index & 0x01) << 7) | (first.ch_cfg << 3)) as u8,
    ];
    params.with_extra_data(Box::new(asc));
    let mut decoder = symphonia::default::get_codecs()
        .make(&params, &DecoderOptions::default())
        .map_err(|e| format!("AAC 解码器初始化失败：{e}"))?;

    let mut sample_buf: Option<SampleBuffer<f32>> = None;
    let mut ts = 0u64;
    let mut logged = false;
    let mut frames = 0u32;
    while pos + 7 <= bytes.len() {
        if sh.gen.load(Ordering::SeqCst) != gen || !rx_ok(rx) {
            return Ok(());
        }
        let Some(h) = parse_adts(bytes, pos) else {
            if frames == 0 {
                pos += 1;
                continue;
            }
            break;
        };
        let data = &bytes[pos + h.header_len..pos + h.frame_len];
        pos += h.frame_len;
        frames += 1;
        let packet = symphonia::core::formats::Packet::new_from_slice(0, ts, 1024, data);
        ts += 1024;
        let decoded = match decoder.decode(&packet) {
            Ok(d) => d,
            Err(SymError::DecodeError(_)) => continue,
            Err(SymError::IoError(e)) if e.kind() == std::io::ErrorKind::UnexpectedEof => break,
            Err(e) => return Err(format!("AAC 解码失败：{e}")),
        };
        let spec = *decoded.spec();
        if sample_buf.as_ref().map(|b| b.capacity() < decoded.capacity()).unwrap_or(true) {
            sample_buf = Some(SampleBuffer::<f32>::new(decoded.capacity() as u64, spec));
        }
        let buf = sample_buf.as_mut().unwrap();
        buf.copy_interleaved_ref(decoded);
        out.push(buf.samples(), spec.channels.count(), spec.rate, sh);
        if !logged {
            logged = true;
            log(format!("开始出声 aac {}ch {}Hz", spec.channels.count(), spec.rate));
        }
        sh.playing.store(true, Ordering::Relaxed);
        sh.loading.store(false, Ordering::Relaxed);
    }
    if frames == 0 {
        return Err("ADTS 帧为空".into());
    }
    Ok(())
}

// ---------------- HLS ----------------

fn resolve(base: &str, rel: &str) -> String {
    if rel.starts_with("http://") || rel.starts_with("https://") {
        return rel.to_string();
    }
    if rel.starts_with("//") {
        return format!("https:{rel}");
    }
    let scheme_end = base.find("://").map(|i| i + 3).unwrap_or(0);
    if rel.starts_with('/') {
        let host_end = base[scheme_end..].find('/').map(|i| scheme_end + i).unwrap_or(base.len());
        return format!("{}{}", &base[..host_end], rel);
    }
    match base.rfind('/') {
        Some(i) if i >= scheme_end => format!("{}{}", &base[..=i], rel),
        _ => format!("{base}/{rel}"),
    }
}

struct Playlist {
    segments: Vec<String>,
    target: f32,
    endlist: bool,
    variant: Option<String>,
    map: Option<String>,
}

fn parse_playlist(base: &str, text: &str) -> Playlist {
    let mut segments = Vec::new();
    let mut target = 5.0f32;
    let mut endlist = false;
    let mut variant = None;
    let mut map = None;
    let mut take_variant = false;
    for line in text.lines() {
        let line = line.trim();
        if line.is_empty() {
            continue;
        }
        if line.starts_with("#EXT-X-MAP:") {
            if let Some(rest) = line.split("URI=\"").nth(1) {
                if let Some(uri) = rest.split('"').next() {
                    map = Some(resolve(base, uri));
                }
            }
        } else if line.starts_with("#EXT-X-TARGETDURATION:") {
            if let Ok(v) = line[22..].trim().parse::<f32>() {
                target = v.clamp(1.0, 15.0);
            }
        } else if line.starts_with("#EXT-X-ENDLIST") {
            endlist = true;
        } else if line.starts_with("#EXT-X-STREAM-INF") {
            take_variant = true;
        } else if line.starts_with('#') {
            continue;
        } else if take_variant {
            variant = Some(resolve(base, line));
            take_variant = false;
        } else {
            segments.push(resolve(base, line));
        }
    }
    Playlist { segments, target, endlist, variant, map }
}

fn play_hls(
    agent: &ureq::Agent,
    rx: &Receiver<Cmd>,
    sh: &Arc<Shared>,
    gen: u64,
    url: &str,
    body: &str,
) -> Result<(), String> {
    let first = parse_playlist(url, body);
    let (plist_url, plist) = if let Some(v) = first.variant {
        let text = get(agent, &v)?.into_string().map_err(|e| e.to_string())?;
        (v.clone(), parse_playlist(&v, &text))
    } else {
        (url.to_string(), first)
    };
    if plist.segments.is_empty() {
        return Err("播放列表为空".into());
    }
    log(format!("HLS {} 段 目标时长 {:?}", plist.segments.len(), plist.target));
    let mut queues: VecDeque<String> = plist.segments.iter().cloned().collect();
    let mut played: VecDeque<String> = VecDeque::new();
    let mut last_reload = Instant::now();
    let mut out = Resampler::new();
    let mut init: Option<Vec<u8>> = None;
    let mut fails = 0u32;
    if let Some(m) = &plist.map {
        init = get(agent, m).ok().map(|r| r.into_reader().read_to_end_limited().unwrap_or_default());
    }
    loop {
        if sh.gen.load(Ordering::SeqCst) != gen || !rx_ok(rx) {
            return Ok(());
        }
        if let Some(seg) = queues.pop_front() {
            played.push_back(seg.clone());
            while played.len() > 24 {
                played.pop_front();
            }
            let bytes = get(agent, &seg)?.into_reader().read_to_end_limited()?;
            if bytes.is_empty() {
                continue;
            }
            let (payload, ext) = if looks_like_ts(&bytes) {
                (ts_audio_payload(&bytes), "ts")
            } else if bytes.starts_with(b"\x00\x00\x00") || bytes.windows(4).any(|w| w == b"ftyp") {
                match &init {
                    Some(i) => {
                        let mut v = Vec::with_capacity(i.len() + bytes.len());
                        v.extend_from_slice(i);
                        v.extend_from_slice(&bytes);
                        (v, "mp4")
                    }
                    None => (bytes, "mp4"),
                }
            } else {
                let e = sniff_ext(&bytes);
                (bytes, e)
            };
            if payload.len() < 256 {
                continue;
            }
            let decoded = if ext == "ts" || ext == "aac" {
                decode_adts(&payload, &mut out, sh, rx, gen)
            } else {
                let src = MemSource { inner: Cursor::new(payload) };
                decode_stream(src, ext, &mut out, sh, rx, gen, true)
            };
            match decoded {
                Ok(()) => fails = 0,
                Err(e) => {
                    fails += 1;
                    if fails >= 3 {
                        return Err(e);
                    }
                }
            }
            sh.playing.store(true, Ordering::Relaxed);
            sh.loading.store(false, Ordering::Relaxed);
            last_reload = Instant::now();
            continue;
        }
        if plist.endlist {
            return Err("直播已结束".into());
        }
        if last_reload.elapsed() > Duration::from_millis(1000) {
            last_reload = Instant::now();
            match get(agent, &plist_url) {
                Ok(r) => {
                    let text = r.into_string().unwrap_or_default();
                    let next = parse_playlist(&plist_url, &text);
                    let mut added = 0;
                    for seg in next.segments {
                        if !played.contains(&seg) && !queues.contains(&seg) {
                            queues.push_back(seg);
                            added += 1;
                        }
                    }
                    if added == 0 {
                        std::thread::sleep(Duration::from_millis(600));
                    }
                }
                Err(_) => std::thread::sleep(Duration::from_millis(800)),
            }
        } else {
            std::thread::sleep(Duration::from_millis(250));
        }
    }
}

trait ReadLimited {
    fn read_to_end_limited(self) -> Result<Vec<u8>, String>;
}

impl ReadLimited for Box<dyn Read + Send + Sync + 'static> {
    fn read_to_end_limited(mut self) -> Result<Vec<u8>, String> {
        let mut buf = Vec::with_capacity(256 * 1024);
        let mut chunk = [0u8; 16 * 1024];
        loop {
            match self.read(&mut chunk) {
                Ok(0) => break,
                Ok(n) => {
                    buf.extend_from_slice(&chunk[..n]);
                    if buf.len() > 8 * 1024 * 1024 {
                        break;
                    }
                }
                Err(e) => {
                    if buf.is_empty() {
                        return Err(format!("分片下载失败：{e}"));
                    }
                    break;
                }
            }
        }
        Ok(buf)
    }
}

fn looks_like_ts(b: &[u8]) -> bool {
    b.len() > 376 && b[0] == 0x47 && b[188] == 0x47
}

fn sniff_ext(b: &[u8]) -> &'static str {
    if b.len() > 3 && &b[..3] == b"ID3" {
        return "mp3";
    }
    if b.len() > 1 && b[0] == 0xFF {
        let b1 = b[1];
        if b1 & 0xF6 == 0xF0 {
            return "aac";
        }
        if b1 & 0xE0 == 0xE0 {
            return "mp3";
        }
    }
    "mp3"
}

fn ts_audio_payload(ts: &[u8]) -> Vec<u8> {
    let mut pmt_pid: Option<u16> = None;
    let mut audio_pid: Option<u16> = None;
    let mut out: Vec<u8> = Vec::with_capacity(ts.len());
    let mut cur: Vec<u8> = Vec::new();
    for chunk in ts.chunks(188) {
        if chunk.len() < 188 || chunk[0] != 0x47 {
            continue;
        }
        let pid = (((chunk[1] & 0x1f) as u16) << 8) | chunk[2] as u16;
        let pusi = chunk[1] & 0x40 != 0;
        let afc = (chunk[3] >> 4) & 0x3;
        let mut off = 4usize;
        if afc == 2 || afc == 3 {
            off += 1 + chunk[4] as usize;
        }
        if off >= 188 {
            continue;
        }
        let payload = &chunk[off..];
        if pid == 0 {
            if let Some(sec) = psi_section(payload) {
                if sec.len() >= 12 && sec[0] == 0x00 {
                    let pid0 = (((sec[10] as u16) & 0x1f) << 8) | sec[11] as u16;
                    if pid0 != 0x10 {
                        pmt_pid = Some(pid0);
                    }
                }
            }
        } else if Some(pid) == pmt_pid {
            if let Some(sec) = psi_section(payload) {
                if sec.len() > 12 && sec[0] == 0x02 && audio_pid.is_none() {
                    let mut i = 12 + ((((sec[10] as usize) & 0x0f) << 8) | sec[11] as usize);
                    while i + 5 <= sec.len() {
                        let st = sec[i];
                        let epid = (((sec[i + 1] as u16) & 0x1f) << 8) | sec[i + 2] as u16;
                        let es_len = (((sec[i + 3] as usize) & 0x0f) << 8) | sec[i + 4] as usize;
                        if matches!(st, 0x0f | 0x11 | 0x03 | 0x04) {
                            audio_pid = Some(epid);
                            break;
                        }
                        i += 5 + es_len;
                    }
                }
            }
        } else if Some(pid) == audio_pid {
            if pusi {
                flush_pes(&mut cur, &mut out);
            }
            cur.extend_from_slice(payload);
        }
    }
    flush_pes(&mut cur, &mut out);
    out
}

fn flush_pes(cur: &mut Vec<u8>, out: &mut Vec<u8>) {
    if cur.len() > 9 && cur[0] == 0 && cur[1] == 0 && cur[2] == 1 {
        let hlen = cur[8] as usize;
        let start = 9 + hlen;
        if start < cur.len() {
            out.extend_from_slice(&cur[start..]);
        }
    } else if !cur.is_empty() {
        out.extend_from_slice(cur);
    }
    cur.clear();
}

fn psi_section(payload: &[u8]) -> Option<&[u8]> {
    let pointer = *payload.first()? as usize;
    let rest = payload.get(1 + pointer..)?;
    if rest.len() < 3 {
        return None;
    }
    let len = ((((rest[1] as usize) & 0x0f) << 8) | rest[2] as usize) + 3;
    rest.get(..len.min(rest.len()))
}

// ---------------- 重采样 ----------------

struct Resampler {
    phase: f64,
    tail: [f32; 2],
    primed: bool,
}

impl Resampler {
    fn new() -> Resampler {
        Resampler { phase: 0.0, tail: [0.0; 2], primed: false }
    }

    fn push(&mut self, samples: &[f32], ch: usize, src_rate: u32, sh: &Shared) {
        if ch == 0 {
            return;
        }
        let frames = samples.len() / ch;
        if frames == 0 {
            return;
        }
        let rate = sh.out_rate.load(Ordering::Relaxed).max(8000) as f64;
        let src_rate = if src_rate == 0 { 44100.0 } else { src_rate as f64 };
        let step = src_rate / rate;
        if !self.primed {
            self.tail = frame_at(samples, ch, 0);
            self.primed = true;
            self.phase = 0.0;
        }
        let total = frames as f64;
        let mut pos = self.phase;
        loop {
            let i = pos.floor();
            if i + 1.0 >= total {
                break;
            }
            let frac = (pos - i) as f32;
            let a = if i < 0.0 { self.tail } else { frame_at(samples, ch, i as usize) };
            let b = frame_at(samples, ch, (i + 1.0) as usize);
            let (l, r) = (a[0] + (b[0] - a[0]) * frac, a[1] + (b[1] - a[1]) * frac);
            self.emit(l, r, rate, sh);
            pos += step;
        }
        self.phase = pos - total;
        self.tail = frame_at(samples, ch, frames - 1);
    }

    fn emit(&self, l: f32, r: f32, rate: f64, sh: &Shared) {
        let cap = (rate * CAP_SECS as f64) as usize * OUT_CH;
        let mut spin = 0;
        loop {
            {
                let mut b = sh.buf.lock().unwrap();
                if b.len() < cap {
                    b.push_back(l);
                    b.push_back(r);
                    return;
                }
            }
            spin += 1;
            std::thread::sleep(Duration::from_millis(if spin > 400 { 60 } else { 10 }));
        }
    }
}

fn frame_at(samples: &[f32], ch: usize, i: usize) -> [f32; 2] {
    let base = i * ch;
    match ch {
        1 => {
            let v = samples[base];
            [v, v]
        }
        _ => [samples[base], samples[base + 1]],
    }
}
