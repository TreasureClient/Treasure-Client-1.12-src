//! Treasure Client: the `.lag` and `.lag full` chat commands, like the JS and WASM
//! editions' lag monitor. Made so a few screenshots from a slow Chromebook say what is
//! slow: what each frame was spent on, moving and standing still, the last lag spikes and
//! slow seconds with what caused them, how fast the CPU is running (heat), memory, and
//! what grows over a session. Only time in a world counts, and never while Treasure
//! Client caps the frame rate (another window focused, idle, a menu).

use std::collections::VecDeque;
use std::fmt::Write as _;
use std::sync::Mutex;
use std::sync::atomic::{AtomicBool, AtomicU64, Ordering::Relaxed};

use super::budget;

/// Parts of a frame, in the order kept. 1..=11 are inside the game's frame, 12..=16 are
/// drawing, 17..=21 run between frames, 22 is what is left (the browser, or waiting for
/// the screen).
pub(crate) const N: usize = 23;
const NAMES: [&str; N] = [
    "game other",
    "lighting",
    "meshing",
    "HUD/menus",
    "cave culling",
    "world sync",
    "hand",
    "transforms",
    "entities",
    "block ents",
    "particles",
    "mesh sends",
    "GPU upload",
    "views",
    "screen wait",
    "draw prep",
    "GPU submit",
    "azalea",
    "game tick",
    "packets",
    "1.12 translate",
    "meshes in",
    "browser/idle",
];
const INSIDE: std::ops::RangeInclusive<usize> = 1..=11;
const DRAW: std::ops::RangeInclusive<usize> = 12..=16;
const BETWEEN: std::ops::RangeInclusive<usize> = 17..=21;
const BROWSER: usize = 22;

const SPIKE_US: u64 = 50_000;
const KEEP_SPIKES: usize = 12;
const KEEP_SLOW: usize = 8;
const MAX_MINUTES: usize = 240;

static IN_WORLD: AtomicBool = AtomicBool::new(false);
static MOVING: AtomicBool = AtomicBool::new(false);
static CAPPED: AtomicBool = AtomicBool::new(false);
static RENDERED: AtomicBool = AtomicBool::new(true);
static PKTS: AtomicU64 = AtomicU64::new(0);
static BYTES: AtomicU64 = AtomicU64::new(0);
static CHUNKS: AtomicU64 = AtomicU64::new(0);
static MESH_IN: AtomicU64 = AtomicU64::new(0);
static MESH_OUT: AtomicU64 = AtomicU64::new(0);
// the mouse jump filter (renderer/input.rs): jumps dropped and big moves let through, with the biggest
static MOUSE_DROPPED: AtomicU64 = AtomicU64::new(0);
static MOUSE_DROPPED_MAX: AtomicU64 = AtomicU64::new(0);
static MOUSE_KEPT: AtomicU64 = AtomicU64::new(0);
static MOUSE_KEPT_MAX: AtomicU64 = AtomicU64::new(0);

/// A mouse move over 200 pixels: dropped as a made-up jump, or let through.
pub(crate) fn note_mouse_jump(dropped: bool, size: f32) {
    let (n, max) = if dropped { (&MOUSE_DROPPED, &MOUSE_DROPPED_MAX) } else { (&MOUSE_KEPT, &MOUSE_KEPT_MAX) };
    n.fetch_add(1, Relaxed);
    max.fetch_max(size as u64, Relaxed);
}
static VIA_GUESSED: AtomicU64 = AtomicU64::new(0);
static VIA_TUFF: AtomicU64 = AtomicU64::new(0);
static TUFF_PALETTE: AtomicU64 = AtomicU64::new(0);
static TUFF_ON: AtomicBool = AtomicBool::new(false);

/// Settings worth knowing, as the game last reported them.
#[derive(Clone, Default)]
pub(crate) struct Settings {
    pub render_distance: u32,
    pub render_scale: u32,
    pub max_fps: u32,
    pub vsync: bool,
    pub shaders: bool,
    pub smooth_lighting: bool,
    pub fancy_leaves: bool,
    pub particles: String,
    pub clouds: String,
    /// Objects in the game: all, with a position, cameras drawing.
    pub entities: u32,
    pub placed: u32,
    pub cameras: u32,
}

static SETTINGS: Mutex<Option<Settings>> = Mutex::new(None);

