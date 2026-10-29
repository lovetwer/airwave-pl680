#![cfg_attr(not(debug_assertions), windows_subsystem = "windows")]

slint::include_modules!();

mod audio;
mod log;

use std::cell::RefCell;
use std::collections::HashSet;
use std::rc::Rc;

use slint::winit_030::WinitWindowAccessor;
use slint::{ModelRc, SharedString, VecModel};

// ---------------- 目录数据 ----------------

#[derive(serde::Deserialize, Clone, Default)]
struct Raw {
    #[serde(default)]
    id: String,
    #[serde(default)]
    name: String,
    #[serde(default)]
    url: String,
    #[serde(default, rename = "urlResolved")]
    url_resolved: String,
    #[serde(default)]
    tags: Vec<String>,
    #[serde(default)]
    state: String,
    #[serde(default)]
    codec: String,
    #[serde(default)]
    bitrate: u32,
    #[serde(default)]
    hls: bool,
    #[serde(default)]
    votes: i64,
}

const TAGS: &[(&str, &str)] = &[
    ("新闻", "news"),
    ("音乐", "music"),
    ("老歌", "oldies"),
    ("经济", "economics"),
    ("电视", "tv"),
    ("古典", "classical"),
    ("故事", "storytelling"),
    ("相声", "crosstalk"),
];

const REGION_CHIPS: &[&str] = &[
    "全国", "北京", "上海", "华南", "江浙皖", "川渝", "东北", "华中", "华北", "西部",
];

const PROVINCES: &[(&str, &[&str])] = &[
    ("北京", &["北京", "Beijing", "Peking"]),
    ("天津", &["天津", "Tientsin", "Tianjin"]),
    ("河北", &["河北", "石家庄", "唐山", "保定", "邯郸", "秦皇岛", "Hopei", "Hebei"]),
    ("山西", &["山西", "太原", "大同", "临汾", "Shansi"]),
    ("内蒙古", &["内蒙", "呼和浩特", "包头", "鄂尔多斯", "Mongolia"]),
    ("辽宁", &["辽宁", "沈阳", "大连", "鞍山", "抚顺", "营口", "Liaoning", "Liaotung", "Dalian"]),
    ("吉林", &["吉林", "长春", "延边", "Kirin"]),
    ("黑龙江", &["黑龙江", "哈尔滨", "齐齐哈尔", "牡丹江", "大庆", "Amur", "Heilungkiang"]),
    ("上海", &["上海", "Shanghai", "东广", "东方广播", "第一财经", "浦江", "魔都"]),
    ("江苏", &["江苏", "南京", "苏州", "无锡", "常州", "南通", "徐州", "扬州", "镇江", "盐城", "Kiangsu"]),
    ("浙江", &["浙江", "杭州", "宁波", "温州", "绍兴", "嘉兴", "金华", "台州", "湖州", "Chekiang"]),
    ("安徽", &["安徽", "合肥", "芜湖", "蚌埠", "安庆", "黄山", "阜阳", "Anhwei"]),
    ("福建", &["福建", "福州", "厦门", "泉州", "漳州", "莆田", "宁德", "Fukien"]),
    ("江西", &["江西", "南昌", "赣州", "九江", "景德镇", "Kiangsi"]),
    ("山东", &["山东", "济南", "青岛", "烟台", "潍坊", "临沂", "淄博", "济宁", "泰安", "威海", "日照", "东营", "德州", "聊城", "滨州", "菏泽", "枣庄", "Shantung"]),
    ("河南", &["河南", "郑州", "洛阳", "开封", "南阳", "信阳", "Honan"]),
    ("湖北", &["湖北", "武汉", "宜昌", "襄阳", "荆州", "十堰", "Hupei"]),
    ("湖南", &["湖南", "长沙", "株洲", "湘潭", "衡阳", "岳阳", "常德", "张家界", "益阳", "娄底", "郴州", "永州", "邵阳", "怀化", "湘西", "芒果", "金鹰", "潇湘", "Hunan"]),
    ("广东", &["广东", "广州", "深圳", "珠海", "佛山", "东莞", "中山", "惠州", "江门", "汕头", "湛江", "肇庆", "韶关", "梅州", "河源", "阳江", "茂名", "云浮", "潮州", "揭阳", "汕尾", "羊城", "粤语", "珠江", "Kwangtung"]),
    ("广西", &["广西", "南宁", "桂林", "柳州", "北海", "贵港", "梧州", "Kwangsi"]),
    ("海南", &["海南", "海口", "三亚", "Hainan"]),
    ("重庆", &["重庆", "巴渝", "Chungking"]),
    ("四川", &["四川", "成都", "绵阳", "德阳", "宜宾", "泸州", "乐山", "南充", "Szechuan"]),
    ("贵州", &["贵州", "贵阳", "遵义", "安顺", "Kweichow"]),
    ("云南", &["云南", "昆明", "大理", "丽江", "曲靖", "Yunnan"]),
    ("陕西", &["陕西", "西安", "宝鸡", "咸阳", "渭南", "Shensi"]),
    ("甘肃", &["甘肃", "兰州", "天水", "酒泉", "Kansu"]),
    ("宁夏", &["宁夏", "银川", "Ninghsia"]),
    ("青海", &["青海", "西宁", "Tsinghai"]),
    ("新疆", &["新疆", "乌鲁木齐", "喀什", "伊犁", "Sinkiang"]),
    ("西藏", &["西藏", "拉萨", "Xizang", "Tibet"]),
    ("港澳", &["香港", "澳门", "Hong Kong", "Macau", "Macao", "RTHK", "濠江", "凤凰"]),
    ("台湾", &["台湾", "台北", "高雄", "台中", "Formosa"]),
];

