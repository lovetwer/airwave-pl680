'use strict';

/* 安装包地址：上传到 R2 后替换这里的两行即可，页面上的下载按钮统一从这里取值 */
const DOWNLOADS = {
  win: 'https://github.com/lovetwer',
  apk: 'https://github.com/lovetwer',
  repo: 'https://github.com/lovetwer',
};

const $ = (s, r = document) => r.querySelector(s);
const $$ = (s, r = document) => Array.from(r.querySelectorAll(s));

/* ---------- 电台频率：和桌面/安卓端同一套公式 ---------- */
function hashRatio(s) {
  let h = 0;
  for (let i = 0; i < s.length; i++) h = (h * 31 + s.charCodeAt(i)) % 99991;
  return (h % 1000) / 1000;
}

function freqOf(st) {
  if (st.hls || st.id.startsWith('link:')) {
    const seed = st.url || st.id;
    const v = 5900 + hashRatio(seed) * 4000;
    return { text: String(Math.round(v / 5) * 5), mode: 'NET' };
  }
  const v = 87.5 + hashRatio(st.id || st.name) * 12.45;
  return { text: v.toFixed(2), mode: 'FM' };
}

/* ---------- 七段数码管 ---------- */
const SEG = {
  '0': 'abcdef', '1': 'bc', '2': 'abdeg', '3': 'abcdg', '4': 'bcfg',
  '5': 'acdfg', '6': 'acdefg', '7': 'abc', '8': 'abcdefg', '9': 'abcdfg',
};

function digitEl(ch) {
  const d = document.createElement('span');
  d.className = 'segdig';
  const on = SEG[ch] || '';
  for (const s of 'abcdefg') {
    const i = document.createElement('i');
    i.className = s + (on.includes(s) ? ' on' : '');
    d.appendChild(i);
  }
  return d;
}

function renderDigits(str, mode) {
  const box = $('#digits');
  box.textContent = '';
  box.setAttribute('aria-label', mode === 'FM' ? str + ' MHz' : str + ' kHz');
  const chars = str.replace(/\D/g, '').padEnd(4, ' ').slice(0, 4).split('');
  chars.forEach((ch, i) => {
    if (i === 2) {
      const slot = document.createElement('span');
      slot.className = 'dotslot' + (mode === 'FM' ? ' on' : '');
      slot.innerHTML = '<i class="dot"></i>';
      box.appendChild(slot);
    }
    box.appendChild(digitEl(ch));
  });
}

function renderClock() {
  const now = new Date();
  const hh = String(now.getHours()).padStart(2, '0');
  const mm = String(now.getMinutes()).padStart(2, '0');
  const box = $('#clock');
  box.textContent = '';
  [hh[0], hh[1]].forEach((c) => box.appendChild(digitEl(c)));
  const colon = document.createElement('span');
  colon.className = 'segcolon';
  box.appendChild(colon);
  [mm[0], mm[1]].forEach((c) => box.appendChild(digitEl(c)));
}

/* ---------- 状态 ---------- */
const audio = $('#audio');
const deck = $('#deck');
const state = {
  stations: [],
  view: [],
  area: '全部',
  q: '',
  current: -1,
  playing: false,
  loading: false,
  favs: new Set(JSON.parse(localStorage.getItem('aw-favs') || '[]')),
  skipTimer: 0,
  stallTimer: 0,
};

let toastTimer = 0;
function toast(msg) {
  const el = $('#toast');
  el.textContent = msg;
  el.classList.add('show');
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => el.classList.remove('show'), 4200);
}

/* ---------- 列表 ---------- */
function areaList() {
  const count = new Map();
  for (const s of state.stations) count.set(s.area, (count.get(s.area) || 0) + 1);
  const order = ['全国', '北京', '上海', '广东', '浙江', '四川', '陕西', '湖北', '云南', '新疆', '江西', '安徽'];
  const areas = order.filter((a) => count.has(a));
  for (const [a, n] of count) if (!areas.includes(a) && n >= 2) areas.push(a);
  return areas;
}

function buildChips() {
  const box = $('#chips');
  box.textContent = '';
  const mk = (label, val, fav) => {
    const b = document.createElement('button');
    b.type = 'button';
    b.className = 'chip';
    b.textContent = label;
    b.setAttribute('aria-pressed', String(state.area === val));
    b.addEventListener('click', () => {
      state.area = val;
      $$('.chip', box).forEach((c) => c.setAttribute('aria-pressed', 'false'));
      b.setAttribute('aria-pressed', 'true');
      applyFilter();
    });
    if (fav) b.dataset.fav = '1';
    box.appendChild(b);
  };
  mk('全部', '全部');
  mk('★ 收藏', '收藏');
  areaList().forEach((a) => mk(a, a));
}