// ---- what the game tells us -------------------------------------------------------------

/// Every frame: in a world, moving or turning, and the settings.
pub(crate) fn note_view(in_world: bool, moving: bool, settings: impl FnOnce() -> Settings) {
    IN_WORLD.store(in_world, Relaxed);
    MOVING.store(moving, Relaxed);
    let mut s = SETTINGS.lock().unwrap();
    static AT: AtomicU64 = AtomicU64::new(0);
    let now = budget::now_us();
    if s.is_none() || now.saturating_sub(AT.load(Relaxed)) > 2_000_000 {
        AT.store(now, Relaxed);
        *s = Some(settings());
    }
}

/// Treasure Client is capping the frame rate on purpose (unfocused, idle, menus).
pub(crate) fn note_capped(capped: bool) {
    CAPPED.store(capped, Relaxed);
}

pub(crate) fn note_rendered(rendered: bool) {
    RENDERED.store(rendered, Relaxed);
}

/// Bytes from the server (one websocket message).
pub(crate) fn note_net_in(bytes: usize) {
    PKTS.fetch_add(1, Relaxed);
    BYTES.fetch_add(bytes as u64, Relaxed);
}

pub(crate) fn note_chunk() {
    CHUNKS.fetch_add(1, Relaxed);
}

pub(crate) fn note_mesh_in() {
    MESH_IN.fetch_add(1, Relaxed);
}

/// Blocks changed in a chunk by Modern Block Guessing and by a TuffX+ server.
pub(crate) fn note_via_blocks(guessed: usize, tuff: usize) {
    VIA_GUESSED.fetch_add(guessed as u64, Relaxed);
    VIA_TUFF.fetch_add(tuff as u64, Relaxed);
}

pub(crate) fn note_tuff(on: bool, palette: usize) {
    TUFF_ON.store(on, Relaxed);
    TUFF_PALETTE.store(palette as u64, Relaxed);
}

pub(crate) fn note_mesh_out() {
    MESH_OUT.fetch_add(1, Relaxed);
}

// ---- the record -------------------------------------------------------------------------

#[derive(Clone, Copy, Default)]
struct Minute {
    updates: u64,
    frames: u64,
    wall: u64,
    parts: [u64; N],
    spikes: u32,
    worst: u64,
    slow_secs: u32,
    speed_sum: u64,
    speed_n: u64,
    mem_mb: u32,
    heap_mb: u32,
    pkts: u64,
    bytes: u64,
    chunks: u64,
    mesh_in: u64,
    mesh_out: u64,
}

#[derive(Clone, Copy)]
struct Spike {
    at_s: u64,
    wall: u64,
    parts: [u64; N],
    moving: bool,
}

#[derive(Clone, Copy, Default)]
struct Acc {
    frames: u64,
    wall: u64,
    parts: [u64; N],
    spikes: u64,
}

#[derive(Clone, Copy, Default)]
struct Counts {
    sections: u64,
    entities: u64,
    draws: u64,
    mem_mb: u32,
    heap_mb: u32,
}

struct Lag {
    last_us: u64,
    snap: budget::Totals,
    play_us: u64,
    minutes: Vec<Minute>,
    moving: Acc,
    still: Acc,
    spikes: VecDeque<Spike>,
    worst: Option<Spike>,
    sec: Acc,
    slow: VecDeque<(u64, u64, [u64; N])>,
    calib_best: u64,
    calib_last: u64,
    calib_at: u64,
    first: Option<(u64, Counts, f32)>,
}

static STATE: Mutex<Option<Lag>> = Mutex::new(None);

/// A fixed bit of work: how long it takes says how fast the CPU runs right now.
fn calibrate() -> u64 {
    let t0 = budget::now_us();
    let mut x: u32 = 0x9E37_79B9;
    for i in 0..200_000u32 {
        x ^= x << 13;
        x ^= x >> 17;
        x ^= x << 5;
        x = x.wrapping_add(i);
    }
    std::hint::black_box(x);
    budget::now_us().saturating_sub(t0).max(1)
}