const NATIONAL: &[&str] = &[
    "中国之声", "中国交通", "中国国际", "中央人民", "中央广播电视", "CNR", "CCTV", "CGTN",
    "CRI", "China Plus", "环球资讯", "国际广播", "新闻联播", "中华之声", "神州", "华夏", "国家",
    "民族", "阅读之声", "文艺之声", "经典调频",
];

fn tag_cn(t: &str) -> String {
    let low = t.to_lowercase();
    match low.as_str() {
        "news" => "新闻",
        "music" => "音乐",
        "oldies" => "老歌",
        "economics" => "经济",
        "tv" => "电视伴音",
        "classical" => "古典",
        "storytelling" => "故事",
        "crosstalk" => "相声",
        "talk" => "谈话",
        "culture" => "文化",
        "entertainment" => "娱乐",
        "information" => "资讯",
        "instrumental" => "器乐",
        "pop" => "流行",
        "ambient" => "氛围",
        "relax" => "放松",
        "easy listening" => "轻音乐",
        "golden music" => "经典金曲",
        "local service" => "地方台",
        _ => return t.to_string(),
    }
    .to_string()
}

fn province_of(st: &Raw) -> String {
    let hay = format!("{} {}", st.name, st.state);
    if !st.state.is_empty() {
        for (label, kws) in PROVINCES {
            if kws.iter().any(|k| st.state.contains(k)) {
                return label.to_string();
            }
        }
    }
    for (label, kws) in PROVINCES {
        if kws.iter().any(|k| hay.contains(k)) {
            return label.to_string();
        }
    }
    String::new()
}

fn area_of(st: &Raw) -> String {
    let p = province_of(st);
    if !p.is_empty() {
        return p;
    }
    if NATIONAL.iter().any(|k| st.name.contains(k)) {
        return "全国".to_string();
    }
    "全国".to_string()
}

fn matches_region(st: &Raw, key: &str) -> bool {
    if key == "全国" {
        return NATIONAL.iter().any(|k| st.name.contains(k));
    }
    let prov = province_of(st);
    match key {
        "北京" => prov == "北京",
        "上海" => prov == "上海",
        "华南" => ["广东", "广西", "海南", "福建", "港澳", "台湾"].contains(&prov.as_str()),
        "江浙皖" => ["江苏", "浙江", "安徽"].contains(&prov.as_str()),
        "川渝" => ["四川", "重庆"].contains(&prov.as_str()),
        "东北" => ["辽宁", "吉林", "黑龙江"].contains(&prov.as_str()),
        "华中" => ["湖北", "湖南", "河南", "江西"].contains(&prov.as_str()),
        "华北" => ["北京", "天津", "河北", "山西", "内蒙古", "山东"].contains(&prov.as_str()),
        "西部" => ["陕西", "甘肃", "宁夏", "青海", "新疆", "西藏", "云南", "贵州"]
            .contains(&prov.as_str()),
        _ => true,
    }
}

fn hash_ratio(s: &str) -> f64 {
    let mut h: u64 = 0;
    for c in s.chars() {
        h = (h * 31 + c as u64) % 99991;
    }
    (h % 1000) as f64 / 1000.0
}

fn station_freq(st: &Raw) -> (String, String) {
    // LCD 只有 4 位数码位（FM 小数点固定在第 2、3 位之间），区间必须收在 4 位以内，
    // 否则会把 105.48 显示成 10.54、14820 显示成 1482。
    if st.hls || st.id.starts_with("link:") {
        let seed = if st.url_resolved.is_empty() { &st.id } else { &st.url_resolved };
        let v = 5900.0 + hash_ratio(seed) * 4000.0;
        let rounded = (v / 5.0).round() * 5.0;
        return (format!("{}", rounded as i64), "NET".to_string());
    }
    let v = 87.5 + hash_ratio(if st.id.is_empty() { &st.name } else { &st.id }) * 12.45;
    (format!("{:.2}", v), "FM".to_string())
}

fn digits4(text: &str) -> [i32; 4] {
    let d: Vec<i32> = text.chars().filter_map(|c| c.to_digit(10).map(|x| x as i32)).collect();
    let mut out = [0; 4];
    for i in 0..4 {
        out[i] = *d.get(i).unwrap_or(&0);
    }
    out
}

fn load_catalog() -> Vec<Raw> {
    let src = include_str!("../../radio/data/cn-stations.js");
    let start = src.find('[').unwrap_or(0);
    let end = src.rfind(']').map(|i| i + 1).unwrap_or(src.len());
    serde_json::from_str::<Vec<Raw>>(&src[start..end]).unwrap_or_default()
}

// ---------------- 应用状态 ----------------

#[derive(serde::Serialize, serde::Deserialize, Clone, Default)]
struct LinkRec {
    #[serde(default)]
    name: String,
    #[serde(default)]
    url: String,
}

#[derive(serde::Serialize, serde::Deserialize, Default)]
struct Saved {
    #[serde(default)]
    favs: Vec<String>,
    #[serde(default)]
    links: Vec<LinkRec>,
    #[serde(default)]
    recents: Vec<String>,
    #[serde(default)]
    volume: Option<i32>,
    #[serde(default)]
    autostart: Option<bool>,
    #[serde(default)]
    autoplay: Option<bool>,
    #[serde(default)]
    resume: Option<bool>,
    #[serde(default)]
    shuffle: Option<bool>,
    #[serde(default)]
    skip: Option<bool>,
}