function applyFilter() {
  const q = state.q.trim().toLowerCase();
  state.view = state.stations.filter((s) => {
    if (state.area === '收藏') return state.favs.has(s.id);
    if (state.area !== '全部' && s.area !== state.area) return false;
    if (!q) return true;
    return (
      s.name.toLowerCase().includes(q) ||
      s.area.includes(q) ||
      s.tags.join(' ').toLowerCase().includes(q)
    );
  });
  renderList();
}

function renderList() {
  const list = $('#list');
  list.textContent = '';
  const frag = document.createDocumentFragment();

  state.view.forEach((st, i) => {
    const idx = state.stations.indexOf(st) + 1;
    const f = freqOf(st);
    const row = document.createElement('button');
    row.type = 'button';
    row.className = 'row' + (state.stations[state.current] === st ? ' on' : '');
    row.setAttribute('role', 'listitem');
    row.innerHTML =
      `<span class="idx mono">${String(idx).padStart(3, '0')}</span>` +
      '<span class="dot"></span>' +
      `<span class="meta"><span class="nm"></span><span class="sub"><span class="ar"></span><span>·</span><span class="tg"></span></span></span>` +
      `<span class="freq"></span><span class="badge">MP3</span>`;
    row.querySelector('.nm').textContent = st.name;
    row.querySelector('.ar').textContent = st.area;
    row.querySelector('.tg').textContent = st.tags.join(' / ');
    row.querySelector('.freq').textContent = f.mode === 'FM' ? f.text : f.text;
    row.addEventListener('click', () => play(st));
    frag.appendChild(row);
  });

  if (!state.view.length) {
    const empty = document.createElement('div');
    empty.className = 'empty';
    empty.textContent = state.area === '收藏'
      ? '还没有收藏。点列表右侧的 ☆，或者按 F。'
      : '没有匹配的电台，换个关键词试试。';
    frag.appendChild(empty);
  }
  list.appendChild(frag);
  $('#libSub').textContent = `${state.view.length} / ${state.stations.length} 个台 · 网页可直连`;
}

/* ---------- 播放 ---------- */
function stationFreqText(st) {
  const f = freqOf(st);
  return f.mode === 'FM' ? `FM ${f.text}` : `NET ${f.text}`;
}

function paint(st) {
  if (!st) return;
  const f = freqOf(st);
  renderDigits(f.text, f.mode);
  $('#lcdMode').textContent = f.mode;
  $('#lcdName').textContent = st.name;
  $('#lcdMeta').textContent = `${st.area} · ${st.tags.join(' / ')} · ${f.mode} ${f.text}`;
  $('#lcdBit').textContent = '64 kbps · MP3';
  $('#star').classList.toggle('on', state.favs.has(st.id));
  $('#star').textContent = state.favs.has(st.id) ? '★' : '☆';
  $$('.row').forEach((r) => r.classList.remove('on'));
  const idx = state.view.indexOf(st);
  const rows = $$('.row');
  if (idx >= 0 && rows[idx]) rows[idx].classList.add('on');
}

function setStatus(kind) {
  const el = $('#lcdStatus');
  if (kind === 'playing') el.textContent = '在线 ON AIR';
  else if (kind === 'loading') el.textContent = '调谐中 TUNING';
  else if (kind === 'error') el.textContent = '无信号 NO SIGNAL';
  else el.textContent = '待机 STANDBY';
  deck.classList.toggle('is-playing', kind === 'playing');
  deck.classList.toggle('is-loading', kind === 'loading');
  deck.classList.toggle('power-off', kind !== 'playing');
  state.playing = kind === 'playing';
  state.loading = kind === 'loading';
  $('#playIco').textContent = state.playing ? '❚❚' : '▶';
  $('#playTxt').textContent = state.playing ? 'PAUSE' : 'PLAY';
  $('#play').classList.toggle('on', state.playing);
}

function clearTimers() {
  clearTimeout(state.skipTimer);
  clearTimeout(state.stallTimer);
}