fn memory_mb() -> (u32, u32) {
    #[cfg(target_arch = "wasm32")]
    {
        let wasm = (core::arch::wasm32::memory_size(0) * 65536 / (1024 * 1024)) as u32;
        let heap = (|| {
            let perf = js_sys::Reflect::get(&js_sys::global(), &"performance".into()).ok()?;
            let mem = js_sys::Reflect::get(&perf, &"memory".into()).ok()?;
            let used = js_sys::Reflect::get(&mem, &"usedJSHeapSize".into()).ok()?.as_f64()?;
            Some((used / (1024.0 * 1024.0)) as u32)
        })()
        .unwrap_or(0);
        (wasm, heap)
    }
    #[cfg(not(target_arch = "wasm32"))]
    (0, 0)
}

fn counts_now() -> Counts {
    let (mem_mb, heap_mb) = memory_mb();
    let (sections, entities, draws) = budget::scene_counts();
    Counts { sections, entities, draws, mem_mb, heap_mb }
}

/// Called at the start of every game update, with the timers' running totals.
pub(crate) fn frame(now: u64, totals: budget::Totals) {
    let mut guard = STATE.lock().unwrap();
    let st = guard.get_or_insert_with(|| Lag {
        last_us: 0,
        snap: totals,
        play_us: 0,
        minutes: Vec::new(),
        moving: Acc::default(),
        still: Acc::default(),
        spikes: VecDeque::new(),
        worst: None,
        sec: Acc::default(),
        slow: VecDeque::new(),
        calib_best: u64::MAX,
        calib_last: 0,
        calib_at: 0,
        first: None,
    });
    let prev = std::mem::replace(&mut st.snap, totals);
    let last = std::mem::replace(&mut st.last_us, now);
    let pkts = PKTS.swap(0, Relaxed);
    let bytes = BYTES.swap(0, Relaxed);
    let chunks = CHUNKS.swap(0, Relaxed);
    let mesh_in = MESH_IN.swap(0, Relaxed);
    let mesh_out = MESH_OUT.swap(0, Relaxed);
    if last == 0 {
        return;
    }
    let wall = now.saturating_sub(last);
    if !IN_WORLD.load(Relaxed) || CAPPED.load(Relaxed) || wall > 2_000_000 {
        st.sec = Acc::default(); // a pause, a menu, a hidden tab: not lag
        return;
    }

    // this update's parts
    let d = |a: u64, b: u64| a.saturating_sub(b);
    let mut parts = [0u64; N];
    for i in 0..budget::SLOTS_N {
        let v = d(totals.slots[i], prev.slots[i]);
        let at = match i {
            0..=10 => 1 + i,
            11 => 20, // 1.12 translator
            12 => 21, // meshes in
            13 => 18, // game tick
            _ => 19,  // packets
        };
        parts[at] += v;
    }
    for (k, i) in DRAW.enumerate() {
        parts[i] = d(totals.phases[k], prev.phases[k]);
    }
    parts[17] = d(totals.bot, prev.bot);
    let ours = d(totals.ours, prev.ours);
    let inside: u64 = INSIDE.map(|i| parts[i]).sum();
    parts[0] = ours.saturating_sub(inside);
    let busy: u64 = ours + DRAW.map(|i| parts[i]).sum::<u64>() + BETWEEN.map(|i| parts[i]).sum::<u64>();
    parts[BROWSER] = wall.saturating_sub(busy);
    let rendered = RENDERED.load(Relaxed);
    let moving = MOVING.load(Relaxed);

    st.play_us += wall;
    let mi = ((st.play_us / 60_000_000) as usize).min(MAX_MINUTES - 1);
    if st.minutes.len() <= mi {
        let (mem, heap) = memory_mb();
        st.minutes.resize(mi + 1, Minute::default());
        st.minutes[mi].mem_mb = mem;
        st.minutes[mi].heap_mb = heap;
    }
    if st.first.is_none() && st.play_us >= 60_000_000 {
        let speed = st.minutes.first().map_or(0.0, |m| m.speed_sum as f32 / m.speed_n.max(1) as f32);
        st.first = Some((0, counts_now(), speed));
    }
    // the CPU speed, every 3 seconds of play
    if now.saturating_sub(st.calib_at) > 3_000_000 {
        st.calib_at = now;
        let t = calibrate();
        st.calib_last = t;
        st.calib_best = st.calib_best.min(t);
        let m = &mut st.minutes[mi];
        m.speed_sum += st.calib_best * 100 / t;
        m.speed_n += 1;
    }
    let m = &mut st.minutes[mi];
    m.updates += 1;
    m.frames += u64::from(rendered);
    m.wall += wall;
    for i in 0..N {
        m.parts[i] += parts[i];
    }
    m.pkts += pkts;
    m.bytes += bytes;
    m.chunks += chunks;
    m.mesh_in += mesh_in;
    m.mesh_out += mesh_out;
    let spike = wall >= SPIKE_US;
    if spike {
        m.spikes += 1;
        m.worst = m.worst.max(wall);
        let s = Spike { at_s: st.play_us / 1_000_000, wall, parts, moving };
        if st.worst.is_none_or(|w| wall > w.wall) {
            st.worst = Some(s);
        }
        st.spikes.push_back(s);
        if st.spikes.len() > KEEP_SPIKES {
            st.spikes.pop_front();
        }
    }
    let acc = if moving { &mut st.moving } else { &mut st.still };
    acc.frames += u64::from(rendered);
    acc.wall += wall;
    acc.spikes += u64::from(spike);
    for i in 0..N {
        acc.parts[i] += parts[i];
    }
    // seconds under 30 fps
    st.sec.frames += u64::from(rendered);
    st.sec.wall += wall;
    for i in 0..N {
        st.sec.parts[i] += parts[i];
    }
    if st.sec.wall >= 1_000_000 {
        let fps = st.sec.frames * 1_000_000 / st.sec.wall;
        if fps < 30 {
            st.minutes[mi].slow_secs += 1;
            let f = st.sec.frames.max(1);
            let per: [u64; N] = std::array::from_fn(|i| st.sec.parts[i] / f);
            st.slow.push_back((st.play_us / 1_000_000, fps, per));
            if st.slow.len() > KEEP_SLOW {
                st.slow.pop_front();
            }
        }
        st.sec = Acc::default();
    }
}