fn state_path() -> std::path::PathBuf {
    let base = std::env::var("LOCALAPPDATA").unwrap_or_else(|_| ".".into());
    std::path::PathBuf::from(base).join("AIRWAVE Native").join("state.json")
}

fn load_state() -> Saved {
    std::fs::read_to_string(state_path())
        .ok()
        .and_then(|s| serde_json::from_str(&s).ok())
        .unwrap_or_default()
}

fn link_raw(l: &LinkRec) -> Raw {
    let url = l.url.trim().to_string();
    let name = if l.name.trim().is_empty() {
        url.clone()
    } else {
        l.name.trim().to_string()
    };
    Raw {
        id: format!("link:{url}"),
        name,
        url: url.clone(),
        url_resolved: url.clone(),
        tags: vec!["直链".into()],
        state: "直链".into(),
        hls: url.to_ascii_lowercase().contains(".m3u8"),
        ..Default::default()
    }
}

struct App {
    catalog: Vec<Raw>,
    links: Vec<LinkRec>,
    pool: Vec<Raw>,
    nav: i32,
    tag: String,
    region: String,
    query: String,
    current: i32,
    favs: HashSet<String>,
    recents: Vec<String>,
    volume: i32,
    muted: bool,
    autostart: bool,
    autoplay: bool,
    resume: bool,
    shuffle: bool,
    skip: bool,
}

#[cfg(windows)]
const RUN_KEY: &str = r"HKCU\Software\Microsoft\Windows\CurrentVersion\Run";

#[cfg(windows)]
fn reg(args: &[&str]) -> bool {
    use std::os::windows::process::CommandExt;
    const CREATE_NO_WINDOW: u32 = 0x0800_0000;
    std::process::Command::new("reg")
        .args(args)
        .creation_flags(CREATE_NO_WINDOW)
        .output()
        .map(|o| o.status.success())
        .unwrap_or(false)
}

#[cfg(windows)]
fn run_entry() -> Option<String> {
    use std::os::windows::process::CommandExt;
    const CREATE_NO_WINDOW: u32 = 0x0800_0000;
    let out = std::process::Command::new("reg")
        .args(["query", RUN_KEY, "/v", "AIRWAVE"])
        .creation_flags(CREATE_NO_WINDOW)
        .output()
        .ok()?;
    if !out.status.success() {
        return None;
    }
    let text = String::from_utf8_lossy(&out.stdout);
    for line in text.lines() {
        if let Some(i) = line.find("REG_SZ") {
            return Some(line[i + 6..].trim().to_string());
        }
    }
    Some(String::new())
}

#[cfg(windows)]
fn apply_autostart(on: bool, autoplay: bool) {
    let exe = std::env::current_exe()
        .map(|p| p.display().to_string())
        .unwrap_or_default();
    if on {
        let val = if autoplay { format!("\"{exe}\" --autoplay") } else { format!("\"{exe}\"") };
        if run_entry().as_deref() != Some(val.as_str()) {
            let ok = reg(&["add", RUN_KEY, "/v", "AIRWAVE", "/t", "REG_SZ", "/d", &val, "/f"]);
            log::log(format!("开机自启写入 {ok} {val}"));
        }
    } else if run_entry().is_some() {
        let ok = reg(&["delete", RUN_KEY, "/v", "AIRWAVE", "/f"]);
        log::log(format!("开机自启移除 {ok}"));
    }
}

fn rnd_pos(len: usize, avoid: i32) -> usize {
    if len <= 1 {
        return 0;
    }
    let nanos = std::time::SystemTime::now()
        .duration_since(std::time::UNIX_EPOCH)
        .map(|d| d.as_nanos() as u64)
        .unwrap_or(0);
    let mut h = nanos ^ 0x9e37_79b9_7f4a_7c15;
    h ^= h >> 31;
    h = h.wrapping_mul(0xff51_afd7_ed55_8ccd);
    h ^= h >> 29;
    let mut p = (h % len as u64) as usize;
    if p as i32 == avoid {
        p = (p + 1) % len;
    }
    p
}

impl App {
    fn save(&self) {
        let saved = Saved {
            favs: self.favs.iter().cloned().collect(),
            links: self.links.clone(),
            recents: self.recents.clone(),
            volume: Some(self.volume),
            autostart: Some(self.autostart),
            autoplay: Some(self.autoplay),
            resume: Some(self.resume),
            shuffle: Some(self.shuffle),
            skip: Some(self.skip),
        };
        if let Ok(text) = serde_json::to_string_pretty(&saved) {
            if let Some(dir) = state_path().parent() {
                let _ = std::fs::create_dir_all(dir);
            }
            let _ = std::fs::write(state_path(), text);
        }
    }

    fn pool_for(&self) -> Vec<Raw> {
        let base: Vec<Raw> = match self.nav {
            1 => self
                .catalog
                .iter()
                .filter(|st| self.favs.contains(&st.id))
                .cloned()
                .collect(),
            2 => self
                .recents
                .iter()
                .filter_map(|id| self.find(id))
                .collect(),
            3 => self.links.iter().map(link_raw).collect(),
            _ => self.catalog.clone(),
        };
        let q = self.query.to_lowercase();
        base.into_iter()
            .filter(|st| {
                if !q.is_empty() {
                    let hay = format!(
                        "{} {} {} {}",
                        st.name.to_lowercase(),
                        st.state.to_lowercase(),
                        st.tags.join(" ").to_lowercase(),
                        province_of(st)
                    );
                    if !hay.contains(&q) {
                        return false;
                    }
                }
                if self.nav == 0 {
                    if !self.tag.is_empty()
                        && !st.tags.iter().any(|t| t.eq_ignore_ascii_case(&self.tag))
                    {
                        return false;
                    }
                    if !self.region.is_empty() && !matches_region(st, &self.region) {
                        return false;
                    }
                }
                true
            })
            .collect()
    }