function play(st) {
  clearTimers();
  if (state.current >= 0 && state.stations[state.current] === st && state.playing) {
    audio.pause();
    setStatus('idle');
    return;
  }
  state.current = state.stations.indexOf(st);
  paint(st);
  setStatus('loading');
  audio.src = st.url;
  audio.volume = Number($('#vol').value) / 100;
  audio.muted = $('#mute').classList.contains('on');
  audio.play().catch(() => fail(st, '浏览器未能开始播放'));
  state.stallTimer = setTimeout(() => {
    if (!state.playing) fail(st, '连接超时');
  }, 11000);
}

function fail(st, why) {
  clearTimers();
  setStatus('error');
  toast(`${st.name} 暂时连不上（${why}），已自动换台`);
  const i = state.stations.indexOf(st);
  const nxt = state.stations[(i + 1 + state.stations.length) % state.stations.length];
  state.skipTimer = setTimeout(() => play(nxt), 1400);
}

function step(dir) {
  if (!state.stations.length) return;
  const list = state.view.length ? state.view : state.stations;
  const cur = list.indexOf(state.stations[state.current]);
  const next = list[((cur < 0 ? 0 : cur + dir) + list.length) % list.length];
  play(next);
}

function togglePlay() {
  if (state.playing) {
    audio.pause();
    setStatus('idle');
    return;
  }
  if (state.current < 0) {
    play(state.view[0] || state.stations[0]);
    return;
  }
  audio.play().catch(() => fail(state.stations[state.current], '浏览器拦截'));
}

/* ---------- 事件 ---------- */
audio.addEventListener('playing', () => {
  clearTimers();
  setStatus('playing');
});
audio.addEventListener('pause', () => {
  if (!state.loading) setStatus('idle');
});
audio.addEventListener('error', () => {
  if (state.current >= 0) fail(state.stations[state.current], '流已失效');
});
audio.addEventListener('stalled', () => {
  if (state.playing) setStatus('loading');
});

$('#play').addEventListener('click', togglePlay);
$('#power').addEventListener('click', togglePlay);
$('#prev').addEventListener('click', () => step(-1));
$('#next').addEventListener('click', () => step(1));

$('#vol').addEventListener('input', (e) => {
  const v = Number(e.target.value);
  audio.volume = v / 100;
  $('#volVal').textContent = v;
  if (v > 0 && $('#mute').classList.contains('on')) {
    $('#mute').classList.remove('on');
    audio.muted = false;
  }
});

$('#mute').addEventListener('click', () => {
  const on = $('#mute').classList.toggle('on');
  audio.muted = on;
});

$('#star').addEventListener('click', () => {
  const st = state.stations[state.current];
  if (!st) { toast('先选一个电台'); return; }
  if (state.favs.has(st.id)) state.favs.delete(st.id);
  else state.favs.add(st.id);
  localStorage.setItem('aw-favs', JSON.stringify([...state.favs]));
  paint(st);
  if (state.area === '收藏') applyFilter();
});

$('#q').addEventListener('input', (e) => {
  state.q = e.target.value;
  applyFilter();
});

document.addEventListener('keydown', (e) => {
  if (e.target.matches('input, textarea')) {
    if (e.key === 'Escape') e.target.blur();
    return;
  }
  if (e.code === 'Space') { e.preventDefault(); togglePlay(); }
  else if (e.key === 'ArrowLeft') { e.preventDefault(); step(-1); }
  else if (e.key === 'ArrowRight') { e.preventDefault(); step(1); }
  else if (e.key === '/') { e.preventDefault(); $('#q').focus(); }
  else if (e.key === 'm' || e.key === 'M') { $('#mute').click(); }
  else if (e.key === 'f' || e.key === 'F') { $('#star').click(); }
});

/* ---------- 启动 ---------- */
function wireDownloads() {
  $('#dlWin').href = DOWNLOADS.win;
  $('#dlApk').href = DOWNLOADS.apk;
  $('#dlWinTop').href = DOWNLOADS.win;
  $('#dlApkTop').href = DOWNLOADS.apk;
  $('#ghLink').href = DOWNLOADS.repo;
  $('#srcLink').href = DOWNLOADS.repo;
  $('#ghFoot').href = DOWNLOADS.repo;
}

async function boot() {
  wireDownloads();
  renderClock();
  setInterval(renderClock, 20000);
  renderDigits('87.50', 'FM');
  setStatus('idle');

  try {
    const res = await fetch('assets/stations.json', { cache: 'no-cache' });
    state.stations = await res.json();
  } catch (err) {
    $('#libSub').textContent = '电台目录加载失败';
    return;
  }
  buildChips();
  applyFilter();
}

boot();
