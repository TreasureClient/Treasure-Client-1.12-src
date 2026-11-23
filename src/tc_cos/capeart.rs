//! Treasure Client cosmetics: draws the animated capes (all original designs). A copy of the JS
//! and WASM editions' `TCCapeArt`, checked to give the same pictures.
//!
//! Every cape is a strip of [`FRAMES`] pictures laid out in a grid on one texture; the game only
//! moves the cape's texture coordinates to the next picture, so nothing is redrawn while you play.
//!
//! A cape picture follows Minecraft's cape layout at 4x detail: the outside of the cape is 40x64
//! pixels at (4,4), the lining at (48,4), the edges around them. Each animation loops exactly.

use std::f64::consts::PI;

/// Detail: 4 texture pixels per Minecraft pixel.
pub const S: usize = 4;
/// One picture: the part of the cape texture the cape uses (22x17), at 4x.
pub const CELL_W: usize = 22 * S;
pub const CELL_H: usize = 17 * S;
pub const FRAMES: usize = 64;
pub const COLS: usize = 8;
pub const TEX_W: usize = CELL_W * COLS;
pub const TEX_H: usize = CELL_H * (FRAMES / COLS);
/// How long one loop of the animation lasts.
pub const LOOP_MS: u64 = 4000;

const FW: usize = 10 * S;
const FH: usize = 16 * S;
const FWI: i32 = FW as i32;
const FHI: i32 = FH as i32;

pub const IDS: &[&str] = &["treasure", "frost", "tropical", "ember", "aurora", "galaxy", "circuit", "theme"];

pub fn known(id: &str) -> bool {
    match id.strip_prefix("custom:") {
        Some(spec) => super::valid_mk(spec),
        None => IDS.contains(&id),
    }
}

/// True if a cape changes with the wearer's theme colour.
pub fn uses_theme(id: &str) -> bool {
    id == "theme"
        || id.strip_prefix("custom:").is_some_and(|spec| {
            let p: Vec<&str> = spec.split('.').collect();
            p.len() == 5 && (p[1] == "theme" || p[2] == "theme")
        })
}

/// The frame to show at a time (ms).
pub fn frame_at(ms: u64) -> usize {
    ((ms % LOOP_MS) * FRAMES as u64 / LOOP_MS) as usize
}

/// Draws a cape a few frames at a time, so making one never stalls a frame.
pub struct Builder {
    /// ARGB, TEX_W x TEX_H.
    pub pixels: Vec<u32>,
    art: Art,
    r: Vec<f32>,
    g: Vec<f32>,
    b: Vec<f32>,
    next: usize,
}

impl Builder {
    /// `accent` = the theme colour (0xRRGGBB; only "theme" and Maker capes use it).
    pub fn new(id: &str, accent: u32) -> Builder {
        Builder {
            pixels: vec![0; TEX_W * TEX_H],
            art: Art::make(id, accent),
            r: vec![0.0; FW * FH],
            g: vec![0.0; FW * FH],
            b: vec![0.0; FW * FH],
            next: 0,
        }
    }

    /// Draws up to n more frames; true when the whole cape is done.
    pub fn step(&mut self, n: usize) -> bool {
        for _ in 0..n {
            if self.next >= FRAMES {
                break;
            }
            let f = self.next;
            self.next += 1;
            self.draw_frame(f);
        }
        self.next >= FRAMES
    }

    fn draw_frame(&mut self, f: usize) {
        let t = f as f32 / FRAMES as f32;
        self.r.fill(0.0);
        self.g.fill(0.0);
        self.b.fill(0.0);
        let mut c = Canvas {
            r: &mut self.r,
            g: &mut self.g,
            b: &mut self.b,
        };
        self.art.frame(t, &mut c);
        let out = &mut self.pixels;
        let (ox, oy) = ((f % COLS) * CELL_W, (f / COLS) * CELL_H);
        let (lining, trim) = (self.art.lining, self.art.trim);
        for y in 0..FH {
            for x in 0..FW {
                let i = y * FW + x;
                out[(oy + S + y) * TEX_W + ox + S + x] = argb(self.r[i], self.g[i], self.b[i]);
            }
        }
        for y in 0..FH {
            let k = 1.0 - 0.25 * y as f32 / FH as f32;
            let lin = scale(lining, k);
            for x in 0..FW {
                out[(oy + S + y) * TEX_W + ox + 2 * S + FW + x] = lin;
            }
            for x in 0..S {
                out[(oy + S + y) * TEX_W + ox + x] = trim;
                out[(oy + S + y) * TEX_W + ox + S + FW + x] = trim;
            }
        }
        for y in 0..S {
            for x in 0..FW {
                out[(oy + y) * TEX_W + ox + S + x] = trim;
                out[(oy + y) * TEX_W + ox + S + FW + x] = trim;
            }
        }
    }
}

/// Draws a whole cape at once (for checks).
pub fn build(id: &str, accent: u32) -> Vec<u32> {
    let mut b = Builder::new(id, accent);
    while !b.step(FRAMES) {}
    b.pixels
}

// ------------------------------------------------------------------ drawing helpers

struct Canvas<'a> {
    r: &'a mut [f32],
    g: &'a mut [f32],
    b: &'a mut [f32],
}