// ---- the report -------------------------------------------------------------------------

fn ms(us: u64) -> String {
    format!("{:.1}", us as f64 / 1000.0)
}

fn clock(s: u64) -> String {
    format!("{}:{:02}", s / 60, s % 60)
}

/// The biggest parts among `which`, as "name 3.2, name 1.1" (per frame).
fn top(parts: &[u64; N], frames: u64, which: impl Iterator<Item = usize>, n: usize) -> String {
    let f = frames.max(1);
    let mut list: Vec<usize> = which.filter(|i| parts[*i] / f >= 50).collect();
    list.sort_by(|a, b| parts[*b].cmp(&parts[*a]));
    let out: Vec<String> = list.iter().take(n).map(|i| format!("{} §f{}§7", NAMES[*i], ms(parts[*i] / f))).collect();
    if out.is_empty() { "§fnothing".to_owned() } else { out.join(", ") }
}

fn spike_text(s: &Spike, n: usize) -> String {
    format!(
        "§8{} §f{}ms§7{} {}",
        clock(s.at_s),
        s.wall / 1000,
        if s.moving { "" } else { " still" },
        top(&s.parts, 1, 0..N, n)
    )
}

#[derive(Default)]
struct Sum {
    m: Minute,
    speed: Option<u64>,
}

fn sum(minutes: &[Minute]) -> Sum {
    let mut s = Sum::default();
    let (mut sp, mut spn) = (0, 0);
    for m in minutes {
        s.m.updates += m.updates;
        s.m.frames += m.frames;
        s.m.wall += m.wall;
        for i in 0..N {
            s.m.parts[i] += m.parts[i];
        }
        s.m.spikes += m.spikes;
        s.m.worst = s.m.worst.max(m.worst);
        s.m.slow_secs += m.slow_secs;
        s.m.pkts += m.pkts;
        s.m.bytes += m.bytes;
        s.m.chunks += m.chunks;
        s.m.mesh_in += m.mesh_in;
        s.m.mesh_out += m.mesh_out;
        sp += m.speed_sum;
        spn += m.speed_n;
        s.m.mem_mb = m.mem_mb;
        s.m.heap_mb = m.heap_mb;
    }
    s.speed = (spn > 0).then(|| sp / spn);
    s
}

fn fps(frames: u64, wall: u64) -> u64 {
    if wall == 0 { 0 } else { frames * 1_000_000 / wall }
}