    fn find(&self, id: &str) -> Option<Raw> {
        if id.starts_with("link:") {
            return self
                .links
                .iter()
                .map(link_raw)
                .find(|st| st.id == id);
        }
        self.catalog.iter().find(|st| st.id == id).cloned()
    }

    fn record_recent(&mut self, id: &str) {
        self.recents.retain(|x| x != id);
        self.recents.insert(0, id.to_string());
        self.recents.truncate(40);
    }
}

fn build_rows(app: &mut App) -> Vec<Station> {
    let mut rows = Vec::with_capacity(app.pool.len());
    for (pos, st) in app.pool.iter().enumerate() {
        let area = area_of(st);
        let tagline = if st.tags.is_empty() {
            "中国电台".to_string()
        } else {
            st.tags.iter().take(2).map(|t| tag_cn(t)).collect::<Vec<_>>().join(" / ")
        };
        let codec = if st.hls {
            "HLS".to_string()
        } else if st.bitrate > 0 {
            format!("{}k", st.bitrate)
        } else if !st.codec.is_empty() {
            st.codec.to_uppercase()
        } else {
            "NET".to_string()
        };
        rows.push(Station {
            name: SharedString::from(st.name.clone()),
            area: SharedString::from(area),
            tagline: SharedString::from(tagline),
            codec: SharedString::from(codec),
            hls: st.hls,
            fav: app.favs.contains(&st.id),
            code: SharedString::from(format!("{:02}", pos + 1)),
        });
    }
    rows
}

fn refresh_list(ui: &AppWindow, app: &mut App) {
    app.pool = app.pool_for();
    let rows = build_rows(app);
    let n = rows.len();
    ui.set_stations(ModelRc::new(VecModel::from(rows)));
    app.current = -1;
    ui.set_current(-1);
    ui.set_total_label(SharedString::from(format!("{} 个电台", n)));
    ui.set_list_sub(SharedString::from(match (app.tag.is_empty(), app.region.is_empty()) {
        (true, true) => format!("{} 个电台 · 全部为中国", n),
        (false, true) => format!("{} 个电台 · {}", n, app.tag),
        (true, false) => format!("{} 个电台 · {}", n, app.region),
        (false, false) => format!("{} 个电台 · {} · {}", n, app.region, app.tag),
    }));
    refresh_chips(ui, app);
}

fn refresh_chips(ui: &AppWindow, app: &App) {
    let navs = [
        ("中国电台", app.catalog.len()),
        ("我的收藏", app.favs.len()),
        ("最近播放", app.recents.len()),
        ("我的直链", app.links.len()),
    ]
    .iter()
    .enumerate()
    .map(|(i, (label, n))| Chip {
        label: SharedString::from(*label),
        n: *n as i32,
        c: SharedString::from(format!("{}", n)),
        on: app.nav == i as i32,
    })
    .collect::<Vec<_>>();
    ui.set_navs(ModelRc::new(VecModel::from(navs)));

    let tags = TAGS
        .iter()
        .map(|(label, key)| Chip {
            label: SharedString::from(*label),
            n: 0,
            c: SharedString::from(""),
            on: app.tag == *key,
        })
        .collect::<Vec<_>>();
    ui.set_tags(ModelRc::new(VecModel::from(tags)));

    let regions = REGION_CHIPS
        .iter()
        .map(|label| {
            let n = app.catalog.iter().filter(|st| matches_region(st, label)).count();
            Chip {
                label: SharedString::from(*label),
                n: n as i32,
                c: SharedString::from(format!("{}", n)),
                on: app.region == *label,
            }
        })
        .collect::<Vec<_>>();
    ui.set_regions(ModelRc::new(VecModel::from(regions)));
}

fn station_url(st: &Raw) -> String {
    if st.url_resolved.trim().is_empty() {
        st.url.trim().to_string()
    } else {
        st.url_resolved.trim().to_string()
    }
}

fn show_station(ui: &AppWindow, app: &App, pos: usize) {
    let Some(st) = app.pool.get(pos) else { return };
    let (freq, mode) = station_freq(st);
    let d = digits4(&freq);
    ui.set_freq_d0(d[0]);
    ui.set_freq_d1(d[1]);
    ui.set_freq_d2(d[2]);
    ui.set_freq_d3(d[3]);
    ui.set_lcd_mode(SharedString::from(mode));
    ui.set_lcd_name(SharedString::from(st.name.clone()));
    let area = area_of(st);
    let meta = if st.hls {
        format!("{} · HLS 流 · 网络调谐", area)
    } else if st.bitrate > 0 {
        format!("{} · {}kbps · 网络调谐", area, st.bitrate)
    } else {
        format!("{} · 网络调谐", area)
    };
    ui.set_lcd_meta(SharedString::from(meta));
}

// ---------------- 纹理与字体 ----------------