impl Canvas<'_> {
    fn put(&mut self, i: usize, cr: f32, cg: f32, cb: f32) {
        self.r[i] = cr;
        self.g[i] = cg;
        self.b[i] = cb;
    }

    fn set(&mut self, x: i32, y: i32, cr: f32, cg: f32, cb: f32) {
        if x < 0 || y < 0 || x >= FWI || y >= FHI {
            return;
        }
        self.put((y * FWI + x) as usize, cr, cg, cb);
    }

    /// Mixes a colour in (a = how much).
    fn add(&mut self, x: i32, y: i32, cr: f32, cg: f32, cb: f32, a: f32) {
        if x < 0 || y < 0 || x >= FWI || y >= FHI || a <= 0.0 {
            return;
        }
        let i = (y * FWI + x) as usize;
        self.r[i] = lerp(self.r[i], cr, a);
        self.g[i] = lerp(self.g[i], cg, a);
        self.b[i] = lerp(self.b[i], cb, a);
    }

    fn flake(&mut self, x: i32, y: i32, size: i32, a: f32) {
        self.add(x, y, 1.0, 1.0, 1.0, a);
        for k in 1..=size {
            let f = a * if k == size && size > 1 { 0.6 } else { 0.85 };
            self.add(x + k, y, 0.9, 0.97, 1.0, f);
            self.add(x - k, y, 0.9, 0.97, 1.0, f);
            self.add(x, y + k, 0.9, 0.97, 1.0, f);
            self.add(x, y - k, 0.9, 0.97, 1.0, f);
        }
        if size >= 3 {
            for (dx, dy) in [(1, 1), (-1, -1), (1, -1), (-1, 1)] {
                self.add(x + dx, y + dy, 0.9, 0.97, 1.0, a * 0.5);
            }
        }
    }

    #[allow(clippy::too_many_arguments)]
    fn sparkle(&mut self, x: i32, y: i32, arm: i32, a: f32, cr: f32, cg: f32, cb: f32) {
        if a <= 0.02 {
            return;
        }
        self.add(x, y, 1.0, 1.0, 1.0, a);
        for k in 1..=arm {
            let f = a * (1.0 - k as f32 / (arm as f32 + 1.0));
            self.add(x + k, y, cr, cg, cb, f);
            self.add(x - k, y, cr, cg, cb, f);
            self.add(x, y + k, cr, cg, cb, f);
            self.add(x, y - k, cr, cg, cb, f);
        }
    }
}

fn c8(v: f32) -> u32 {
    let i = (v * 255.0 + 0.5) as i32;
    i.clamp(0, 255) as u32
}

fn argb(r: f32, g: f32, b: f32) -> u32 {
    0xFF00_0000 | (c8(r) << 16) | (c8(g) << 8) | c8(b)
}

fn ch(c: u32, s: u32) -> f32 {
    ((c >> s) & 255) as f32 / 255.0
}

fn scale(c: u32, k: f32) -> u32 {
    0xFF00_0000 | (c8(ch(c, 16) * k) << 16) | (c8(ch(c, 8) * k) << 8) | c8(ch(c, 0) * k)
}

fn mix_rgb(a: u32, b: u32, k: f32) -> u32 {
    argb(lerp(ch(a, 16), ch(b, 16), k), lerp(ch(a, 8), ch(b, 8), k), lerp(ch(a, 0), ch(b, 0), k))
}

fn lerp(a: f32, b: f32, k: f32) -> f32 {
    a + (b - a) * k
}

fn sq(v: f32) -> f32 {
    v * v
}

fn frac(v: f32) -> f32 {
    v - v.floor()
}

fn smooth(e0: f32, e1: f32, v: f32) -> f32 {
    let k = ((v - e0) / (e1 - e0)).clamp(0.0, 1.0);
    k * k * (3.0 - 2.0 * k)
}

/// Java's Math.round for floats.
fn jround(v: f32) -> i32 {
    (v + 0.5).floor() as i32
}

/// sin of a value worked out in double precision, as Java does with Math.sin.
fn dsin(v: f64) -> f32 {
    v.sin() as f32
}

pub(crate) fn hash(x: i32, y: i32, seed: i32) -> f32 {
    let mut h = x
        .wrapping_mul(374_761_393)
        .wrapping_add(y.wrapping_mul(668_265_263))
        .wrapping_add(seed.wrapping_mul(982_451_653));
    h = (h ^ ((h as u32) >> 13) as i32).wrapping_mul(1_274_126_177);
    h ^= ((h as u32) >> 16) as i32;
    (h & 0xFF_FFFF) as f32 / 0x100_0000 as f32
}

fn noise(x: f32, y: f32, seed: i32) -> f32 {
    let (xi, yi) = (x.floor() as i32, y.floor() as i32);
    let (mut fx, mut fy) = (x - xi as f32, y - yi as f32);
    fx = fx * fx * (3.0 - 2.0 * fx);
    fy = fy * fy * (3.0 - 2.0 * fy);
    let (a, b) = (hash(xi, yi, seed), hash(xi + 1, yi, seed));
    let (c, d) = (hash(xi, yi + 1, seed), hash(xi + 1, yi + 1, seed));
    lerp(lerp(a, b, fx), lerp(c, d, fx), fy)
}

fn fbm(x: f32, y: f32, seed: i32) -> f32 {
    noise(x, y, seed) * 0.55 + noise(x * 2.1, y * 2.1, seed + 1) * 0.3 + noise(x * 4.3, y * 4.3, seed + 2) * 0.15
}

fn hsv(h: f32, s: f32, v: f32) -> [f32; 3] {
    let h = frac(h) * 6.0;
    let i = h as i32;
    let f = h - i as f32;
    let (p, q, u) = (v * (1.0 - s), v * (1.0 - s * f), v * (1.0 - s * (1.0 - f)));
    match i {
        0 => [v, u, p],
        1 => [q, v, p],
        2 => [p, v, u],
        3 => [p, q, v],
        4 => [u, p, v],
        _ => [v, p, q],
    }
}