/// Which part took the most of the spike time.
fn verdict(spikes: &VecDeque<Spike>, speed: Option<u64>) -> String {
    let mut t = [0u64; N];
    for s in spikes {
        for i in 0..N {
            t[i] += s.parts[i];
        }
    }
    let all: u64 = t.iter().sum();
    let mut out = String::new();
    if all > 0 {
        let big = (0..N).max_by_key(|i| t[*i]).unwrap_or(0);
        let _ = write!(out, "Most spike time: §f{} {}%§7", NAMES[big], t[big] * 100 / all);
    } else {
        out.push_str("No spikes kept yet");
    }
    if let Some(sp) = speed
        && sp < 60
    {
        let _ = write!(out, " · the CPU runs at §c{sp}%§7 (heat)");
    }
    out
}

/// The `.lag` lines (short) or `.lag full` lines.
pub(crate) fn report(full: bool) -> Vec<String> {
    let guard = STATE.lock().unwrap();
    let Some(st) = guard.as_ref().filter(|s| s.play_us > 2_000_000) else {
        return vec!["§6[Lag] §7no data yet: play in a world for a few seconds".to_owned()];
    };
    let mut out = Vec::new();
    let n = st.minutes.len();
    let recent = &st.minutes[n.saturating_sub(5)..];
    let w = if full { sum(&st.minutes) } else { sum(recent) };
    let m = &w.m;
    let f = m.frames.max(1);
    let span = if full { format!("{} min", n) } else { format!("last {} min", recent.len()) };
    out.push(format!(
        "§6[Lag] §f{} min played §7({span}) · §f{} fps §7· §f{} §7spikes, worst §f{}ms §7· §f{} §7slow seconds{}{}",
        st.play_us / 60_000_000,
        fps(m.frames, m.wall),
        m.spikes,
        m.worst / 1000,
        m.slow_secs,
        w.speed.map_or(String::new(), |s| format!(" · speed §f{s}%§7")),
        if m.mem_mb > 0 { format!(" · §f{} MB§7 (JS {} MB)", m.mem_mb, m.heap_mb) } else { String::new() },
    ));
    let per = |i: usize| m.parts[i] / f;
    let game: u64 = per(0) + INSIDE.map(per).sum::<u64>();
    let draw: u64 = DRAW.map(per).sum();
    let between: u64 = BETWEEN.map(per).sum();
    out.push(format!(
        "§7Frame §f{}ms§7 = game §f{}§7 + draw §f{}§7 + between frames §f{}§7 + browser/idle §f{}",
        ms(m.wall / f),
        ms(game),
        ms(draw),
        ms(between),
        ms(per(BROWSER)),
    ));
    out.push(format!(
        "§7Game: {} §7· Draw: {} §7· Between: {}",
        top(&m.parts, f, std::iter::once(0).chain(INSIDE), if full { 6 } else { 4 }),
        top(&m.parts, f, DRAW, 3),
        top(&m.parts, f, BETWEEN, 3),
    ));
    for (name, a) in [("Moving", &st.moving), ("Still", &st.still)] {
        if a.frames < 30 {
            continue;
        }
        let af = a.frames.max(1);
        let work: u64 = (0..BROWSER).map(|i| a.parts[i]).sum::<u64>() / af;
        out.push(format!(
            "§7{name} §f{} fps§7, work §f{}ms§7 (game {}, draw {}, between {}), browser/idle {}, spikes §f{}",
            fps(a.frames, a.wall),
            ms(work),
            ms((std::iter::once(0).chain(INSIDE)).map(|i| a.parts[i]).sum::<u64>() / af),
            ms(DRAW.map(|i| a.parts[i]).sum::<u64>() / af),
            ms(BETWEEN.map(|i| a.parts[i]).sum::<u64>() / af),
            ms(a.parts[BROWSER] / af),
            a.spikes,
        ));
    }
    let shown = if full { st.spikes.len() } else { 3 };
    let last: Vec<String> = st.spikes.iter().rev().take(shown).map(|s| spike_text(s, if full { 4 } else { 2 })).collect();
    if last.is_empty() {
        out.push("§7Spikes: §fnone".to_owned());
    } else if full {
        out.push("§7Last spikes (newest first):".to_owned());
        for pair in last.chunks(2) {
            out.push(format!("§7  {}", pair.join(" §8| ")));
        }
    } else {
        out.push(format!("§7Spikes: {}", last.join(" §8| ")));
    }
    let secs = (m.wall / 1_000_000).max(1);
    let (sections, entities, draws) = budget::scene_counts();
    out.push(format!(
        "§7Now: §f{sections}§7 sections, §f{draws}§7 draws, §f{entities}§7 entities · §f{}§7 msgs/s ({} KB/s), §f{}§7 chunks/min, meshes §f{}§7/s in, {}/s out",
        m.pkts / secs,
        m.bytes / 1024 / secs,
        m.chunks * 60 / secs,
        m.mesh_in / secs,
        m.mesh_out / secs,
    ));
    if full {
        full_extra(st, &w, &mut out);
    }
    out.push(format!(
        "§6[Lag] §7{}{}",
        verdict(&st.spikes, w.speed),
        if full { "" } else { " §8(.lag full for more)" }
    ));
    out
}

