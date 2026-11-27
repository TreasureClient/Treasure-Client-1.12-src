// Treasure Launcher's page. Talks to the Rust side through Tauri's invoke() and events
// (see app/src-tauri/src/main.rs for the list).
'use strict';

const T = window.__TAURI__;
const invoke = (cmd, args) => (T ? T.core.invoke(cmd, args) : Promise.reject('Open this inside Treasure Launcher.'));
const listen = (ev, cb) => T && T.event.listen(ev, e => cb(e.payload));
const $ = s => document.querySelector(s), $$ = s => [...document.querySelectorAll(s)];
const esc = s => String(s ?? '').replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
const errText = e => String(e && e.message ? e.message : e);

const S = {
  settings: {}, instances: [], accounts: [], signinAvailable: false, supported: [], version: '',
  current: null,          // instance shown on Home / instance page
  running: new Set(),     // instance ids with a game open
  jobs: {},               // id -> {stage, done, total}
  logs: {},               // id -> lines from the running game
  started: {},            // id -> Date.now() when the game started
  versions: [],           // Minecraft releases
  notices: [], unread: 0,
};

// ---------------------------------------------------------------- small helpers
let toastTimer;
function toast(text, err) {
  const t = $('#toast');
  t.textContent = text; t.className = 'toast' + (err ? ' err' : ''); t.hidden = false;
  clearTimeout(toastTimer); toastTimer = setTimeout(() => (t.hidden = true), err ? 7000 : 3000);
}
function notice(title, text, err) {
  S.notices.unshift({ title, text, err, at: Date.now() }); S.notices.length = Math.min(S.notices.length, 30);
  S.unread++; renderBell();
}
function fail(e) { toast(errText(e), true); }
const fmtTime = s => { const m = Math.floor((s || 0) / 60); return m >= 60 ? `${Math.floor(m / 60)} h ${String(m % 60).padStart(2, '0')} min` : `${m} min`; };
function ago(ts) {
  if (!ts) return 'Never played';
  const d = Date.now() / 1000 - ts;
  if (d < 60) return 'Just now'; if (d < 3600) return `${Math.floor(d / 60)} min ago`; if (d < 86400) return `${Math.floor(d / 3600)} h ago`;
  if (d < 172800) return 'Yesterday'; if (d < 604800) return `${Math.floor(d / 86400)} days ago`; return new Date(ts * 1000).toLocaleDateString();
}
const mb = b => b >= 1e9 ? (b / 1e9).toFixed(1) + ' GB' : (b / 1e6).toFixed(b < 1e7 ? 1 : 0) + ' MB';
const num = n => n >= 1e6 ? (n / 1e6).toFixed(1) + 'M' : n >= 1e3 ? Math.round(n / 1e3) + 'k' : String(n);
const inst = id => S.instances.find(i => i.id === id);
function hash(s) { let h = 7; for (const c of String(s)) h = (h * 31 + c.charCodeAt(0)) >>> 0; return h % 997; }

// ---------------------------------------------------------------- pixel art
function rnd(seed) { let x = seed * 9301 + 49297; return () => (x = (x * 9301 + 49297) % 233280) / 233280; }
function pixelIcon(svg, seed, a = 'var(--a1)', b = '#22170a') {
  svg.setAttribute('viewBox', '0 0 8 8'); svg.setAttribute('shape-rendering', 'crispEdges');
  const r = rnd(seed); let out = `<rect width="8" height="8" fill="${b}"/>`;
  for (let i = 0; i < 4; i++) for (let j = 1; j < 7; j++) if (r() > .5) out += `<rect x="${i}" y="${j}" width="1" height="1" fill="${a}"/><rect x="${7 - i}" y="${j}" width="1" height="1" fill="${a}"/>`;
  svg.innerHTML = out;
}
function chest(svg) {
  svg.setAttribute('viewBox', '0 0 16 16'); svg.setAttribute('shape-rendering', 'crispEdges');
  svg.innerHTML = '<rect width="16" height="16" fill="#241707"/><rect x="2" y="4" width="12" height="10" fill="var(--a3)"/><rect x="3" y="5" width="10" height="3" fill="var(--a2)"/><rect x="3" y="9" width="10" height="4" fill="var(--a2)"/><rect x="2" y="8" width="12" height="1" fill="#2a1a08"/><rect x="7" y="7" width="2" height="3" fill="var(--hi)"/>';
}
const isTreasurePack = i => i.pack && i.pack.kind === 'treasure-pack';
/** An instance's picture: the chest for Treasure Packs, the modpack's icon, or pixel art. */
function iconEl(i, cls = 'ic') {
  if (i.pack && i.pack.iconUrl && /^https:\/\/cdn\.modrinth\.com\//.test(i.pack.iconUrl)) {
    const img = document.createElement('img'); img.className = cls; img.alt = ''; img.src = i.pack.iconUrl; img.loading = 'lazy'; return img;
  }
  const svg = document.createElementNS('http://www.w3.org/2000/svg', 'svg'); svg.setAttribute('class', cls);
  isTreasurePack(i) ? chest(svg) : pixelIcon(svg, hash(i.id)); return svg;
}
function face(svg, seed) {
  const r = rnd(seed + 5), hair = ['#4a3020', '#8A5A20', '#111', '#5a3a1a', '#d8b25a'][Math.floor(r() * 5)], skin = ['#c69c77', '#d8b08a', '#b58a6a', '#e0b896', '#8d5a3a'][Math.floor(r() * 5)];
  svg.innerHTML = `<rect width="8" height="8" fill="${hair}"/><rect y="2" width="8" height="6" fill="${skin}"/><rect y="2" width="8" height="1" fill="${hair}"/><rect x="1" y="4" width="2" height="1" fill="#fff"/><rect x="2" y="4" width="1" height="1" fill="#3b4a8f"/><rect x="5" y="4" width="2" height="1" fill="#fff"/><rect x="5" y="4" width="1" height="1" fill="#3b4a8f"/><rect x="3" y="6" width="2" height="1" fill="#7a4a35"/>`;
}
/** A blocky landscape in the theme's colours, for banners and cards. */
function landscape(cv, seed) {
  if (!cv || !cv.isConnected) return;
  const r = rnd(seed + 3), dpr = Math.min(2, window.devicePixelRatio || 1);
  const w = cv.clientWidth || 300, h = cv.clientHeight || 100;
  cv.width = w * dpr; cv.height = h * dpr;
  const c = cv.getContext('2d'); c.scale(dpr, dpr);
  const css = getComputedStyle(document.documentElement), v = k => css.getPropertyValue(k).trim();
  const A1 = v('--a1'), A2 = v('--a2'), A3 = v('--a3'), BG = v('--bg0');
  const g = c.createLinearGradient(0, 0, 0, h); g.addColorStop(0, A3); g.addColorStop(1, BG); c.fillStyle = g; c.fillRect(0, 0, w, h);
  c.globalAlpha = .55; c.fillStyle = A1; c.beginPath(); c.arc(w * (.6 + r() * .3), h * .3, h * .16, 0, 7); c.fill(); c.globalAlpha = 1;
  const B = Math.max(10, Math.round(h / 16));
  [[.5, A3, .55], [.68, A2, .35], [.82, BG, 1]].forEach(([base, col, a]) => {
    c.globalAlpha = a; c.fillStyle = col; let y = h * base;
    for (let x = 0; x < w; x += B) { y += (r() - .5) * B * 1.6; y = Math.max(h * (base - .15), Math.min(h * (base + .1), y)); const yy = Math.round(y / B) * B; c.fillRect(x, yy, B, h - yy); }
  });
  c.globalAlpha = .18; c.strokeStyle = '#000';
  for (let x = 0; x < w; x += B) for (let y = Math.round(h * .5 / B) * B; y < h; y += B) c.strokeRect(x + .5, y + .5, B, B);
  c.globalAlpha = 1;
}