/// java.util.Random, so the circuit tracks come out the same as in the other editions.
struct JavaRandom(i64);

impl JavaRandom {
    fn new(seed: i64) -> JavaRandom {
        JavaRandom((seed ^ 0x5DEE_CE66D) & ((1 << 48) - 1))
    }

    fn next(&mut self, bits: u32) -> i32 {
        self.0 = (self.0.wrapping_mul(0x5DEE_CE66D).wrapping_add(0xB)) & ((1 << 48) - 1);
        (self.0 >> (48 - bits)) as i32
    }

    fn next_int(&mut self, bound: i32) -> i32 {
        if bound & (-bound) == bound {
            return ((bound as i64 * self.next(31) as i64) >> 31) as i32;
        }
        loop {
            let bits = self.next(31);
            let val = bits % bound;
            if bits.wrapping_sub(val).wrapping_add(bound - 1) >= 0 {
                return val;
            }
        }
    }
}

// ------------------------------------------------------------------ the designs

enum Kind {
    Treasure { ar: f32, ag: f32, ab: f32, theme: bool },
    Frost,
    Tropical,
    Ember { crack: Vec<f32>, cell: Vec<f32> },
    Aurora,
    Galaxy,
    Circuit { pix: Vec<(i32, i32, usize, f32)>, path_len: Vec<i32> },
    Custom(Custom),
}

struct Art {
    lining: u32,
    trim: u32,
    kind: Kind,
}

struct Custom {
    pat: usize,
    emb: usize,
    mot: usize,
    r1: f32,
    g1: f32,
    b1: f32,
    r2: f32,
    g2: f32,
    b2: f32,
}

impl Art {
    fn make(id: &str, accent: u32) -> Art {
        let accent = 0xFF00_0000 | (accent & 0xFF_FFFF);
        if let Some(spec) = id.strip_prefix("custom:") {
            return Art::custom(spec, accent);
        }
        match id {
            "frost" => Art { lining: 0xFF0C2440, trim: 0xFFBFEFFF, kind: Kind::Frost },
            "tropical" => Art { lining: 0xFF06343C, trim: 0xFF45E3C6, kind: Kind::Tropical },
            "ember" => Art::ember(),
            "aurora" => Art { lining: 0xFF070C22, trim: 0xFF6CF2B4, kind: Kind::Aurora },
            "galaxy" => Art { lining: 0xFF0A0618, trim: 0xFFB38CFF, kind: Kind::Galaxy },
            "circuit" => Art::circuit(),
            "theme" => Art::treasure(accent, true),
            _ => Art::treasure(0xFFFFD76B, false),
        }
    }

    fn treasure(accent: u32, theme: bool) -> Art {
        Art {
            lining: if theme { mix_rgb(0xFF0D0D10, accent, 0.18) } else { 0xFF3A2410 },
            trim: mix_rgb(0xFF00_0000, accent, 0.7),
            kind: Kind::Treasure { ar: ch(accent, 16), ag: ch(accent, 8), ab: ch(accent, 0), theme },
        }
    }

    fn ember() -> Art {
        let n = 15;
        let px: Vec<f32> = (0..n).map(|k| hash(k, 5, 31) * FW as f32).collect();
        let py: Vec<f32> = (0..n).map(|k| hash(k, 6, 31) * FH as f32).collect();
        let mut crack = vec![0.0; FW * FH];
        let mut cell = vec![0.0; FW * FH];
        for y in 0..FH {
            for x in 0..FW {
                let (xf, yf) = (x as f32, y as f32);
                let wx = xf + (noise(xf * 0.2, yf * 0.2, 3) - 0.5) * 5.0;
                let wy = yf + (noise(xf * 0.2, yf * 0.2, 4) - 0.5) * 5.0;
                let (mut d1, mut d2, mut c1) = (1e9f32, 1e9f32, 0);
                for k in 0..n as usize {
                    let (dx, dy) = (wx - px[k], wy - py[k]);
                    let d = (dx * dx + dy * dy).sqrt();
                    if d < d1 {
                        d2 = d1;
                        d1 = d;
                        c1 = k;
                    } else if d < d2 {
                        d2 = d;
                    }
                }
                crack[y * FW + x] = d2 - d1;
                cell[y * FW + x] = hash(c1 as i32, 9, 31);
            }
        }
        Art { lining: 0xFF1C0C06, trim: 0xFFFF7A2C, kind: Kind::Ember { crack, cell } }
    }

    fn circuit() -> Art {
        let mut pix = Vec::new();
        let paths = 8;
        let mut path_len = vec![0i32; paths];
        let mut rnd = JavaRandom::new(4242);
        for (p, plen) in path_len.iter_mut().enumerate() {
            let mut cx = 1 + rnd.next_int(8);
            let mut cy = if p % 2 == 0 { 0 } else { 2 + rnd.next_int(4) };
            let (mut len, mut s) = (0, 0);
            let steps = 10 + rnd.next_int(10);
            let mut last_dir = 0;
            for _ in 0..steps {
                let roll = rnd.next_int(10);
                let dir = if roll < 6 || last_dir != 0 { 0 } else if roll < 8 { 1 } else { 2 };
                let nx = cx + if dir == 1 { -1 } else if dir == 2 { 1 } else { 0 };
                let ny = cy + if dir == 0 { 1 } else { 0 };
                if !(0..=9).contains(&nx) || ny > 15 {
                    break;
                }
                for q in 0..S as i32 {
                    let x = cx * S as i32 + 2 + (nx - cx) * q;
                    let y = cy * S as i32 + 2 + (ny - cy) * q;
                    pix.push((x, y, p, s as f32));
                    s += 1;
                }
                cx = nx;
                cy = ny;
                last_dir = dir;
                len = s;
            }
            *plen = len.max(1);
        }
        Art { lining: 0xFF06140F, trim: 0xFF2FD39A, kind: Kind::Circuit { pix, path_len } }
    }