fn full_extra(st: &Lag, w: &Sum, out: &mut Vec<String>) {
    let m = &w.m;
    let f = m.frames.max(1);
    // every part, per frame
    let mut all: Vec<String> = Vec::new();
    for i in 0..N {
        let v = m.parts[i] / f;
        if v >= 20 {
            all.push(format!("{} §f{}§7", NAMES[i], ms(v)));
        }
    }
    out.push(format!("§7All parts (ms per frame): {}", all.join(", ")));
    out.push(format!(
        "§7Updates §f{}§7 for §f{}§7 frames drawn ({}% not drawn: max fps setting)",
        m.updates,
        m.frames,
        (m.updates.saturating_sub(m.frames)) * 100 / m.updates.max(1)
    ));
    if let Some(s) = &st.worst {
        out.push(format!("§7Worst frame: {}", spike_text(s, 6)));
    }
    if !st.slow.is_empty() {
        let list: Vec<String> = st
            .slow
            .iter()
            .rev()
            .take(4)
            .map(|(at, fps, per)| format!("§8{} §f{fps} fps§7 {}", clock(*at), top(per, 1, 0..N, 3)))
            .collect();
        out.push("§7Slow seconds (under 30 fps, ms per frame):".to_owned());
        for pair in list.chunks(2) {
            out.push(format!("§7  {}", pair.join(" §8| ")));
        }
    }
    // the minutes
    let n = st.minutes.len();
    let rows: Vec<String> = st.minutes[n.saturating_sub(8)..]
        .iter()
        .enumerate()
        .map(|(k, mi)| {
            let idx = n.saturating_sub(8) + k + 1;
            let work: u64 = (0..BROWSER).map(|i| mi.parts[i]).sum::<u64>() / mi.frames.max(1);
            format!(
                "§8{idx}§f {}fps {}ms {}sp {}%{}",
                fps(mi.frames, mi.wall),
                ms(work),
                mi.spikes,
                mi.speed_sum / mi.speed_n.max(1),
                if mi.mem_mb > 0 { format!(" {}MB", mi.mem_mb) } else { String::new() }
            )
        })
        .collect();
    out.push("§7Per minute (fps, work, spikes, speed, memory):".to_owned());
    for chunk in rows.chunks(4) {
        out.push(format!("§7  {}", chunk.join(" §8| ")));
    }
    // growth since the first minute
    if let Some((_, c0, s0)) = &st.first {
        let c = counts_now();
        let m0 = st.minutes.first().copied().unwrap_or_default();
        let ml = st.minutes.last().copied().unwrap_or_default();
        let wk = |mi: &Minute| (0..BROWSER).map(|i| mi.parts[i]).sum::<u64>() / mi.frames.max(1);
        out.push(format!(
            "§7Since minute 1: work §f{}→{}ms§7, speed §f{:.0}→{}%§7, memory §f{}→{} MB§7 (JS {}→{}), sections {}→{}, entities {}→{}, draws {}→{}",
            ms(wk(&m0)),
            ms(wk(&ml)),
            s0,
            ml.speed_sum / ml.speed_n.max(1),
            c0.mem_mb,
            c.mem_mb,
            c0.heap_mb,
            c.heap_mb,
            c0.sections,
            c.sections,
            c0.entities,
            c.entities,
            c0.draws,
            c.draws
        ));
    } else {
        out.push("§7Since minute 1: §fneeds 1 min of play".to_owned());
    }
    out.push(format!(
        "§7CPU test: best §f{}µs§7, now §f{}µs§7 · {}",
        st.calib_best,
        st.calib_last,
        device()
    ));
    let s = SETTINGS.lock().unwrap().clone().unwrap_or_default();
    let received = crate::client::tracking::received_chunks().lock().map_or(0, |c| c.len());
    let meshed = crate::client::tracking::meshed_chunks().lock().map_or(0, |c| c.len());
    out.push(format!(
        "§7Mesh worker §f{}§7 · objects §f{}§7 ({} placed), cameras §f{}§7 · columns §f{received}§7 loaded, {meshed} meshed, server distance §f{}",
        crate::client::mesh_worker::status(),
        s.entities,
        s.placed,
        s.cameras,
        azalea_world::retain::server_radius(),
    ));
    out.push(format!(
        "§7Mouse: §f{}§7 jumps dropped (biggest {}), §f{}§7 big moves let through (biggest {}), raw input §f{}",
        MOUSE_DROPPED.swap(0, Relaxed),
        MOUSE_DROPPED_MAX.swap(0, Relaxed),
        MOUSE_KEPT.swap(0, Relaxed),
        MOUSE_KEPT_MAX.swap(0, Relaxed),
        on(crate::platform::pointer::raw_on()),
    ));
    if let Some(line) = crate::gui::tc_pvp::lag_line() {
        out.push(line);
    }
    let tc = crate::tc_mods::get();
    out.push(format!(
        "§7Via: §f{}§7, guessing §f{}§7 ({} blocks), TuffX+ §f{}§7 (palette {}, {} blocks)",
        on(tc.via),
        on(tc.via_guess),
        VIA_GUESSED.load(Relaxed),
        on(TUFF_ON.load(Relaxed)),
        TUFF_PALETTE.load(Relaxed),
        VIA_TUFF.load(Relaxed),
    ));
    out.push(format!(
        "§7Settings: render distance §f{}§7, scale §f{}%§7, max fps §f{}§7, vsync §f{}§7, shaders §f{}§7, smooth light §f{}§7, fancy leaves §f{}§7, particles §f{}§7, clouds §f{}§7 · leaf cull §f{}§7, entity cull §f{}§7, particle core §f{}§7, perf pack §f{}",
        s.render_distance,
        s.render_scale,
        if s.max_fps >= crate::gui::MAX_FPS_UNLIMITED { "max".to_owned() } else { s.max_fps.to_string() },
        on(s.vsync),
        on(s.shaders),
        on(s.smooth_lighting),
        on(s.fancy_leaves),
        s.particles,
        s.clouds,
        tc.leaf_cull,
        on(tc.entity_culling),
        on(tc.particle_core),
        on(tc.fps_reducer),
    ));
}