// ---------------------------------------------------------------- navigation
const NAMES = { home: 'Home', library: 'Library', browse: 'Browse', skins: 'Skins', settings: 'Settings', inst: 'Library' };
let view = 'home';
function show(v) {
  view = v;
  $$('.view').forEach(s => (s.hidden = true)); $('#v-' + v).hidden = false; $('#scroll').scrollTop = 0;
  $$('.nav[data-view]').forEach(n => { const on = n.dataset.view === v || (v === 'inst' && n.dataset.view === 'library'); on ? n.setAttribute('aria-current', 'page') : n.removeAttribute('aria-current'); });
  $('#crumb').innerHTML = v === 'inst' && S.current ? `Library › <b>${esc(inst(S.current)?.name)}</b>` : `<b>${NAMES[v]}</b>`;
  if (v === 'home') renderHome();
  if (v === 'library') renderLibrary();
  if (v === 'browse') { renderTargets(); if (!$('#results').children.length) runSearch(); }
}
$$('.nav[data-view]').forEach(n => (n.onclick = () => show(n.dataset.view)));
document.addEventListener('click', e => {
  const g = e.target.closest('[data-goto]'); if (g) show(g.dataset.goto);
  if (e.target.closest('[data-back]')) show('library');
  if (e.target.closest('[data-new]')) openNew();
  if (e.target.closest('[data-close]')) closeNew();
  if (!e.target.closest('.playgroup')) $('#pick-menu').hidden = true;
  if (!e.target.closest('.pop, #dl-btn, #bell, #acct-btn')) $$('.pop').forEach(p => (p.hidden = true));
});
function setSlim(slim) { $('#app').classList.toggle('slim', slim); $('#set-labels').setAttribute('aria-checked', String(!slim)); }
$('#collapse').onclick = () => { const slim = !$('#app').classList.contains('slim'); setSlim(slim); S.settings.sidebarLabels = !slim; saveSettings(); };

// ---------------------------------------------------------------- data
async function refresh() {
  try { S.instances = await invoke('list_instances'); } catch (e) { fail(e); }
  if (!S.current || !inst(S.current)) S.current = S.instances[0]?.id || null;
  renderSide();
  if (view === 'home') renderHome();
  if (view === 'library') renderLibrary();
  if (view === 'inst') { if (inst(S.current)) renderInstHead(); else show('library'); }
  renderTargets(); renderPick();
}
const metaHTML = i => `<span><b>${esc(i.mcVersion || '…')}</b> · ${i.loader === 'fabric' ? 'Fabric' : 'Vanilla'}</span>${i.treasureClient ? '<span class="tag">Treasure Client</span>' : ''}${i.pack && i.pack.kind === 'modrinth' ? '<span>Modrinth modpack</span>' : ''}${i.pack && i.pack.versionNumber ? `<span>${esc(i.pack.versionNumber)}</span>` : ''}<span>Played ${fmtTime(i.playSeconds)}</span>`;

// ---------------------------------------------------------------- sidebar
function renderSide() {
  const pins = $('#pins'); pins.innerHTML = '';
  const pinned = S.instances.filter(i => i.pinned);
  pinned.forEach(i => {
    const b = document.createElement('button'); b.className = 'pin'; b.title = i.name;
    b.append(iconEl(i, '')); b.insertAdjacentHTML('beforeend', `<span>${esc(i.name)}<i>${esc(i.mcVersion)} · ${i.treasureClient ? 'TC' : i.loader === 'fabric' ? 'Fabric' : 'Vanilla'}</i></span>`);
    b.onclick = () => openInstance(i.id); pins.appendChild(b);
  });
  $('.side-h').hidden = !pinned.length;
  $('#lib-count').textContent = S.instances.length || '';
  const run = [...S.running][0];
  $('#np').hidden = !run;
  if (run) $('#np-name').textContent = inst(run)?.name || run;
  const a = S.accounts.find(x => x.active);
  $('#acct-name').textContent = a ? a.name : 'Sign in';
  $('#acct-sub').textContent = a ? 'Microsoft account' : 'to play';
  face($('#face'), hash(a ? a.uuid : 'none'));
}
setInterval(() => {
  const run = [...S.running][0]; if (!run || !S.started[run]) return;
  const s = Math.floor((Date.now() - S.started[run]) / 1000);
  $('#np-time').textContent = `${String(Math.floor(s / 3600)).padStart(2, '0')}:${String(Math.floor(s / 60) % 60).padStart(2, '0')}:${String(s % 60).padStart(2, '0')}`;
}, 1000);
$('#np-stop').onclick = () => { const run = [...S.running][0]; if (run) invoke('stop', { id: run }).catch(fail); };

// ---------------------------------------------------------------- progress
function progressText(j) { return j.total ? `${j.stage} · ${Math.floor(j.done / j.total * 100)}% (${mb(j.done)} of ${mb(j.total)})` : `${j.stage}…`; }
let progFrame = 0;
function renderProgress() {
  progFrame = 0;
  const ids = Object.keys(S.jobs);
  $('#dl-badge').hidden = !ids.length; $('#dl-badge').textContent = ids.length;
  $('#side-dl').hidden = !ids.length; $('#dl-none').hidden = !!ids.length;
  if (ids.length) { const j = S.jobs[ids[0]]; $('#side-dl-t').textContent = `${inst(ids[0])?.name || ''}: ${j.stage}`; $('#side-dl-b').style.width = (j.total ? j.done / j.total * 100 : 5) + '%'; }
  $('#dl-list').innerHTML = ids.map(id => { const j = S.jobs[id]; const p = j.total ? j.done / j.total * 100 : 5; return `<div><div style="display:flex;justify-content:space-between;gap:10px"><b style="min-width:0;overflow:hidden;text-overflow:ellipsis;white-space:nowrap">${esc(inst(id)?.name || id)}</b><span class="mono dim">${j.total ? Math.floor(p) + '%' : ''}</span></div><div class="hint">${esc(j.stage)}</div><div class="bar"><i style="width:${p}%"></i></div></div>`; }).join('');
  for (const [barSel, stSel, id] of [['#h-bar', '#h-status', S.current], ['#i-bar', '#i-status', S.current]]) {
    const j = S.jobs[id]; $(barSel).hidden = !j; $(stSel).hidden = !j;
    if (j) { $(barSel).firstElementChild.style.width = (j.total ? j.done / j.total * 100 : 3) + '%'; $(stSel).textContent = progressText(j); }
  }
  $$('[data-prog]').forEach(el => { const j = S.jobs[el.dataset.prog]; el.hidden = !j; if (j) el.firstElementChild.style.width = (j.total ? j.done / j.total * 100 : 3) + '%'; });
  renderPlayButtons();
}
const queueProgress = () => { if (!progFrame) progFrame = requestAnimationFrame(renderProgress); };
function renderPlayButtons() {
  const id = S.current, run = S.running.has(id), busy = !!S.jobs[id];
  for (const b of [$('#play'), $('#i-play')]) {
    b.classList.toggle('busy', run || busy);
    b.querySelector('span').textContent = run ? 'Stop' : busy ? 'Getting ready' : 'Play';
  }
}

// ---------------------------------------------------------------- play
async function play(id, opts = {}) {
  if (S.running.has(id)) return invoke('stop', { id }).catch(fail);
  if (S.jobs[id]) return toast('It\'s getting ready already.');
  if (!S.accounts.some(a => a.active)) { toast('Sign in with a Microsoft account that owns Minecraft first.', true); return openAccounts(); }
  S.jobs[id] = { stage: 'Getting ready', done: 0, total: 0 }; queueProgress();
  try { await invoke('play', { id, server: opts.server || null, world: opts.world || null }); }
  catch (e) { delete S.jobs[id]; queueProgress(); fail(e); }
}
$('#play').onclick = () => S.current && play(S.current);
$('#i-play').onclick = () => S.current && play(S.current);
$('#h-open').onclick = () => S.current && openInstance(S.current);