    fn custom(spec: &str, accent: u32) -> Art {
        let p: Vec<&str> = spec.split('.').collect();
        let get = |i: usize| p.get(i).copied().unwrap_or("");
        let color = |id: &str| super::palette_rgb(id, accent);
        let (c1, c2) = (color(get(1)), color(get(2)));
        let c = Custom {
            pat: super::index_of(super::PATTERNS, get(0)).unwrap_or(0),
            emb: super::index_of(super::EMBLEMS, get(3)).unwrap_or(0),
            mot: super::index_of(super::MOTIONS, get(4)).unwrap_or(0),
            r1: ch(c1, 16),
            g1: ch(c1, 8),
            b1: ch(c1, 0),
            r2: ch(c2, 16),
            g2: ch(c2, 8),
            b2: ch(c2, 0),
        };
        Art {
            lining: mix_rgb(0xFF00_0000 | c1, 0xFF0A0A0C, 0.62),
            trim: 0xFF00_0000 | mix_rgb(0xFF00_0000 | c2, 0xFF00_0000, 0.25),
            kind: Kind::Custom(c),
        }
    }

    fn frame(&self, t: f32, c: &mut Canvas) {
        match &self.kind {
            Kind::Treasure { ar, ag, ab, theme } => treasure(t, c, *ar, *ag, *ab, *theme),
            Kind::Frost => frost(t, c),
            Kind::Tropical => tropical(t, c),
            Kind::Ember { crack, cell } => ember(t, c, crack, cell),
            Kind::Aurora => aurora(t, c),
            Kind::Galaxy => galaxy(t, c),
            Kind::Circuit { pix, path_len } => circuit(t, c, pix, path_len),
            Kind::Custom(k) => k.frame(t, c),
        }
    }
}

fn in_border(x: i32, y: i32, inset: i32, width: i32) -> bool {
    if x < inset || y < inset || x >= FWI - inset || y >= FHI - inset {
        return false;
    }
    x < inset + width || y < inset + width || x >= FWI - inset - width || y >= FHI - inset - width
}

/// The chest in the middle: 1 = bright gold, 0.75 = mid, 0.5 = dark outline, 0 = none.
fn chest(x: i32, y: i32) -> f32 {
    let (x0, x1, ly0, ly1, by1) = (11, 29, 21, 28, 39);
    if x < x0 || x >= x1 || y < ly0 || y >= by1 {
        return 0.0;
    }
    if y < ly0 + 2 && (x < x0 + 2 || x >= x1 - 2) {
        return 0.0;
    }
    if x == x0 || x == x1 - 1 || y == ly0 || y == by1 - 1 {
        return 0.5;
    }
    if y == ly1 || y == ly1 + 1 {
        return 0.5;
    }
    if (18..22).contains(&x) && y >= ly1 - 2 && y < ly1 + 5 {
        return if (x == 19 || x == 20) && y == ly1 + 2 { 0.5 } else { 1.0 };
    }
    if y > ly1 + 1 && (y - ly1) % 4 == 0 {
        return 0.75;
    }
    if x == x0 + 4 || x == x1 - 5 {
        return 0.75;
    }
    if y < ly1 { 1.0 } else { 0.88 }
}

fn treasure(t: f32, c: &mut Canvas, ar: f32, ag: f32, ab: f32, theme: bool) {
    for y in 0..FHI {
        for x in 0..FWI {
            let i = (y * FWI + x) as usize;
            let v = y as f32 / FH as f32;
            let (mut cr, mut cg, mut cb);
            if theme {
                let k = 0.16 - 0.08 * v;
                cr = 0.04 + ar * k;
                cg = 0.04 + ag * k;
                cb = 0.05 + ab * k;
                let s = ((x + y + (t * 16.0) as i32) % 16 + 16) % 16;
                if s < 2 {
                    cr += ar * 0.14;
                    cg += ag * 0.14;
                    cb += ab * 0.14;
                }
            } else {
                cr = lerp(0.17, 0.07, v);
                cg = lerp(0.105, 0.04, v);
                cb = lerp(0.05, 0.016, v);
                if ((x >> 1) + (y >> 1)) % 2 == 0 {
                    cr *= 0.9;
                    cg *= 0.9;
                    cb *= 0.9;
                }
            }
            let mut gold = 0.0;
            if in_border(x, y, 2, 2) {
                gold = 1.0;
            } else if in_border(x, y, 6, 1) {
                gold = 0.55;
            }
            let e = chest(x, y);
            if e > gold {
                gold = e;
            }
            if gold > 0.0 {
                let d = if gold >= 0.99 { 1.0 } else { gold };
                let shade = if d < 0.6 { 0.55 } else if d < 0.9 { 0.78 } else { 1.0 };
                let m = (d * 1.4f32).min(1.0);
                cr = lerp(cr, ar * shade, m);
                cg = lerp(cg, ag * shade, m);
                cb = lerp(cb, ab * shade, m);
            }
            let s = (x as f32 * 0.55 + y as f32) / (FH as f32 + FW as f32 * 0.55);
            let cc = t * 1.7 - 0.35;
            let sh = ((-sq((s - cc) / 0.045)) as f64).exp() as f32;
            let add = sh * if gold > 0.0 { 0.65 } else { 0.12 };
            c.put(i, cr + add, cg + add, cb + add * 0.85);
        }
    }
    let sp = [[8, 12], [31, 47], [27, 15], [11, 52]];
    for (k, p) in sp.iter().enumerate() {
        let mut tw = dsin(2.0 * PI * (t as f64 * 2.0 + k as f64 * 0.37)).max(0.0);
        tw = tw * tw * tw;
        c.sparkle(p[0], p[1], 2, tw, ar * 0.5 + 0.5, ag * 0.5 + 0.5, ab * 0.5 + 0.5);
    }
}