fn on(b: bool) -> &'static str {
    if b { "on" } else { "off" }
}

/// The device, as the browser describes it.
fn device() -> String {
    #[cfg(target_arch = "wasm32")]
    {
        let Some(w) = web_sys::window() else { return String::new() };
        let nav = w.navigator();
        let cores = nav.hardware_concurrency() as u32;
        let ram = js_sys::Reflect::get(&nav, &"deviceMemory".into())
            .ok()
            .and_then(|v| v.as_f64())
            .map_or(String::new(), |g| format!(", {g} GB"));
        let ua = nav.user_agent().unwrap_or_default();
        let os = if ua.contains("CrOS") {
            "ChromeOS"
        } else if ua.contains("Windows") {
            "Windows"
        } else if ua.contains("Mac OS") {
            "Mac"
        } else if ua.contains("Android") {
            "Android"
        } else if ua.contains("Linux") {
            "Linux"
        } else {
            "?"
        };
        let chrome = ua.split("Chrome/").nth(1).and_then(|v| v.split('.').next()).unwrap_or("?");
        let dpr = w.device_pixel_ratio();
        let (iw, ih) = (
            w.inner_width().ok().and_then(|v| v.as_f64()).unwrap_or(0.0),
            w.inner_height().ok().and_then(|v| v.as_f64()).unwrap_or(0.0),
        );
        format!(
            "{os}, Chrome {chrome}, §f{cores}§7 cores{ram}, window §f{}x{}§7 ×{dpr:.2}",
            iw as u32, ih as u32
        )
    }
    #[cfg(not(target_arch = "wasm32"))]
    String::new()
}