fn make_grille() -> slint::Image {
    const S: f32 = 1.25;
    const W: u32 = 700;
    const H: u32 = 384;
    let mut buf = slint::SharedPixelBuffer::<slint::Rgba8Pixel>::new(W, H);
    let px = buf.make_mut_slice();
    let pitch = 10.4;
    let rad = 2.0;
    for y in 0..H {
        let yf = y as f32 / S;
        let t = yf / (H as f32 / S);
        let base = 20.5 - 2.4 * t;
        for x in 0..W {
            let xf = x as f32 / S;
            let streak = (xf * 0.37).sin() * 0.9 + (xf * 1.9).sin() * 0.5;
            let mut r = (base + streak) * 0.92;
            let mut g = (base + streak) * 0.96;
            let mut b = base + streak;
            let cx = (xf / pitch).round() * pitch;
            let cy = (yf / pitch).round() * pitch;
            let dx = xf - cx;
            let dy = yf - cy;
            let d = (dx * dx + dy * dy).sqrt();
            if d < rad {
                let k = 1.0 - (d / rad).powi(2);
                let f = 1.0 - 0.94 * k;
                r *= f;
                g *= f;
                b *= f;
            } else if d < rad + 1.15 {
                let rim = ((rad + 1.15 - d) / 0.45).clamp(0.0, 1.0);
                r += 4.5 * rim;
                g += 5.0 * rim;
                b += 5.5 * rim;
            }
            px[(y * W + x) as usize] = slint::Rgba8Pixel {
                r: r.clamp(0.0, 255.0) as u8,
                g: g.clamp(0.0, 255.0) as u8,
                b: b.clamp(0.0, 255.0) as u8,
                a: 255,
            };
        }
    }
    slint::Image::from_rgba8(buf)
}

fn register_fonts() {
    use slint::fontique_011::fontique;
    let mut coll = slint::fontique_011::shared_collection();
    for data in [
        include_bytes!("../fonts/jetbrains.ttf").as_slice(),
        include_bytes!("../fonts/instrument.ttf").as_slice(),
    ] {
        let blob = fontique::Blob::new(std::sync::Arc::new(data.to_vec()));
        coll.register_fonts(blob, None);
    }
}

fn local_hhmm() -> String {
    let secs = std::time::SystemTime::now()
        .duration_since(std::time::UNIX_EPOCH)
        .map(|d| d.as_secs())
        .unwrap_or(0);
    let t = (secs + 8 * 3600) % 86400;
    format!("{:02}:{:02}", t / 3600, (t % 3600) / 60)
}

fn set_clock(ui: &AppWindow, text: &str) {
    ui.set_clock_text(SharedString::from(text));
    let d: Vec<i32> = text.chars().filter_map(|c| c.to_digit(10).map(|x| x as i32)).collect();
    let g = |i: usize| *d.get(i).unwrap_or(&0);
    ui.set_clock_d0(g(0));
    ui.set_clock_d1(g(1));
    ui.set_clock_d2(g(2));
    ui.set_clock_d3(g(3));
}

fn make_knob(vol: i32) -> slint::Image {
    const W: u32 = 92;
    const S: f32 = 2.0;
    let mut buf = slint::SharedPixelBuffer::<slint::Rgba8Pixel>::new(W, W);
    let px = buf.make_mut_slice();
    let c = 23.0f32;
    let ang = (-132.0 + vol as f32 * 2.64).to_radians();
    let (sa, ca) = (ang.sin(), ang.cos());
    let pivot = 21.8f32;
    for y in 0..W {
        for x in 0..W {
            let xf = x as f32 / S - c;
            let yf = y as f32 / S - c;
            let r = (xf * xf + yf * yf).sqrt();
            let mut col = [0.0f32; 3];
            let mut alpha = ((23.35 - r) / 0.9).clamp(0.0, 1.0);
            if alpha > 0.0 {
                let rf = r / 23.0;
                let mut body = if rf < 0.575 {
                    [42.0, 46.0, 50.0]
                } else if rf < 0.64 {
                    [14.0, 16.0, 18.0]
                } else {
                    [36.0, 40.0, 43.0]
                };
                // repeating conic tick marks, only on the outer ring
                if r > 16.0 && r < 22.2 {
                    let mut deg = yf.atan2(xf).to_degrees() + 90.0;
                    if deg < 0.0 {
                        deg += 360.0;
                    }
                    let b = (deg + 132.0) % 13.2;
                    if b < 1.15 {
                        let k = ((r - 16.0) / 1.6).clamp(0.0, 1.0) * ((22.2 - r) / 1.6).clamp(0.0, 1.0);
                        body[0] += 26.0 * k;
                        body[1] += 28.0 * k;
                        body[2] += 30.0 * k;
                    }
                }
                // outer dark rim
                if r > 22.2 {
                    let k = ((23.0 - r) / 0.8).clamp(0.0, 1.0);
                    for i in 0..3 {
                        body[i] = 6.0 + (body[i] - 6.0) * k * 0.6;
                    }
                }
                // cap
                if r <= 16.0 {
                    let t = ((xf * 0.342 + yf * 0.940) / 32.0 + 0.5).clamp(0.0, 1.0);
                    let (lo, mid, hi) = ([52.0, 57.0, 61.0], [33.0, 37.0, 41.0], [23.0, 26.0, 28.0]);
                    for i in 0..3 {
                        body[i] = if t < 0.55 {
                            lo[i] + (mid[i] - lo[i]) * (t / 0.55)
                        } else {
                            mid[i] + (hi[i] - mid[i]) * ((t - 0.55) / 0.45)
                        };
                    }
                    let sx = xf + 4.48;
                    let sy = yf + 7.04;
                    let sd = (sx * sx + sy * sy).sqrt();
                    if sd < 14.4 {
                        let k = (1.0 - sd / 14.4).powf(1.6) * 0.30;
                        for i in 0..3 {
                            body[i] += (255.0 - body[i]) * k;
                        }
                    }
                    if yf < -14.4 {
                        for i in 0..3 {
                            body[i] += (255.0 - body[i]) * 0.10;
                        }
                    }
                    // indicator, rotated about pivot below the cap centre
                    let vx = xf;
                    let vy = yf - pivot;
                    let rx = vx * ca + vy * sa;
                    let ry = -vx * sa + vy * ca + pivot;
                    let dx = (rx.abs() - 1.0).max(0.0);
                    let dy = ((ry + 8.0).abs() - 5.44).max(0.0);
                    let d = (dx * dx + dy * dy).sqrt();
                    if d < 4.0 {
                        let k = (1.0 - d / 4.0).powf(2.0) * 0.34;
                        for i in 0..3 {
                            body[i] += (255.0 - body[i]) * k;
                        }
                    }
                    if d < 0.9 {
                        let k = (1.0 - d / 0.9).clamp(0.0, 1.0);
                        for i in 0..3 {
                            body[i] = body[i] + ([241.0, 242.0, 236.0][i] - body[i]) * k;
                        }
                    }
                }
                col = body;
            }
            px[(y * W + x) as usize] = slint::Rgba8Pixel {
                r: col[0].clamp(0.0, 255.0) as u8,
                g: col[1].clamp(0.0, 255.0) as u8,
                b: col[2].clamp(0.0, 255.0) as u8,
                a: (alpha * 255.0) as u8,
            };
        }
    }
    slint::Image::from_rgba8(buf)
}