fn frost(t: f32, c: &mut Canvas) {
    for y in 0..FH {
        for x in 0..FW {
            let i = y * FW + x;
            let (xf, yf) = (x as f32, y as f32);
            let v = yf / FH as f32;
            let n = noise(xf * 0.16, yf * 0.16, 11) * 0.5 + noise(xf * 0.4, yf * 0.4, 12) * 0.25;
            let cr = lerp(0.03, 0.25, v) + n * 0.06;
            let cg = lerp(0.10, 0.58, v) + n * 0.10;
            let cb = lerp(0.22, 0.86, v) + n * 0.10;
            let ridge = 1.0 - (noise(xf * 0.09, yf * 0.09, 13) * 2.0 - 1.0).abs();
            let cry = (ridge as f64).powi(14) as f32 * 0.35;
            let m = frac(v * 0.9 - t * 2.0);
            let sh = ((-sq((m - 0.5) / 0.08)) as f64).exp() as f32 * 0.16;
            c.put(i, cr + cry + sh, cg + cry + sh, cb + cry * 0.8 + sh * 0.7);
        }
    }
    for k in 0..16 {
        let loops = 1 + (k % 2);
        let x0 = hash(k, 1, 77) * FW as f32;
        let y0 = hash(k, 2, 77);
        let y = frac(y0 + t * loops as f32) * (FH as f32 + 8.0) - 4.0;
        let x = x0 + 2.5 * dsin(2.0 * PI * (t * loops as f32 + hash(k, 3, 77)) as f64);
        let size = if k % 3 == 0 { 3 } else if k % 3 == 1 { 2 } else { 1 };
        c.flake(jround(x), jround(y), size, 0.92);
    }
}

fn tropical(t: f32, c: &mut Canvas) {
    let sea = 30;
    for y in 0..FHI {
        for x in 0..FWI {
            let i = (y * FWI + x) as usize;
            let (mut cr, mut cg, mut cb);
            if y < sea {
                let v = y as f32 / sea as f32;
                cr = lerp(0.42, 1.0, v);
                cg = lerp(0.16, 0.46, v);
                cb = lerp(0.45, 0.38, v);
                let (dx, dy) = (x as f32 - 19.5, y as f32 - 21.0);
                let d = (dx * dx + dy * dy).sqrt();
                if d < 10.5 {
                    let cut = y > 18 && ((y - 18) % 4 == 0 || (y > 24 && (y - 18) % 4 == 1));
                    if !cut {
                        let k = ((10.5 - d) * 1.2f32).min(1.0);
                        cr = lerp(cr, 1.0, k);
                        cg = lerp(cg, lerp(0.92, 0.62, (y - 11) as f32 / 10.0), k);
                        cb = lerp(cb, 0.42, k);
                    }
                } else if d < 15.0 {
                    let k = (15.0 - d) / 4.5 * 0.25;
                    cr += k;
                    cg += k * 0.7;
                    cb += k * 0.3;
                }
            } else {
                let v = (y - sea) as f32 / (FHI - sea) as f32;
                cr = lerp(0.04, 0.01, v);
                cg = lerp(0.48, 0.17, v);
                cb = lerp(0.52, 0.24, v);
                let w = 6.0 - v * 3.0;
                if (x as f32 - 19.5).abs() < w {
                    let ripple = dsin(2.0 * PI * ((y as f32 * 0.33 - t * 2.0) as f64) + ((x as f32 * 0.7) as f64).sin() * 2.0);
                    if ripple > 0.45 {
                        let k = (1.0 - v) * 0.7;
                        cr += k;
                        cg += k * 0.75;
                        cb += k * 0.3;
                    }
                }
            }
            c.put(i, cr, cg, cb);
        }
    }
    for j in 0..7 {
        let yj = sea as f32 + 1.0 + j as f32 * 5.2;
        let dir = if j % 2 == 0 { 1.0f32 } else { -1.0 };
        let bright = 1.0 - j as f32 * 0.1;
        for x in 0..FWI {
            let ph = 2.0 * std::f32::consts::PI * (x as f32 / 20.0 + dir * t + j as f32 * 0.31);
            let s = (ph as f64).sin() as f32;
            let y = jround(yj - 1.6 * s);
            c.add(x, y, 0.25 * bright, 0.9 * bright, 0.78 * bright, 0.85);
            if s > 0.8 {
                c.add(x, y - 1, 1.0, 1.0, 1.0, 0.75 * bright);
            }
            c.add(x, y + 1, 0.1, 0.5, 0.45, 0.35 * bright);
        }
    }
    for x in 0..FWI {
        c.add(x, sea, 1.0, 0.75, 0.5, 0.5);
    }
}