function renderPick() {
  const m = $('#pick-menu'); m.innerHTML = '<span class="mono dim" style="padding:6px 10px">Play which instance?</span>';
  S.instances.forEach(i => {
    const b = document.createElement('button'); b.setAttribute('role', 'menuitem');
    b.append(iconEl(i)); b.insertAdjacentHTML('beforeend', `<span style="min-width:0;flex:1"><b>${esc(i.name)}</b><div class="hint">${esc(i.mcVersion)} · ${i.loader === 'fabric' ? 'Fabric' : 'Vanilla'}</div></span>${i.id === S.current ? '<span class="tag">Selected</span>' : ''}`);
    b.onclick = () => { S.current = i.id; m.hidden = true; renderHome(); renderPick(); }; m.appendChild(b);
  });
  m.insertAdjacentHTML('beforeend', '<div class="sep"></div>');
  const n = document.createElement('button'); n.textContent = '+ New instance'; n.style.color = 'var(--a1)'; n.onclick = () => { m.hidden = true; openNew(); }; m.appendChild(n);
}
$('#pick').onclick = e => { e.stopPropagation(); $('#pick-menu').hidden = !$('#pick-menu').hidden; };

// ---------------------------------------------------------------- home
function card(i) {
  const b = document.createElement('div'); b.className = 'card'; b.tabIndex = 0;
  b.innerHTML = `<div class="cover"><canvas style="position:absolute;inset:0;width:100%;height:100%"></canvas><button class="qp" aria-label="Play ${esc(i.name)}"><svg width="16" height="16" viewBox="0 0 24 24" fill="currentColor"><path d="M7 4v16l13-8z"/></svg></button></div><div class="body"><div class="name">${esc(i.name)}</div><div class="sub">${esc(i.mcVersion || '…')} · ${i.loader === 'fabric' ? 'Fabric' : 'Vanilla'}${i.treasureClient ? ' · TC' : ''}</div><div class="hint">${S.running.has(i.id) ? 'Playing now' : esc(ago(i.lastPlayed))}</div><div class="bar" data-prog="${esc(i.id)}" hidden><i></i></div></div>`;
  b.querySelector('.cover').insertBefore(iconEl(i), b.querySelector('.qp'));
  requestAnimationFrame(() => landscape(b.querySelector('canvas'), hash(i.id)));
  b.onclick = e => { if (e.target.closest('.qp')) { S.current = i.id; play(i.id); } else openInstance(i.id); };
  b.onkeydown = e => { if (e.key === 'Enter') openInstance(i.id); };
  return b;
}
function renderHome() {
  const i = inst(S.current);
  $('#hero').hidden = !i;
  if (i) {
    $('#h-name').textContent = i.name; $('#h-meta').innerHTML = metaHTML(i);
    $('#h-kicker').textContent = i.lastPlayed ? `Jump back in · last played ${ago(i.lastPlayed).toLowerCase()}` : isTreasurePack(i) ? 'Ready when you are · downloads the first time you play' : 'Ready to play';
    requestAnimationFrame(() => landscape($('#hero-art'), hash(i.id)));
  }
  const hc = $('#home-cards'); hc.innerHTML = '';
  S.instances.slice(0, 4).forEach(x => hc.appendChild(card(x)));
  const nb = document.createElement('button'); nb.className = 'card new'; nb.innerHTML = '<svg width="26" height="26" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M12 5v14M5 12h14"/></svg>New instance'; nb.onclick = openNew; hc.appendChild(nb);
  renderJump(); queueProgress(); renderPlayButtons();
}
async function renderJump() {
  const recent = S.instances.filter(i => i.lastPlayed).slice(0, 3);
  const all = [];
  for (const i of recent) { try { (await invoke('worlds', { id: i.id })).slice(0, 3).forEach(w => all.push({ i, w })); } catch { /* no worlds yet */ } }
  all.sort((a, b) => b.w.lastPlayed - a.w.lastPlayed);
  const box = $('#jump'); box.innerHTML = '';
  all.slice(0, 4).forEach(({ i, w }) => {
    const b = document.createElement('button'); b.className = 'jump';
    const svg = document.createElementNS('http://www.w3.org/2000/svg', 'svg'); svg.setAttribute('class', 'ic'); pixelIcon(svg, hash(w.folder));
    b.append(svg); b.insertAdjacentHTML('beforeend', `<span style="min-width:0"><b>${esc(w.folder)}</b><span class="hint">World · ${esc(i.name)}</span></span><span class="go"><svg width="14" height="14" viewBox="0 0 24 24" fill="currentColor"><path d="M7 4v16l13-8z"/></svg></span>`);
    b.onclick = () => { S.current = i.id; renderHome(); play(i.id, { world: w.folder }); };
    box.appendChild(b);
  });
  $('#jump-wrap').hidden = !box.children.length;
}
async function loadHomeExtras() {
  try {
    const n = await invoke('news');
    const box = $('#news'); box.innerHTML = '';
    (n.news || []).slice(0, 4).forEach((x, k) => {
      const d = document.createElement('div'); d.className = 'news';
      d.innerHTML = `<canvas class="art"></canvas><div style="min-width:0">${x.tag ? `<span class="tag">${esc(x.tag)}</span>` : ''}<b>${esc(x.title)}</b><span class="hint">${esc(x.text)}</span></div>`;
      box.appendChild(d); requestAnimationFrame(() => landscape(d.querySelector('canvas'), k * 7 + 2));
    });
    $('#news-wrap').hidden = !box.children.length;
    if (n.launcher && n.launcher.version && n.launcher.version !== S.version) notice(`Treasure Launcher ${n.launcher.version} is out`, 'Get it from the Treasure Client website.');
  } catch { /* offline */ }
  try {
    const r = await invoke('search', { q: { text: '', kind: 'modpack', mc: S.supported[0] || null, loader: 'fabric', sort: 'downloads', limit: 4, categories: [], offset: 0 } });
    const box = $('#featured'); box.innerHTML = '';
    r.hits.forEach(h => box.appendChild(featRow(h)));
  } catch { $('#featured').innerHTML = '<p class="hint">Modrinth can\'t be reached right now.</p>'; }
}
function hitIcon(h) {
  if (h.icon_url && /^https:\/\//.test(h.icon_url)) { const img = document.createElement('img'); img.className = 'ic'; img.src = h.icon_url; img.alt = ''; img.loading = 'lazy'; return img; }
  const svg = document.createElementNS('http://www.w3.org/2000/svg', 'svg'); svg.setAttribute('class', 'ic'); pixelIcon(svg, hash(h.project_id)); return svg;
}
function featRow(h) {
  const d = document.createElement('div'); d.className = 'feat';
  d.append(hitIcon(h)); d.insertAdjacentHTML('beforeend', `<span style="min-width:0"><b>${esc(h.title)}</b><div class="hint">${esc(h.description)}</div></span><button class="add ghost">Install</button>`);
  d.querySelector('button').onclick = e => installPack(h, e.currentTarget);
  return d;
}

// ---------------------------------------------------------------- library
let libView = 'grid';
function renderLibrary() {
  const q = $('#lib-q').value.trim().toLowerCase(), sort = $('#lib-sort').value;
  const list = S.instances.filter(i => !q || i.name.toLowerCase().includes(q) || (i.mcVersion || '').includes(q));
  list.sort((a, b) => sort === 'name' ? a.name.localeCompare(b.name) : sort === 'time' ? b.playSeconds - a.playSeconds : Math.max(b.lastPlayed, b.created) - Math.max(a.lastPlayed, a.created));
  const groups = [...new Set(['Treasure Packs', 'My instances', 'Modpacks', ...S.instances.map(i => i.group || 'My instances')])];
  $('#groups').innerHTML = groups.map(g => `<option value="${esc(g)}">`).join('');
  const box = $('#lib'); box.innerHTML = '';
  if (!S.instances.length) { box.innerHTML = '<div class="empty"><b>No instances yet</b>Press + New instance to make one.</div>'; return; }
  groups.forEach(g => {
    const items = list.filter(i => (i.group || 'My instances') === g); if (!items.length) return;
    const sec = document.createElement('div'); sec.style.marginBottom = '22px';
    sec.innerHTML = `<div class="row-h" style="margin-bottom:12px"><h3>${esc(g)}</h3><span class="mono dim">${items.length}</span></div>`;
    if (libView === 'grid') { const c = document.createElement('div'); c.className = 'cards'; items.forEach(i => c.appendChild(card(i))); sec.appendChild(c); }
    else {
      const l = document.createElement('div'); l.className = 'listview';
      items.forEach(i => {
        const r = document.createElement('button'); r.className = 'lrow';
        r.append(iconEl(i)); r.insertAdjacentHTML('beforeend', `<span style="min-width:0"><b>${esc(i.name)}</b><div class="hint">${esc(i.mcVersion)} · ${i.loader === 'fabric' ? 'Fabric' : 'Vanilla'}${i.treasureClient ? ' · Treasure Client' : ''}</div><div class="bar" data-prog="${esc(i.id)}" hidden><i></i></div></span><span class="hint hide-s">${fmtTime(i.playSeconds)}</span><span class="hint hide-s">${esc(ago(i.lastPlayed))}</span><span class="mono dim">${S.running.has(i.id) ? 'Playing' : ''}</span>`);
        r.onclick = () => openInstance(i.id); l.appendChild(r);
      });
      sec.appendChild(l);
    }
    box.appendChild(sec);
  });
  if (!box.children.length) box.innerHTML = `<p class="hint">No instances match "${esc(q)}".</p>`;
  queueProgress();
}
$('#lib-q').oninput = renderLibrary; $('#lib-sort').onchange = renderLibrary;
$$('[data-lv]').forEach(b => (b.onclick = () => { libView = b.dataset.lv; $$('[data-lv]').forEach(x => x.setAttribute('aria-pressed', String(x === b))); renderLibrary(); }));

// ---------------------------------------------------------------- instance page
let ckind = 'mod', contentList = [], updatesList = [];
function renderInstHead() {
  const i = inst(S.current); if (!i) return;
  const ic = $('#i-icon'); ic.innerHTML = ''; ic.append(iconEl(i));
  $('#i-name').textContent = i.name; $('#i-meta').innerHTML = metaHTML(i);
  $('#crumb').innerHTML = `Library › <b>${esc(i.name)}</b>`;
  $('#i-stats').innerHTML = [['Played', fmtTime(i.playSeconds)], ['Last played', ago(i.lastPlayed)], ['Mods', '…', 'st-mods'], ['Size', '…', 'st-size']].map(([k, v, id]) => `<div class="stat"><span class="mono dim">${k}</span><b${id ? ` id="${id}"` : ''}>${esc(v)}</b></div>`).join('');
  invoke('instance_size', { id: i.id }).then(b => { const e = $('#st-size'); if (e) e.textContent = mb(b); }).catch(() => {});
  invoke('content', { id: i.id, kind: 'mod' }).then(l => { const e = $('#st-mods'); if (e) e.textContent = l.filter(x => x.enabled).length; }).catch(() => {});
  landscape($('#inst-art'), hash(i.id) + 40);
  queueProgress(); renderPlayButtons();
}
function openInstance(id) {
  S.current = id; show('inst'); renderInstHead(); fillInstSettings(); selectTab('content'); loadContent(true);
  $('#log').innerHTML = (S.logs[id] || []).map(logLine).join('');
}
function selectTab(p) {
  $$('#i-tabs .tab').forEach(t => t.setAttribute('aria-selected', String(t.dataset.pane === p)));
  $$('.pane').forEach(x => (x.hidden = x.id !== 'p-' + p));
  if (p === 'worlds') loadWorlds(); if (p === 'shots') loadShots(); if (p === 'logs') loadLog();
}
$$('#i-tabs .tab').forEach(t => (t.onclick = () => selectTab(t.dataset.pane)));
$$('#ckind button').forEach(b => (b.onclick = () => { ckind = b.dataset.k; $$('#ckind button').forEach(x => x.setAttribute('aria-pressed', String(x === b))); loadContent(true); }));
$('#mod-q').oninput = () => renderContent();
$('#add-content').onclick = () => { setKind(ckind); show('browse'); $('#target').value = S.current; runSearch(); };

async function loadContent(checkUpdates) {
  const id = S.current; $('#mods').innerHTML = '<div class="loading">Reading the folder</div>';
  try { contentList = await invoke('content', { id, kind: ckind }); } catch (e) { contentList = []; fail(e); }
  if (id !== S.current) return;
  renderContent();
  if (checkUpdates) {
    const u = $('#upd-all'); u.disabled = true; u.textContent = 'Checking for updates…'; $('#n-upd').textContent = '';
    try { updatesList = await invoke('check_updates', { id, kind: ckind }); } catch { updatesList = []; }
    if (id !== S.current) return;
    u.disabled = !updatesList.length; u.classList.toggle('done', !updatesList.length);
    u.textContent = updatesList.length ? `Update all (${updatesList.length})` : 'All up to date';
    $('#n-upd').textContent = updatesList.length ? `${updatesList.length} update${updatesList.length > 1 ? 's' : ''}` : '';
    renderContent();
  }
}
function renderContent() {
  const q = $('#mod-q').value.trim().toLowerCase(), box = $('#mods'); box.innerHTML = '';
  const rows = contentList.filter(m => !q || (m.name + ' ' + m.file).toLowerCase().includes(q));
  if (!contentList.length) {
    box.innerHTML = `<div class="empty"><b>Nothing here yet</b>${isTreasurePack(inst(S.current) || {}) && !(inst(S.current).pack || {}).installed ? 'The Treasure Pack downloads the first time you press Play.' : 'Press + Add content to find some on Modrinth.'}</div>`;
  }
  rows.forEach(m => {
    const upd = updatesList.find(u => u.file === m.file);
    const r = document.createElement('div'); r.className = 'mrow' + (m.enabled ? '' : ' off');
    const svg = document.createElementNS('http://www.w3.org/2000/svg', 'svg'); svg.setAttribute('class', 'ic');
    m.file.startsWith('treasure-client') ? chest(svg) : pixelIcon(svg, hash(m.id || m.file), 'var(--a2)', '#1f150a');
    r.append(svg);
    r.insertAdjacentHTML('beforeend', `<div style="min-width:0"><div class="t">${esc(m.name)} <span class="hint">${esc(m.version)}</span></div><div class="d">${esc(m.description || m.file)}</div></div>${upd ? `<button class="upd">Update to ${esc(upd.versionNumber)}</button>` : `<button class="chip" title="Remove" aria-label="Remove ${esc(m.name)}" style="padding:4px 8px">✕</button>`}<button class="switch" role="switch" aria-checked="${m.enabled}" aria-label="${esc(m.name)}"></button>`);
    const u = r.querySelector('.upd');
    if (u) u.onclick = async () => { u.disabled = true; u.textContent = 'Updating…'; try { await invoke('apply_updates', { id: S.current, kind: ckind, updates: [upd] }); toast(`${m.name} updated`); loadContent(true); } catch (e) { fail(e); u.disabled = false; } };
    const x = r.querySelector('.chip');
    if (x) x.onclick = async () => { if (await ask(`Remove ${m.name}?`, 'It\'s deleted from this instance.', 'Remove')) { try { await invoke('remove_content', { id: S.current, kind: ckind, file: m.file }); loadContent(false); } catch (e) { fail(e); } } };
    r.querySelector('.switch').onclick = async () => { try { await invoke('set_content', { id: S.current, kind: ckind, file: m.file, on: !m.enabled }); loadContent(false); } catch (e) { fail(e); } };
    box.appendChild(r);
  });
  // a common mistake: shaders on but Sodium off
  const on = n => contentList.some(m => m.enabled && m.id === n);
  const warn = ckind === 'mod' && on('iris') && contentList.some(m => m.id === 'sodium') && !on('sodium');
  $('#i-warn').hidden = !warn; $('#i-warn-t').textContent = 'Iris needs Sodium, which is off. Turn Sodium back on or the game won\'t start.';
}
$('#upd-all').onclick = async () => {
  const u = $('#upd-all'); u.disabled = true; u.textContent = 'Updating…';
  try { const n = await invoke('apply_updates', { id: S.current, kind: ckind, updates: updatesList }); toast(`${n} updated`); } catch (e) { fail(e); }
  loadContent(true);
};
async function loadWorlds() {
  const box = $('#worlds'); box.innerHTML = '<div class="loading">Looking for worlds</div>';
  let w = []; try { w = await invoke('worlds', { id: S.current }); } catch { /* none */ }
  box.innerHTML = w.length ? '' : '<div class="empty"><b>No worlds yet</b>Worlds you make in this instance show up here.</div>';
  w.forEach(x => {
    const b = document.createElement('button'); b.className = 'jump';
    const svg = document.createElementNS('http://www.w3.org/2000/svg', 'svg'); svg.setAttribute('class', 'ic'); pixelIcon(svg, hash(x.folder));
    b.append(svg); b.insertAdjacentHTML('beforeend', `<span style="min-width:0"><b>${esc(x.folder)}</b><span class="hint">Last played ${esc(ago(x.lastPlayed).toLowerCase())}</span></span><span class="go" title="Play this world"><svg width="14" height="14" viewBox="0 0 24 24" fill="currentColor"><path d="M7 4v16l13-8z"/></svg></span>`);
    b.onclick = () => play(S.current, { world: x.folder });
    box.appendChild(b);
  });
}
async function loadShots() {
  const box = $('#shots'); box.innerHTML = '<div class="loading">Loading screenshots</div>';
  let files = []; try { files = await invoke('screenshots', { id: S.current }); } catch { /* none */ }
  box.innerHTML = files.length ? '' : '<div class="empty" style="grid-column:1/-1"><b>No screenshots yet</b>Press F2 in the game to take one.</div>';
  const id = S.current;
  for (const f of files.slice(0, 24)) {
    const img = document.createElement('img'); img.alt = f; img.title = f; img.style.cssText = 'width:100%;aspect-ratio:16/9;object-fit:cover;border-radius:10px;cursor:pointer;background:var(--bg1)';
    img.onclick = () => invoke('open_folder', { id, sub: 'screenshots' }).catch(fail);
    box.appendChild(img);
    invoke('screenshot', { id, file: f }).then(src => (img.src = src)).catch(() => {});
  }
}
const logLine = l => `<div class="${/ERROR|Exception|FATAL/.test(l) ? 'e' : /WARN/.test(l) ? 'w' : ''}">${esc(l)}</div>`;
async function loadLog() {
  const id = S.current;
  if (S.logs[id] && S.logs[id].length) { $('#log').innerHTML = S.logs[id].map(logLine).join(''); }
  else { try { const l = await invoke('latest_log', { id }); $('#log').innerHTML = l.length ? l.map(logLine).join('') : '<span class="hint">No log yet. Play once and it shows here.</span>'; } catch { $('#log').textContent = ''; } }
  $('#log').scrollTop = 1e9;
}
$('#copy-log').onclick = async () => { try { await navigator.clipboard.writeText($('#log').innerText); toast('Log copied'); } catch { const r = document.createRange(); r.selectNodeContents($('#log')); getSelection().removeAllRanges(); getSelection().addRange(r); toast('Log selected. Press Ctrl+C to copy.'); } };
$('#log-folder').onclick = () => invoke('open_folder', { id: S.current, sub: 'logs' }).catch(fail);
$('#i-folder').onclick = () => invoke('open_folder', { id: S.current, sub: null }).catch(fail);

// instance settings
function fillInstSettings() {
  const i = inst(S.current); if (!i) return;
  $('#s-name').value = i.name; $('#s-group').value = i.group || '';
  const mem = i.memoryMb || S.settings.memoryMb || 4096; $('#s-mem').value = mem; $('#s-mem-v').textContent = (mem / 1024).toFixed(1) + ' GB';
  $('#s-mem-d').textContent = i.memoryMb ? 'RAM this instance gets.' : 'Uses the launcher setting until you change it here.';
  $('#s-tc-row').hidden = !(i.loader === 'fabric' && S.supported.includes(i.mcVersion));
  $('#s-tc').setAttribute('aria-checked', String(!!i.treasureClient)); $('#s-pin').setAttribute('aria-checked', String(!!i.pinned));
  $('#s-w').value = i.width || ''; $('#s-h').value = i.height || ''; $('#s-java').value = i.javaPath || ''; $('#s-args').value = i.jvmArgs || '';
}
let saveTimer;
function saveInst(changes, now) {
  const i = inst(S.current); if (!i) return;
  Object.assign(i, changes);
  clearTimeout(saveTimer);
  saveTimer = setTimeout(async () => {
    try { const saved = await invoke('save_instance', { inst: i }); Object.assign(i, saved); renderInstHead(); renderSide(); } catch (e) { fail(e); refresh(); }
  }, now ? 0 : 500);
}
$('#s-name').oninput = e => saveInst({ name: e.target.value });
$('#s-group').oninput = e => saveInst({ group: e.target.value });
$('#s-mem').oninput = e => { $('#s-mem-v').textContent = (e.target.value / 1024).toFixed(1) + ' GB'; saveInst({ memoryMb: +e.target.value }); };
$('#s-tc').onclick = e => { const on = e.currentTarget.getAttribute('aria-checked') !== 'true'; e.currentTarget.setAttribute('aria-checked', String(on)); saveInst({ treasureClient: on }, true); toast(on ? 'Treasure Client gets added the next time you play.' : 'Treasure Client taken out.'); setTimeout(() => loadContent(false), 600); };
$('#s-pin').onclick = e => { const on = e.currentTarget.getAttribute('aria-checked') !== 'true'; e.currentTarget.setAttribute('aria-checked', String(on)); saveInst({ pinned: on }, true); };
const intOrNull = v => (/^\d+$/.test(v.trim()) ? +v.trim() : null);
$('#s-w').oninput = e => saveInst({ width: intOrNull(e.target.value) });
$('#s-h').oninput = e => saveInst({ height: intOrNull(e.target.value) });
$('#s-java').oninput = e => saveInst({ javaPath: e.target.value.trim() || null });
$('#s-args').oninput = e => saveInst({ jvmArgs: e.target.value.trim() || null });
$('#s-repair').onclick = async () => { S.jobs[S.current] = { stage: 'Checking every file', done: 0, total: 0 }; queueProgress(); try { await invoke('repair', { id: S.current }); } catch (e) { delete S.jobs[S.current]; queueProgress(); fail(e); } };
$('#s-dup').onclick = async () => { try { const c = await invoke('duplicate_instance', { id: S.current }); await refresh(); openInstance(c.id); toast('Copy made'); } catch (e) { fail(e); } };
$('#s-del').onclick = async () => {
  const i = inst(S.current);
  if (!(await ask(`Delete ${i.name}?`, 'Its mods, settings and worlds are deleted for good.', 'Delete'))) return;
  try { await invoke('delete_instance', { id: i.id }); S.current = null; await refresh(); show('library'); toast(`${i.name} deleted`); } catch (e) { fail(e); }
};

// ---------------------------------------------------------------- browse
const CATS = { modpack: ['optimization', 'adventure', 'technology', 'multiplayer', 'lightweight', 'quests'], mod: ['optimization', 'utility', 'decoration', 'adventure', 'technology', 'storage'], resourcepack: ['16x', '32x', 'simplistic', 'realistic', 'themed', 'gui'], shader: ['vanilla-like', 'realistic', 'cartoon', 'fantasy', 'cursed', 'semi-realistic'] };
const KIND_WORD = { modpack: 'modpacks', mod: 'mods', resourcepack: 'resource packs', shader: 'shaders' };
let kind = 'modpack', offset = 0, searchTimer, searchSeq = 0;
function setKind(k) {
  kind = k; offset = 0;
  $$('#kinds button').forEach(x => x.setAttribute('aria-pressed', String(x.dataset.kind === k)));
  $('#q').placeholder = 'Search ' + KIND_WORD[k];
  $('#target-wrap').hidden = k === 'modpack'; $('#ver-grp').hidden = k !== 'modpack';
  $('#cats').innerHTML = '<span class="mono dim">Category</span>' + CATS[k].map(c => `<label class="check"><input type="checkbox" value="${c}"> ${c[0].toUpperCase() + c.slice(1).replace('-', ' ')}</label>`).join('');
  $$('#cats input').forEach(x => (x.onchange = () => runSearch()));
}
function renderTargets() {
  const s = $('#target'), v = s.value;
  s.innerHTML = S.instances.map(i => `<option value="${esc(i.id)}">${esc(i.name)} (${esc(i.mcVersion)})</option>`).join('');
  if (v && inst(v)) s.value = v; else if (S.current) s.value = S.current;
}
async function runSearch(more) {
  if (!more) offset = 0;
  const seq = ++searchSeq, box = $('#results');
  if (!more) box.innerHTML = '<div class="loading">Searching Modrinth</div>';
  const target = inst($('#target').value);
  if (kind !== 'modpack' && !target) { box.innerHTML = '<div class="empty"><b>Make an instance first</b>Mods, resource packs and shaders go into an instance.</div>'; return; }
  const q = {
    text: $('#q').value.trim(), kind, sort: $('#b-sort').value, offset, limit: 20,
    mc: kind === 'modpack' ? ($('#b-ver').value || null) : target.mcVersion,
    loader: kind === 'mod' ? (target.loader === 'fabric' ? 'fabric' : null) : kind === 'modpack' ? 'fabric' : null,
    categories: $$('#cats input:checked').map(x => x.value),
  };
  try {
    const r = await invoke('search', { q });
    if (seq !== searchSeq) return;
    if (!more) box.innerHTML = '';
    box.querySelector('.morebtn')?.remove();
    r.hits.forEach(h => box.appendChild(resultRow(h)));
    if (!box.children.length) box.innerHTML = `<div class="empty"><b>Nothing found</b>Try other words${kind === 'mod' && target.loader !== 'fabric' ? ', or a Fabric instance (vanilla instances can\'t use mods)' : ''}.</div>`;
    offset += r.hits.length;
    if (offset < r.total_hits) { const b = document.createElement('button'); b.className = 'chip morebtn'; b.textContent = 'Show more'; b.onclick = () => runSearch(true); box.appendChild(b); }
  } catch (e) { if (seq === searchSeq) box.innerHTML = `<div class="empty"><b>Modrinth can't be reached</b>${esc(errText(e))}</div>`; }
}
function resultRow(h) {
  const d = document.createElement('div'); d.className = 'result';
  d.append(hitIcon(h));
  const tags = (h.display_categories || h.categories || []).filter(c => !['fabric', 'forge', 'neoforge', 'quilt', 'minecraft', 'iris', 'optifine'].includes(c)).slice(0, 3);
  d.insertAdjacentHTML('beforeend', `<div style="min-width:0"><b style="font-size:16px">${esc(h.title)}</b> <span class="hint">by ${esc(h.author)}</span><div class="hint">${esc(h.description)}</div><div class="tags">${tags.map(t => `<span class="tag">${esc(t)}</span>`).join('')}<span class="hint">${num(h.downloads)} downloads</span></div></div><div style="display:flex;flex-direction:column;gap:6px;align-items:stretch"><button class="add">${kind === 'modpack' ? 'Install' : 'Add'}</button><button class="chip" style="justify-content:center">Page</button></div>`);
  const [b, page] = d.querySelectorAll('button');
  page.onclick = () => invoke('open_url', { url: `https://modrinth.com/project/${encodeURIComponent(h.slug || h.project_id)}` }).catch(fail);
  b.onclick = async () => {
    if (kind === 'modpack') return installPack(h, b);
    const id = $('#target').value; b.disabled = true; b.textContent = 'Adding…';
    try { const added = await invoke('add_content', { id, project: h.project_id, kind }); b.classList.add('done'); b.textContent = 'Added ✓'; toast(added.length > 1 ? `${h.title} added, with ${added.length - 1} mod${added.length > 2 ? 's' : ''} it needs` : `${h.title} added to ${inst(id)?.name}`); }
    catch (e) { b.disabled = false; b.textContent = 'Add'; fail(e); }
  };
  return d;
}
async function installPack(h, b) {
  if (b) { b.disabled = true; b.textContent = 'Starting…'; }
  try {
    const i = await invoke('install_modpack', { project: h.project_id, title: h.title, iconUrl: h.icon_url || null });
    S.jobs[i.id] = { stage: 'Starting', done: 0, total: 0 };
    await refresh();
    if (b) { b.classList.add('done'); b.textContent = 'Installing…'; }
    toast(`Installing ${h.title}. It's in your Library.`);
  } catch (e) { if (b) { b.disabled = false; b.textContent = 'Install'; } fail(e); }
}
$$('#kinds button').forEach(b => (b.onclick = () => { setKind(b.dataset.kind); runSearch(); }));
$('#q').oninput = () => { clearTimeout(searchTimer); searchTimer = setTimeout(() => runSearch(), 300); };
$('#b-sort').onchange = () => runSearch(); $('#b-ver').onchange = () => runSearch(); $('#target').onchange = () => runSearch();

// ---------------------------------------------------------------- new instance
let choice = 'tc';
async function loadVersions() {
  if (S.versions.length) return;
  try { S.versions = (await invoke('game_versions', { snapshots: false })).map(v => v.id); } catch { S.versions = [...S.supported]; }
  $('#b-ver').innerHTML = '<option value="">Any version</option>' + S.versions.slice(0, 40).map(v => `<option>${esc(v)}</option>`).join('');
}
async function openNew() { $('#scrim').hidden = false; await loadVersions(); updateNew(); $('#ni-name').focus(); }
function closeNew() { $('#scrim').hidden = true; }
$('#scrim').onclick = e => { if (e.target.id === 'scrim') closeNew(); };
$$('.choice[data-choice]').forEach(c => (c.onclick = () => { choice = c.dataset.choice; $$('.choice[data-choice]').forEach(x => x.setAttribute('aria-pressed', String(x === c))); updateNew(); }));
$('#ni-ver').onchange = () => updateNew(true);
$('#ni-loader').onchange = () => updateNew(true);
function updateNew(keepVersions) {
  const vs = choice === 'tc' ? S.supported : S.versions, sel = $('#ni-ver'), cur = sel.value;
  if (!keepVersions) { sel.innerHTML = vs.map(v => `<option>${esc(v)}</option>`).join(''); if (vs.includes(cur)) sel.value = cur; }
  $('#ni-loader-f').hidden = choice !== 'plain'; $('#ni-form').hidden = choice === 'pack'; $('#ni-pack').hidden = choice !== 'pack'; $('#create').hidden = choice === 'pack';
  const h = $('#ni-hint'); h.classList.remove('err');
  h.textContent = choice === 'tc' ? 'Adds Treasure Client and Fabric API. Nothing else. Treasure Client is made for 1.21.11 and 26.1 to 26.3.' : $('#ni-loader').value === 'fabric' ? 'Plain Fabric. Add mods from Browse whenever you like.' : 'Plain Minecraft, no mods.';
}
$('#create').onclick = async () => {
  const b = $('#create'); b.disabled = true;
  try {
    const i = await invoke('create_instance', { name: $('#ni-name').value, mc: $('#ni-ver').value, loader: choice === 'tc' ? 'fabric' : $('#ni-loader').value, tc: choice === 'tc', group: $('#ni-group').value.trim() || 'My instances' });
    closeNew(); await refresh(); openInstance(i.id); toast(`${i.name} made. Press Play to download it.`);
  } catch (e) { fail(e); }
  b.disabled = false;
};
$('#ni-browse').onclick = () => { closeNew(); setKind('modpack'); show('browse'); runSearch(); };
$('#ni-file').onclick = () => $('#ni-file-in').click();
$('#ni-file-in').onchange = e => {
  const f = e.target.files[0]; if (!f) return;
  const r = new FileReader();
  r.onload = async () => { try { const i = await invoke('import_mrpack_data', { data: String(r.result) }); S.jobs[i.id] = { stage: 'Starting', done: 0, total: 0 }; closeNew(); await refresh(); toast(`Installing ${i.name}. It's in your Library.`); } catch (er) { fail(er); } e.target.value = ''; };
  r.readAsDataURL(f);
};

// drop a .mrpack on the window
listen('tauri://drag-enter', () => ($('#drop').hidden = false));
listen('tauri://drag-leave', () => ($('#drop').hidden = true));
listen('tauri://drag-drop', async p => {
  $('#drop').hidden = true;
  for (const path of (p.paths || []).filter(x => x.toLowerCase().endsWith('.mrpack'))) {
    try { const i = await invoke('import_mrpack', { path }); S.jobs[i.id] = { stage: 'Starting', done: 0, total: 0 }; await refresh(); toast(`Installing ${i.name}. It's in your Library.`); } catch (e) { fail(e); }
  }
});

// ---------------------------------------------------------------- ask (our own yes/no box)
function ask(title, text, yes = 'OK') {
  return new Promise(res => {
    $('#ask-title').textContent = title; $('#ask-text').textContent = text; $('#ask-yes').textContent = yes; $('#ask').hidden = false; $('#ask-yes').focus();
    const done = v => { $('#ask').hidden = true; $('#ask-yes').onclick = $('#ask-no').onclick = null; res(v); };
    $('#ask-yes').onclick = () => done(true); $('#ask-no').onclick = () => done(false);
  });
}

// ---------------------------------------------------------------- accounts
function place(pop, btn, up) {
  const r = btn.getBoundingClientRect();
  if (up && innerWidth > 720) { pop.style.left = Math.min(innerWidth - pop.offsetWidth - 12, r.left) + 'px'; pop.style.top = Math.max(12, r.top - pop.offsetHeight - 8) + 'px'; }
  else { pop.style.top = (r.bottom + 8) + 'px'; pop.style.left = Math.max(12, Math.min(innerWidth - pop.offsetWidth - 12, r.right - pop.offsetWidth)) + 'px'; }
}
function togglePop(sel, btn, up) { const p = $(sel), open = p.hidden; $$('.pop').forEach(x => (x.hidden = true)); if (open) { p.hidden = false; place(p, btn, up); } }
function renderAccounts() {
  const box = $('#acct-list'); box.innerHTML = S.accounts.length ? '' : '<p class="hint" style="margin:0">No accounts yet.</p>';
  S.accounts.forEach(a => {
    const row = document.createElement('div'); row.className = 'pin'; row.style.cursor = 'default';
    row.innerHTML = `<svg viewBox="0 0 8 8" shape-rendering="crispEdges"></svg><span style="flex:1">${esc(a.name)}<i>${a.active ? 'Playing as this account' : 'Microsoft account'}</i></span>${a.active ? '<span class="tag ok">Active</span>' : '<button class="chip use">Use</button>'}<button class="chip out" title="Sign out">✕</button>`;
    face(row.querySelector('svg'), hash(a.uuid));
    row.querySelector('.use')?.addEventListener('click', async () => { try { await invoke('set_account', { uuid: a.uuid }); S.accounts = await invoke('accounts'); renderAccounts(); renderSide(); toast('Switched to ' + a.name); } catch (e) { fail(e); } });
    row.querySelector('.out').onclick = async () => { if (await ask(`Sign out ${a.name}?`, 'You can sign in again any time.', 'Sign out')) { try { await invoke('sign_out', { uuid: a.uuid }); S.accounts = await invoke('accounts'); renderAccounts(); renderSide(); } catch (e) { fail(e); } } };
    box.appendChild(row);
  });
}
function openAccounts() { renderAccounts(); togglePop('#acct-pop', $('#acct-btn'), true); }
$('#acct-btn').onclick = e => { e.stopPropagation(); openAccounts(); };
let pollTimer;
$('#acct-add').onclick = async () => {
  $$('.pop').forEach(p => (p.hidden = true));
  const body = $('#si-body'); $('#signin').hidden = false;
  if (!S.signinAvailable) { body.innerHTML = '<p class="hint">Microsoft sign-in turns on once Mojang approves Treasure Launcher (we\'ve asked). Until then, play through the official Minecraft launcher.</p>'; return; }
  body.innerHTML = '<div class="loading">Asking Microsoft for a code</div>';
  try {
    const dc = await invoke('signin_start');
    body.innerHTML = `<p class="hint">1. Open <b>${esc(dc.verificationUri)}</b> in your browser.<br>2. Type this code and sign in. The launcher never sees your password.</p><div class="code">${esc(dc.userCode)}</div><div class="foot" style="justify-content:center;margin-top:14px"><button class="chip" id="si-copy">Copy code</button><button class="add" id="si-open">Open the page</button></div><p class="loading" style="text-align:center">Waiting for you to finish in the browser</p>`;
    $('#si-copy').onclick = () => navigator.clipboard.writeText(dc.userCode).then(() => toast('Code copied')).catch(() => {});
    $('#si-open').onclick = () => invoke('open_url', { url: dc.verificationUri }).catch(fail);
    const tick = async () => {
      if ($('#signin').hidden) return;
      try {
        const a = await invoke('signin_poll');
        if (a) { S.accounts = await invoke('accounts'); renderSide(); $('#signin').hidden = true; toast(`Signed in as ${a.name}`); return; }
        pollTimer = setTimeout(tick, dc.interval * 1000);
      } catch (e) { body.innerHTML = `<p class="hint err" style="color:var(--err)">${esc(errText(e))}</p><div class="foot"><button class="add" id="si-again">Try again</button></div>`; $('#si-again').onclick = () => $('#acct-add').click(); }
    };
    pollTimer = setTimeout(tick, dc.interval * 1000);
  } catch (e) { body.innerHTML = `<p class="hint" style="color:var(--err)">${esc(errText(e))}</p>`; }
};
$('#si-close').onclick = () => { $('#signin').hidden = true; clearTimeout(pollTimer); invoke('signin_cancel').catch(() => {}); };

// ---------------------------------------------------------------- downloads + alerts
$('#dl-btn').onclick = e => { e.stopPropagation(); togglePop('#dl-pop', e.currentTarget); };
$('#bell').onclick = e => { e.stopPropagation(); S.unread = 0; renderBell(); togglePop('#bell-pop', e.currentTarget); };
function renderBell() {
  $('#bell-badge').hidden = !S.unread; $('#bell-badge').textContent = S.unread;
  $('#bell-list').innerHTML = S.notices.length ? S.notices.map(n => `<div class="notice${n.err ? ' err' : ''}"><b>${esc(n.title)}</b><div class="hint">${esc(n.text)}</div></div>`).join('') : '<p class="hint">Nothing new.</p>';
}

// ---------------------------------------------------------------- events from the launcher
listen('progress', p => { S.jobs[p.id] = { stage: p.stage, done: p.done, total: p.total }; queueProgress(); });
listen('job-done', p => { delete S.jobs[p.id]; queueProgress(); const i = inst(p.id); refresh().then(() => { if (view === 'inst' && p.id === S.current) { renderInstHead(); loadContent(true); } }); if (i) toast(`${i.name} is ready`); });
listen('job-error', p => {
  delete S.jobs[p.id]; queueProgress();
  const name = inst(p.id)?.name || 'Something';
  toast(`${name}: ${p.error}`, true); notice(`${name} didn't work`, p.error, true);
  refresh();
});
listen('game-started', p => { delete S.jobs[p.id]; S.running.add(p.id); S.started[p.id] = Date.now(); S.logs[p.id] = []; queueProgress(); renderSide(); refresh(); });
listen('game-log', p => {
  const l = (S.logs[p.id] = S.logs[p.id] || []); l.push(p.line); if (l.length > 3000) l.splice(0, l.length - 3000);
  if (view === 'inst' && p.id === S.current && !$('#p-logs').hidden) { const log = $('#log'); const end = log.scrollTop + log.clientHeight >= log.scrollHeight - 30; log.insertAdjacentHTML('beforeend', logLine(p.line)); if (end) log.scrollTop = 1e9; }
});
listen('game-exited', p => {
  S.running.delete(p.id); delete S.started[p.id]; queueProgress(); refresh();
  if (p.code && p.seconds < 60) { const name = inst(p.id)?.name || 'Minecraft'; toast(`${name} closed with an error. The Logs tab shows why.`, true); notice(`${name} crashed`, 'Open the instance and look at its Logs tab.', true); }
});

// ---------------------------------------------------------------- Ctrl+K
function palItems(q) {
  const out = [];
  S.instances.forEach(i => { out.push([i.name, `Open · ${i.mcVersion}`, () => openInstance(i.id), 'Open']); out.push([`Play ${i.name}`, 'Start the game', () => { S.current = i.id; play(i.id); }, 'Play']); });
  [['Home', 'Page', () => show('home')], ['Library', 'Page', () => show('library')], ['Browse modpacks', 'Modrinth', () => { setKind('modpack'); show('browse'); runSearch(); }], ['Browse mods', 'Modrinth', () => { setKind('mod'); show('browse'); runSearch(); }], ['New instance', 'Action', openNew], ['Settings', 'Page', () => show('settings')], ['Change theme', 'Settings', () => show('settings')], ['Accounts', 'Sign in or switch', openAccounts]].forEach(x => out.push([...x, 'Go']));
  q = q.trim().toLowerCase(); return out.filter(x => !q || (x[0] + ' ' + x[1]).toLowerCase().includes(q)).slice(0, 10);
}
let palSel = 0, palList = [];
function renderPal() {
  palList = palItems($('#pal-q').value); palSel = Math.min(palSel, Math.max(0, palList.length - 1));
  $('#pal-items').innerHTML = palList.length ? '' : '<p class="hint" style="padding:10px 12px">No matches</p>';
  palList.forEach((x, n) => { const b = document.createElement('button'); b.className = n === palSel ? 'sel' : ''; b.innerHTML = `<span style="min-width:0"><b>${esc(x[0])}</b><div class="hint">${esc(x[1])}</div></span><kbd class="k">${x[3]}</kbd>`; b.onclick = () => { closePal(); x[2](); }; $('#pal-items').appendChild(b); });
}
function openPal() { $('#pal-scrim').hidden = false; $('#pal-q').value = ''; palSel = 0; renderPal(); $('#pal-q').focus(); }
function closePal() { $('#pal-scrim').hidden = true; }
$('#kbar').onclick = openPal; $('#pal-q').oninput = () => { palSel = 0; renderPal(); };
$('#pal-scrim').onclick = e => { if (e.target.id === 'pal-scrim') closePal(); };
document.addEventListener('keydown', e => {
  if ((e.ctrlKey || e.metaKey) && e.key.toLowerCase() === 'k') { e.preventDefault(); $('#pal-scrim').hidden ? openPal() : closePal(); }
  if (e.key === 'Escape') { closePal(); closeNew(); $$('.pop').forEach(p => (p.hidden = true)); $('#pick-menu').hidden = true; if (!$('#ask').hidden) $('#ask-no').click(); }
  if (!$('#pal-scrim').hidden) {
    if (e.key === 'ArrowDown') { palSel = Math.min(palList.length - 1, palSel + 1); renderPal(); e.preventDefault(); }
    if (e.key === 'ArrowUp') { palSel = Math.max(0, palSel - 1); renderPal(); e.preventDefault(); }
    if (e.key === 'Enter' && palList[palSel]) { const x = palList[palSel]; closePal(); x[2](); }
  }
});

// ---------------------------------------------------------------- settings
const THEMES = [['Treasure', '#FFF3C4', '#FFD76B', '#D4A537', '#8A5A20', '#F6ECD2', '#C9B183', '#0F0A05'], ['Frost', '#EAF7FF', '#BFE6FF', '#6FC3EC', '#2F6F9C', '#EEF7FF', '#A9CADD', '#07131F'], ['Tropical', '#D9FFF6', '#45E3C6', '#1FB6BA', '#0E6D78', '#F3ECD6', '#D8C38F', '#05171A'], ['Terracotta', '#FFCF9E', '#E8863A', '#C05F26', '#7A3D1D', '#F6E6D4', '#D8AD84', '#1A0E06'], ['Forest', '#D6F8C2', '#6FCB5B', '#3D9A3F', '#215A27', '#EAF6DD', '#AECB96', '#07160A'], ['Sakura', '#FFE3EF', '#FF9CC6', '#E8679F', '#9C3A66', '#FBEAF1', '#DDB0C4', '#1A0A12'], ['Dark', '#F0EEFC', '#B9B4E8', '#8580C4', '#4A4678', '#ECEAF4', '#A9A6BD', '#08080C']];
function applyTheme(name) {
  const t = THEMES.find(x => x[0] === name) || THEMES[0];
  ['--hi', '--a1', '--a2', '--a3', '--txt', '--dim', '--bg0'].forEach((k, i) => document.documentElement.style.setProperty(k, t[i + 1]));
  $$('.theme').forEach(x => x.setAttribute('aria-pressed', String(x.dataset.t === t[0])));
}
THEMES.forEach(t => {
  const b = document.createElement('button'); b.className = 'theme'; b.dataset.t = t[0];
  b.innerHTML = `<div class="sw">${[t[2], t[3], t[4], t[7]].map(c => `<i style="background:${c}"></i>`).join('')}</div><b>${t[0]}</b>`;
  b.onclick = () => { S.settings.theme = t[0]; applyTheme(t[0]); saveSettings(); if (view === 'home') renderHome(); };
  $('#themes').appendChild(b);
});
let setTimer;
function saveSettings() { clearTimeout(setTimer); setTimer = setTimeout(() => invoke('set_settings', { s: S.settings }).catch(fail), 400); }
function fillSettings() {
  const s = S.settings;
  applyTheme(s.theme); setSlim(s.sidebarLabels === false);
  $('#ram').value = s.memoryMb; $('#ram-v').textContent = (s.memoryMb / 1024).toFixed(1) + ' GB';
  $('#set-while').value = s.whilePlaying || 'hide';
  $('#set-dl').value = s.downloadsAtOnce || 8; $('#set-dl-v').textContent = s.downloadsAtOnce || 8;
  $('#set-java').value = s.javaPath || '';
  $('#rm').setAttribute('aria-checked', String(!!s.reduceMotion)); document.body.classList.toggle('still', !!s.reduceMotion);
}
$('#ram').oninput = e => { S.settings.memoryMb = +e.target.value; $('#ram-v').textContent = (e.target.value / 1024).toFixed(1) + ' GB'; saveSettings(); };
$('#set-while').onchange = e => { S.settings.whilePlaying = e.target.value; saveSettings(); };
$('#set-dl').oninput = e => { S.settings.downloadsAtOnce = +e.target.value; $('#set-dl-v').textContent = e.target.value; saveSettings(); };
$('#set-java').oninput = e => { S.settings.javaPath = e.target.value.trim() || null; saveSettings(); };
$('#rm').onclick = e => { const on = e.currentTarget.getAttribute('aria-checked') !== 'true'; e.currentTarget.setAttribute('aria-checked', String(on)); S.settings.reduceMotion = on; document.body.classList.toggle('still', on); saveSettings(); };
$('#set-labels').onclick = e => { const on = e.currentTarget.getAttribute('aria-checked') !== 'true'; setSlim(!on); S.settings.sidebarLabels = on; saveSettings(); };
$('#open-data').onclick = () => invoke('open_folder', { id: null, sub: null }).catch(fail);
$('#site').onclick = () => invoke('open_url', { url: 'https://treasureclient.github.io' }).catch(fail);
window.addEventListener('resize', () => { if (view === 'home') landscape($('#hero-art'), hash(S.current || '')); });

// ---------------------------------------------------------------- start
(async function start() {
  setKind('modpack');
  if (!T) { toast('This page is the inside of Treasure Launcher. Open the app instead.', true); return; }
  try {
    const d = await invoke('init');
    Object.assign(S, { settings: d.settings, instances: d.instances, accounts: d.accounts, signinAvailable: d.signinAvailable, supported: d.supported, version: d.version });
    S.current = d.instances[0]?.id || null;
    if (d.memoryTotalMb) { const max = Math.max(2048, Math.floor(d.memoryTotalMb * 0.75 / 512) * 512); $('#ram').max = max; $('#s-mem').max = max; $('#ram-d').textContent = `Instances that don't set their own get this much. This computer has ${(d.memoryTotalMb / 1024).toFixed(1)} GB.`; }
    $('#data-dir').textContent = d.dataDir; $('#about').textContent = `Treasure Launcher ${d.version}`;
    fillSettings(); renderSide(); renderPick(); renderTargets(); show('home');
    S.running = new Set(await invoke('running'));
    if (d.firstRun) notice('Welcome to Treasure Launcher', 'Your two Treasure Pack instances are ready. They download the first time you press Play.');
    loadHomeExtras(); loadVersions();
  } catch (e) { fail(e); }
})();