// ---------------- 主程序 ----------------

fn main() -> Result<(), Box<dyn std::error::Error>> {
    log::prepare();
    std::panic::set_hook(Box::new(|info| {
        log::log(format!("PANIC {info}"));
    }));
    log::log("启动");
    slint::BackendSelector::new()
        .backend_name("winit".into())
        .renderer_name("software".into())
        .select()?;
    register_fonts();

    let ui = AppWindow::new()?;
    ui.set_grille(make_grille());
    ui.set_online(true);
    let audio = match audio::Audio::new() {
        Ok(a) => Some(a),
        Err(e) => {
            log::log(format!("音频输出初始化失败: {e}"));
            None
        }
    };
    let audio = Rc::new(audio);

    let saved = load_state();
    let volume = saved.volume.unwrap_or(70).clamp(0, 100);
    ui.set_knob(make_knob(volume));
    if let Some(au) = audio.as_ref() {
        au.set_volume(volume);
    }
    #[cfg(windows)]
    let run = run_entry();
    #[cfg(not(windows))]
    let run: Option<String> = None;
    let autostart = saved.autostart.unwrap_or(run.is_some());
    let autoplay = saved
        .autoplay
        .unwrap_or_else(|| run.as_deref().map(|s| s.contains("--autoplay")).unwrap_or(true));
    let resume = saved.resume.unwrap_or(true);
    let shuffle = saved.shuffle.unwrap_or(false);
    let skip = saved.skip.unwrap_or(true);
    ui.set_opt_autostart(autostart);
    ui.set_opt_autoplay(autoplay);
    ui.set_opt_resume(resume);
    ui.set_opt_shuffle(shuffle);
    ui.set_opt_skip(skip);
    #[cfg(windows)]
    apply_autostart(autostart, autoplay);
    let app = Rc::new(RefCell::new(App {
        catalog: load_catalog(),
        links: saved.links.clone(),
        pool: Vec::new(),
        nav: 0,
        tag: String::new(),
        region: String::new(),
        query: String::new(),
        current: -1,
        favs: saved.favs.iter().cloned().collect(),
        recents: saved.recents.clone(),
        volume,
        muted: false,
        autostart,
        autoplay,
        resume,
        shuffle,
        skip,
    }));
    ui.set_volume(volume);
    {
        let mut a = app.borrow_mut();
        refresh_list(&ui, &mut a);
    }

    let ui_auto = ui.as_weak();
    let app_auto = app.clone();
    let audio_auto = audio.clone();

    ui.on_set_query({
        let ui = ui.as_weak();
        let app = app.clone();
        move |q| {
            let Some(ui) = ui.upgrade() else { return };
            let mut a = app.borrow_mut();
            a.query = q.to_string();
            if ui.get_query() != q {
                ui.set_query(q.clone());
            }
            refresh_list(&ui, &mut a);
        }
    });

    ui.on_pick_tag({
        let ui = ui.as_weak();
        let app = app.clone();
        move |i| {
            let Some(ui) = ui.upgrade() else { return };
            let Some((_, key)) = TAGS.get(i as usize) else { return };
            let mut a = app.borrow_mut();
            a.tag = if a.tag == *key { String::new() } else { key.to_string() };
            refresh_list(&ui, &mut a);
        }
    });

    ui.on_pick_region({
        let ui = ui.as_weak();
        let app = app.clone();
        move |i| {
            let Some(ui) = ui.upgrade() else { return };
            let Some(label) = REGION_CHIPS.get(i as usize) else { return };
            let mut a = app.borrow_mut();
            a.region = if a.region == *label { String::new() } else { label.to_string() };
            refresh_list(&ui, &mut a);
        }
    });

    ui.on_pick_nav({
        let ui = ui.as_weak();
        let app = app.clone();
        move |i| {
            let Some(ui) = ui.upgrade() else { return };
            let mut a = app.borrow_mut();
            a.nav = i;
            a.tag.clear();
            a.region.clear();
            a.query.clear();
            ui.set_query(Default::default());
            ui.set_list_title(SharedString::from(match i {
                1 => "我的收藏",
                2 => "最近播放",
                3 => "我的直链",
                _ => "电台库",
            }));
            refresh_list(&ui, &mut a);
        }
    });

    ui.on_play_row({
        let ui = ui.as_weak();
        let app = app.clone();
        let audio = audio.clone();
        move |pos| {
            let Some(ui) = ui.upgrade() else { return };
            let mut a = app.borrow_mut();
            let pos = pos as usize;
            if pos >= a.pool.len() {
                return;
            }
            a.current = pos as i32;
            ui.set_current(pos as i32);
            show_station(&ui, &a, pos);
            let st = a.pool[pos].clone();
            a.record_recent(&st.id);
            a.save();
            if let Some(au) = audio.as_ref() {
                au.play(&station_url(&st));
            }
        }
    });

    ui.on_toggle_fav({
        let ui = ui.as_weak();
        let app = app.clone();
        move |pos| {
            let Some(ui) = ui.upgrade() else { return };
            let mut a = app.borrow_mut();
            let Some(st) = a.pool.get(pos as usize) else { return };
            let id = st.id.clone();
            if !a.favs.remove(&id) {
                a.favs.insert(id);
            }
            a.save();
            let keep_current = a.current;
            refresh_list(&ui, &mut a);
            ui.set_current(keep_current);
            a.current = keep_current;
        }
    });

    ui.on_vol_adjust({
        let ui = ui.as_weak();
        let app = app.clone();
        let audio = audio.clone();
        move |delta| {
            let Some(ui) = ui.upgrade() else { return };
            let mut a = app.borrow_mut();
            a.volume = (a.volume + delta).clamp(0, 100);
            a.muted = false;
            ui.set_muted(false);
            ui.set_volume(a.volume);
            ui.set_knob(make_knob(a.volume));
            a.save();
            if let Some(au) = audio.as_ref() {
                au.set_volume(a.volume);
            }
        }
    });

    ui.on_prev_station({
        let ui = ui.as_weak();
        let app = app.clone();
        let audio = audio.clone();
        move || {
            let Some(ui) = ui.upgrade() else { return };
            let mut a = app.borrow_mut();
            if a.pool.is_empty() {
                return;
            }
            let pos = if a.shuffle {
                rnd_pos(a.pool.len(), a.current)
            } else if a.current <= 0 {
                a.pool.len() - 1
            } else {
                a.current as usize - 1
            };
            a.current = pos as i32;
            ui.set_current(pos as i32);
            show_station(&ui, &a, pos);
            let st = a.pool[pos].clone();
            if a.nav != 2 {
                a.record_recent(&st.id);
                a.save();
            }
            if let Some(au) = audio.as_ref() {
                au.play(&station_url(&st));
            }
        }
    });

    ui.on_next_station({
        let ui = ui.as_weak();
        let app = app.clone();
        let audio = audio.clone();
        move || {
            let Some(ui) = ui.upgrade() else { return };
            let mut a = app.borrow_mut();
            if a.pool.is_empty() {
                return;
            }
            let pos = if a.shuffle {
                rnd_pos(a.pool.len(), a.current)
            } else if a.current < 0 || a.current as usize + 1 >= a.pool.len() {
                0
            } else {
                a.current as usize + 1
            };
            a.current = pos as i32;
            ui.set_current(pos as i32);
            show_station(&ui, &a, pos);
            let st = a.pool[pos].clone();
            if a.nav != 2 {
                a.record_recent(&st.id);
                a.save();
            }
            if let Some(au) = audio.as_ref() {
                au.play(&station_url(&st));
            }
        }
    });

    ui.on_toggle_mute({
        let ui = ui.as_weak();
        let app = app.clone();
        let audio = audio.clone();
        move || {
            let Some(ui) = ui.upgrade() else { return };
            let mut a = app.borrow_mut();
            a.muted = !a.muted;
            ui.set_muted(a.muted);
            ui.set_volume(if a.muted { 0 } else { a.volume });
            ui.set_knob(make_knob(if a.muted { 0 } else { a.volume }));
            if let Some(au) = audio.as_ref() {
                au.set_volume(if a.muted { 0 } else { a.volume });
            }
        }
    });

    ui.on_add_link({
        let ui = ui.as_weak();
        let app = app.clone();
        let audio = audio.clone();
        move |name, url| {
            let Some(ui) = ui.upgrade() else { return };
            let url = url.trim().to_string();
            if url.is_empty() {
                return;
            }
            log::log(format!("加直链 {url}"));
            if !url.starts_with("http://") && !url.starts_with("https://") {
                ui.set_lcd_status(SharedString::from("地址需 http(s)"));
                return;
            }
            let rec = LinkRec { name: name.trim().to_string(), url };
            let mut a = app.borrow_mut();
            a.links.retain(|l| l.url != rec.url);
            a.links.insert(0, rec.clone());
            a.nav = 3;
            a.tag.clear();
            a.region.clear();
            a.query.clear();
            ui.set_query(Default::default());
            ui.set_list_title(SharedString::from("我的直链"));
            a.save();
            refresh_list(&ui, &mut a);
            let st = link_raw(&rec);
            a.current = 0;
            ui.set_current(0);
            show_station(&ui, &a, 0);
            if let Some(au) = audio.as_ref() {
                au.play(&station_url(&st));
            }
        }
    });

    ui.on_set_option({
        let ui = ui.as_weak();
        let app = app.clone();
        move |key, val| {
            let Some(ui) = ui.upgrade() else { return };
            let mut a = app.borrow_mut();
            match key.as_str() {
                "autostart" => {
                    a.autostart = val;
                    ui.set_opt_autostart(val);
                    #[cfg(windows)]
                    apply_autostart(val, a.autoplay);
                }
                "autoplay" => {
                    a.autoplay = val;
                    ui.set_opt_autoplay(val);
                    #[cfg(windows)]
                    {
                        if a.autostart {
                            apply_autostart(true, val);
                        }
                    }
                }
                "resume" => {
                    a.resume = val;
                    ui.set_opt_resume(val);
                }
                "shuffle" => {
                    a.shuffle = val;
                    ui.set_opt_shuffle(val);
                }
                "skip" => {
                    a.skip = val;
                    ui.set_opt_skip(val);
                }
                _ => return,
            }
            log::log(format!("设置 {key} = {val}"));
            a.save();
        }
    });

    ui.on_toggle_play({
        let ui = ui.as_weak();
        let app = app.clone();
        let audio = audio.clone();
        move || {
            let Some(ui) = ui.upgrade() else { return };
            let mut a = app.borrow_mut();
            let Some(au) = audio.as_ref() else { return };
            if ui.get_playing() || ui.get_loading() {
                au.stop();
                ui.set_playing(false);
                ui.set_loading(false);
                ui.set_lcd_status(SharedString::from("待机 STANDBY"));
                return;
            }
            let pos = if a.current < 0 {
                if a.pool.is_empty() {
                    return;
                }
                a.current = 0;
                ui.set_current(0);
                show_station(&ui, &a, 0);
                0usize
            } else {
                a.current as usize
            };
            if let Some(st) = a.pool.get(pos).cloned() {
                au.play(&station_url(&st));
            }
        }
    });

    ui.on_win_minimize({
        let ui = ui.as_weak();
        move || {
            if let Some(ui) = ui.upgrade() {
                ui.window().set_minimized(true);
            }
        }
    });

    ui.on_win_maximize({
        let ui = ui.as_weak();
        move || {
            if let Some(ui) = ui.upgrade() {
                let maxed = !ui.get_maxed();
                ui.set_maxed(maxed);
                ui.window().set_maximized(maxed);
            }
        }
    });

    ui.on_win_close({
        let ui = ui.as_weak();
        move || {
            if let Some(ui) = ui.upgrade() {
                let _ = ui.hide();
            }
            let _ = slint::quit_event_loop();
        }
    });

    ui.on_win_drag({
        let ui = ui.as_weak();
        move || {
            if let Some(ui) = ui.upgrade() {
                ui.window().with_winit_window(|w| {
                    let _ = w.drag_window();
                });
            }
        }
    });

    ui.set_lcd_status(SharedString::from("待机 STANDBY"));
    let status_timer = slint::Timer::default();
    let in_error = Rc::new(std::cell::Cell::new(false));
    let failures = Rc::new(std::cell::Cell::new(0i32));
    {
        let ui = ui.as_weak();
        let audio = audio.clone();
        let app = app.clone();
        let in_error = in_error.clone();
        let failures = failures.clone();
        status_timer.start(
            slint::TimerMode::Repeated,
            std::time::Duration::from_millis(280),
            move || {
                let Some(ui) = ui.upgrade() else { return };
                let Some(au) = audio.as_ref() else { return };
                let snap = au.snapshot();
                if ui.get_playing() != snap.playing {
                    ui.set_playing(snap.playing);
                }
                if ui.get_loading() != snap.loading {
                    ui.set_loading(snap.loading);
                }
                if snap.error.is_some() {
                    if !in_error.get() {
                        in_error.set(true);
                        let n = failures.get() + 1;
                        failures.set(n);
                        let skip = app.borrow().skip;
                        if skip && n <= 8 {
                            ui.invoke_next_station();
                        } else {
                            ui.set_lcd_status(SharedString::from("无信号 NO SIGNAL"));
                            ui.set_playing(false);
                        }
                    }
                    return;
                }
                in_error.set(false);
                if snap.playing && failures.get() != 0 {
                    failures.set(0);
                }
                let status = if ui.get_muted() {
                    "静音 MUTE"
                } else if snap.loading {
                    "调谐 TUNING"
                } else if snap.playing {
                    "接收 ON AIR"
                } else {
                    "待机 STANDBY"
                };
                if ui.get_lcd_status() != status {
                    ui.set_lcd_status(SharedString::from(status));
                }
            },
        );
    }

    set_clock(&ui, &local_hhmm());
    let timer = slint::Timer::default();
    {
        let ui = ui.as_weak();
        timer.start(
            slint::TimerMode::Repeated,
            std::time::Duration::from_secs(1),
            move || {
                if let Some(ui) = ui.upgrade() {
                    set_clock(&ui, &local_hhmm());
                }
            },
        );
    }

    ui.show()?;
    if std::env::args().any(|a| a == "--autoplay") {
        let t = slint::Timer::default();
        t.start(
            slint::TimerMode::SingleShot,
            std::time::Duration::from_millis(600),
            move || {
                let (Some(ui), Some(au)) = (ui_auto.upgrade(), audio_auto.as_ref()) else { return };
                let mut a = app_auto.borrow_mut();
                if a.pool.is_empty() || !a.autoplay {
                    return;
                }
                let resume = if a.resume {
                    a.recents
                        .first()
                        .and_then(|id| a.pool.iter().position(|s| &s.id == id))
                } else {
                    None
                };
                let pos = resume.unwrap_or_else(|| if a.shuffle { rnd_pos(a.pool.len(), -1) } else { 0 });
                a.current = pos as i32;
                ui.set_current(pos as i32);
                show_station(&ui, &a, pos);
                let st = a.pool[pos].clone();
                a.record_recent(&st.id);
                a.save();
                au.play(&station_url(&st));
            },
        );
        std::mem::forget(t);
    }
    ui.run()?;
    drop(timer);
    drop(status_timer);
    Ok(())
}