fn ember(t: f32, c: &mut Canvas, crack: &[f32], cell: &[f32]) {
    for y in 0..FH {
        for x in 0..FW {
            let i = y * FW + x;
            let ce = cell[i];
            let n = noise(x as f32 * 0.3, y as f32 * 0.3, 8);
            let (mut cr, mut cg, mut cb) = (0.09 + ce * 0.05 + n * 0.04, 0.06 + ce * 0.025 + n * 0.02, 0.05 + n * 0.015);
            let d = crack[i];
            let pulse = 0.55 + 0.45 * dsin(2.0 * PI * ((2.0 * t + y as f32 / FH as f32 + ce * 0.5) as f64));
            if d < 1.3 {
                let k = pulse * (1.0 - d / 1.3 * 0.35);
                cr = lerp(0.55, 1.0, k);
                cg = lerp(0.12, 0.78, k * k);
                cb = lerp(0.02, 0.25, k * k * k);
            } else if d < 4.5 {
                let mut k = 1.0 - (d - 1.3) / 3.2;
                k = k * k * pulse * 0.55;
                cr += k;
                cg += k * 0.32;
                cb += k * 0.06;
            }
            c.put(i, cr, cg, cb);
        }
    }
    for k in 0..9 {
        let loops = 1 + if k % 3 == 0 { 1 } else { 0 };
        let f = frac(hash(k, 1, 51) + t * loops as f32);
        let y = FH as f32 + 3.0 - f * (FH as f32 + 6.0);
        let x = hash(k, 2, 51) * FW as f32 + 2.0 * dsin(2.0 * PI * (f * 2.0 + hash(k, 3, 51)) as f64);
        let a = dsin(PI * f as f64);
        c.add(jround(x), jround(y), 1.0, 0.85, 0.35, a);
        c.add(jround(x), jround(y) + 1, 1.0, 0.45, 0.1, a * 0.5);
    }
}

fn tree_line(x: i32) -> f32 {
    let mut best = (FHI - 5) as f32;
    let tx = [3, 10, 17, 24, 31, 37];
    let th = [10, 7, 12, 8, 11, 7];
    for k in 0..tx.len() {
        let top = (FHI - 5 - th[k] - 2) as f32;
        let y = top + (x - tx[k]).abs() as f32 * 2.2;
        if y < best {
            best = y;
        }
    }
    best
}

fn aurora(t: f32, c: &mut Canvas) {
    for y in 0..FHI {
        for x in 0..FWI {
            let i = (y * FWI + x) as usize;
            let v = y as f32 / FH as f32;
            let (mut cr, mut cg, mut cb) = (lerp(0.02, 0.05, v), lerp(0.03, 0.08, v), lerp(0.10, 0.20, v));
            let h = hash(x, y, 101);
            if h > 0.985 && y < 44 {
                let loops = 1 + (hash(x, y, 102) * 3.0) as i32;
                let tw = 0.45 + 0.55 * dsin(2.0 * PI * (t * loops as f32 + hash(x, y, 103)) as f64);
                cr += 0.7 * tw;
                cg += 0.7 * tw;
                cb += 0.75 * tw;
            }
            for l in 0..3 {
                let lf = l as f32;
                let cx = x as f32 / FW as f32;
                let cy = 14.0 + lf * 8.0
                    + 4.5 * dsin(2.0 * PI * (cx + t + lf * 0.3) as f64)
                    + 2.0 * dsin(2.0 * PI * (cx * 2.0 - 2.0 * t + lf * 0.7) as f64);
                let dy = y as f32 - cy;
                let mut k = if dy < 0.0 {
                    ((dy / (7.0 + lf * 2.0)) as f64).exp() as f32
                } else {
                    ((-sq(dy / 1.8)) as f64).exp() as f32
                };
                let rays = 0.55 + 0.45 * dsin((x as f32 * 0.9) as f64 + 2.0 * PI * t as f64 + (lf * 2.1) as f64);
                k *= rays * (0.75 - lf * 0.12);
                let hue = 0.36 + 0.14 * lf / 2.0
                    + 0.1 * dsin(2.0 * PI * ((t + lf / 3.0) as f64))
                    + if dy < 0.0 { -dy * 0.006 } else { 0.0 };
                let col = hsv(hue, 0.75, 1.0);
                cr += col[0] * k;
                cg += col[1] * k;
                cb += col[2] * k;
            }
            if y as f32 >= tree_line(x) {
                cr = 0.008;
                cg = 0.015;
                cb = 0.03;
            }
            c.put(i, cr, cg, cb);
        }
    }
}

fn galaxy(t: f32, c: &mut Canvas) {
    let ox = 1.6 * (2.0 * PI * t as f64).cos() as f32;
    let oy = 1.6 * dsin(2.0 * PI * t as f64);
    for y in 0..FHI {
        for x in 0..FWI {
            let i = (y * FWI + x) as usize;
            let (xf, yf) = (x as f32, y as f32);
            let (mut cr, mut cg, mut cb) = (0.02f32, 0.012f32, 0.06f32);
            let n = fbm(xf * 0.07 + ox, yf * 0.055 + oy, 21);
            let m = fbm(xf * 0.05 - oy, yf * 0.05 + ox, 22);
            let k = smooth(0.42, 0.82, n);
            let (pr, pg, pb) = (lerp(0.36, 0.86, m), lerp(0.14, 0.22, m), lerp(0.66, 0.58, m));
            cr += pr * k * 0.85;
            cg += pg * k * 0.85;
            cb += pb * k * 0.85;
            let core = smooth(0.72, 0.95, n);
            cr += core * 0.35;
            cg += core * 0.25;
            cb += core * 0.35;
            if hash(x, y, 201) > 0.978 {
                let loops = 1 + (hash(x, y, 202) * 3.0) as i32;
                let tw = 0.35 + 0.65 * dsin(2.0 * PI * (t * loops as f32 + hash(x, y, 203)) as f64).max(0.0);
                cr += 0.8 * tw;
                cg += 0.8 * tw;
                cb += 0.9 * tw;
            }
            c.put(i, cr, cg, cb);
        }
    }
    let big = [[7, 9], [30, 22], [14, 44], [33, 55]];
    for (k, p) in big.iter().enumerate() {
        let tw = 0.5 + 0.5 * dsin(2.0 * PI * ((t * (1 + k % 2) as f32) as f64 + k as f64 * 0.29));
        c.sparkle(p[0], p[1], 2, 0.9 * tw, 0.9, 0.9, 1.0);
    }
    if t > 0.55 && t < 0.75 {
        let f = (t - 0.55) / 0.2;
        let (hx, hy) = (lerp(36.0, 4.0, f), lerp(6.0, 30.0, f));
        for s in 0..12 {
            let a = (1.0 - s as f32 / 12.0) * dsin(PI * f as f64);
            c.add(jround(hx + s as f32 * 1.33), jround(hy - s as f32), 1.0, 0.95, 0.85, a);
        }
    }
}

fn circuit(t: f32, c: &mut Canvas, pix: &[(i32, i32, usize, f32)], path_len: &[i32]) {
    for y in 0..FHI {
        for x in 0..FWI {
            let i = (y * FWI + x) as usize;
            let v = y as f32 / FH as f32;
            let (mut cr, mut cg, mut cb) = (lerp(0.03, 0.015, v), lerp(0.11, 0.06, v), lerp(0.08, 0.045, v));
            if x % S as i32 == 2 && y % S as i32 == 2 {
                cr += 0.03;
                cg += 0.08;
                cb += 0.06;
            }
            c.put(i, cr, cg, cb);
        }
    }
    for &(x, y, p, s) in pix {
        let l = path_len[p] as f32;
        let loops = 1 + p % 2;
        let head = frac(t * loops as f32 + p as f32 * 0.37) * (l + 14.0);
        let dd = head - s;
        let mut bri = if (0.0..14.0).contains(&dd) { 1.0 - dd / 14.0 } else { 0.0 };
        bri *= bri;
        c.set(x, y, lerp(0.08, 0.85, bri), lerp(0.34, 1.0, bri), lerp(0.25, 0.92, bri));
        if bri > 0.3 {
            let a = (bri - 0.3) * 0.8;
            c.add(x + 1, y, 0.25, 0.95, 0.75, a);
            c.add(x - 1, y, 0.25, 0.95, 0.75, a);
            c.add(x, y + 1, 0.25, 0.95, 0.75, a);
            c.add(x, y - 1, 0.25, 0.95, 0.75, a);
        }
    }
    for y in 26..38 {
        for x in 12..28 {
            let edge = x == 12 || x == 27 || y == 26 || y == 37;
            c.set(x, y, if edge { 0.14 } else { 0.03 }, if edge { 0.55 } else { 0.07 }, if edge { 0.4 } else { 0.06 });
        }
    }
    let glow = 0.5 + 0.5 * dsin(2.0 * PI * t as f64);
    for k in 0..4 {
        for d in 1..=2 {
            c.set(14 + k * 4, 26 - d, 0.6, 0.75, 0.7);
            c.set(14 + k * 4, 37 + d, 0.6, 0.75, 0.7);
        }
    }
    for y in 30..34 {
        for x in 18..22 {
            c.set(x, y, lerp(0.1, 0.6, glow), lerp(0.4, 1.0, glow), lerp(0.3, 0.85, glow));
        }
    }
    for p in 0..path_len.len() {
        if let Some(&(x, y, _, _)) = pix.iter().find(|q| q.2 == p) {
            for dy in -1..=1 {
                for dx in -1..=1 {
                    c.set(x + dx, y + dy, 0.2, 0.66, 0.5);
                }
            }
        }
    }
}

/// 1 in the first half of a repeat, 0 in the second, with a one-pixel soft edge.
fn edge(v: f32, half: f32, period: f32) -> f32 {
    if v < 0.5 {
        return 0.5 + v;
    }
    if v < half - 0.5 {
        return 1.0;
    }
    if v < half + 0.5 {
        return half + 0.5 - v;
    }
    if v > period - 0.5 {
        return v - (period - 0.5);
    }
    0.0
}

impl Custom {
    /// How much of colour 2 a pixel gets (0..1).
    fn pattern(&self, x: i32, y: i32, t: f32) -> f32 {
        let flow = self.mot == 2;
        let o16 = if flow { t * 16.0 } else { 0.0 };
        let (xf, yf) = (x as f32, y as f32);
        match self.pat {
            1 => {
                if flow {
                    0.5 - 0.5 * (2.0 * PI * ((yf / FH as f32 - t) as f64)).cos() as f32
                } else {
                    yf / (FH - 1) as f32
                }
            }
            2 => edge((xf + if flow { t * 10.0 } else { 0.0 }) % 10.0, 5.0, 10.0),
            3 => edge((yf + (xf - FW as f32 / 2.0 + 0.5).abs() * 0.9 - o16 + 64.0) % 16.0, 8.0, 16.0),
            4 => {
                let (cx, cy) = (x / 8, ((yf + o16) / 8.0).floor() as i32);
                if (cx + cy) & 1 == 0 { 0.0 } else { 1.0 }
            }
            5 => edge((yf + 3.0 * dsin(2.0 * PI * xf as f64 / 20.0) + o16 + 64.0) % 16.0, 8.0, 16.0),
            7 => {
                if xf * 1.25 + yf > FH as f32 * 0.72 + FW as f32 * 0.4 { 1.0 } else { 0.0 }
            }
            _ => 0.0,
        }
    }

    fn frame(&self, t: f32, c: &mut Canvas) {
        let pulse = if self.mot == 3 { 0.9 + 0.18 * dsin(2.0 * PI * 2.0 * t as f64) } else { 1.0 };
        for y in 0..FHI {
            let v = y as f32 / FH as f32;
            let shade = (1.06 - 0.22 * v) * pulse;
            for x in 0..FWI {
                let i = (y * FWI + x) as usize;
                let k = self.pattern(x, y, t);
                let (cr, cg, cb) = (lerp(self.r1, self.r2, k), lerp(self.g1, self.g2, k), lerp(self.b1, self.b2, k));
                let n = 0.94 + 0.06 * hash(x >> 1, y >> 1, 9);
                c.put(i, cr * shade * n, cg * shade * n, cb * shade * n);
            }
        }
        if self.pat == 6 {
            for k in 0..14 {
                let x = 3.0 + hash(k, 1, 51) * (FW - 6) as f32;
                let y0 = hash(k, 2, 51);
                let y = if self.mot == 2 { frac(y0 + t) } else { y0 } * (FH as f32 + 6.0) - 3.0;
                let arm = if k % 3 == 0 { 2 } else { 1 };
                c.sparkle(jround(x), jround(y), arm, 1.0, self.r2, self.g2, self.b2);
                c.set(jround(x), jround(y), self.r2, self.g2, self.b2);
            }
        }
        if self.emb > 0 {
            self.badge(c);
        }
        if self.mot == 1 {
            for y in 0..FHI {
                for x in 0..FWI {
                    let s = (x as f32 * 0.55 + y as f32) / (FH as f32 + FW as f32 * 0.55);
                    let sh = ((-sq((s - (t * 1.7 - 0.35)) / 0.05)) as f64).exp() as f32 * 0.3;
                    let i = (y * FWI + x) as usize;
                    c.r[i] += sh;
                    c.g[i] += sh;
                    c.b[i] += sh;
                }
            }
        } else if self.mot == 4 {
            for k in 0..9 {
                let mut tw = dsin(2.0 * PI * (t as f64 * 2.0 + k as f64 * 0.37)).max(0.0);
                tw = tw * tw * tw;
                let x = 3 + (hash(k, 7, 52) * (FW - 6) as f32) as i32;
                let y = 3 + (hash(k, 8, 52) * (FH - 6) as f32) as i32;
                c.sparkle(x, y, 2, tw, 1.0, 1.0, 1.0);
            }
        }
    }

    /// The emblem on a round badge in the middle (readable on any pattern).
    fn badge(&self, c: &mut Canvas) {
        let (cx, cy) = (FW as f32 / 2.0 - 0.5, FH as f32 * 0.42);
        let (br, bg, bb) = (self.r1 * 0.4, self.g1 * 0.4, self.b1 * 0.4);
        let (mut er, mut eg, mut eb) = (self.r2, self.g2, self.b2);
        let le = self.r2 * 0.3 + self.g2 * 0.59 + self.b2 * 0.11;
        let lb = br * 0.3 + bg * 0.59 + bb * 0.11;
        if le - lb < 0.3 {
            (er, eg, eb) = (0.95, 0.95, 0.92);
        }
        for y in 0..FHI {
            for x in 0..FWI {
                let d = (sq(x as f32 - cx) + sq(y as f32 - cy)).sqrt();
                if d > 14.5 {
                    continue;
                }
                let i = (y * FWI + x) as usize;
                if d > 13.0 {
                    c.put(i, er * 0.85, eg * 0.85, eb * 0.85);
                } else {
                    let (r, g, b) = (c.r[i], c.g[i], c.b[i]);
                    c.put(i, lerp(r, br, 0.86), lerp(g, bg, 0.86), lerp(b, bb, 0.86));
                }
            }
        }
        let bmp = EMBLEM_BITMAPS[self.emb - 1];
        let (ox, oy) = (jround(cx - 10.5), jround(cy - 10.5));
        for (by, row) in bmp.iter().enumerate() {
            for (bx, ch) in row.bytes().enumerate() {
                if ch != b'#' {
                    continue;
                }
                let lit = 1.08 - by as f32 * 0.03;
                for sy in 0..2 {
                    for sx in 0..2 {
                        c.set(ox + bx as i32 * 2 + sx, oy + by as i32 * 2 + sy, er * lit, eg * lit, eb * lit);
                    }
                }
            }
        }
    }
}

/// The emblems, 11x11 each (drawn twice the size).
const EMBLEM_BITMAPS: [[&str; 11]; 7] = [
    [".....#.....", ".....#.....", "....###....", "....###....", "###########", ".#########.", "..#######..", "...#####...", "...##.##...", "..##...##..", "..#.....#.."],
    ["...........", ".###...###.", "###########", "###########", "###########", ".#########.", "..#######..", "...#####...", "....###....", ".....#.....", "..........."],
    [".....#.....", "....###....", "...#####...", "..#######..", ".#########.", "###########", ".#########.", "..#######..", "...#####...", "....###....", ".....#....."],
    ["......###..", ".....###...", "....###....", "...###.....", "..########.", ".......###.", "......###..", ".....###...", "....###....", "...##......", "..#........"],
    ["....####...", "..#####....", ".#####.....", ".####......", "#####......", "#####......", "#####......", ".####......", ".#####.....", "..#####....", "....####..."],
    ["...........", "#....#....#", "##...#...##", "##..###..##", "###.###.###", "###########", "###########", "###########", "...........", "###########", "..........."],
    [".........##", "........###", ".......###.", "......###..", ".....###...", "#...###....", "##.###.....", ".####......", "..##.......", ".#..#......", "#....#....."],
];
