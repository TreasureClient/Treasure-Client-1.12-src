//! Treasure Client cosmetics: the shapes of auras and boot trails (a port of `TCAuras` and the
//! trail part of `TCWear` in the JS and WASM editions).
//!
//! An aura is a few dozen soft dots and thin rings, worked out from the clock (nothing to update
//! each tick). A boot trail is a short list of footprints, puffs, flames or sparks in world
//! positions, added as the player walks, jumps and lands. Both come out as glowing quads that
//! are added on top of the picture.

use super::Look;

/// One glowing quad: corners (blocks, from the player's feet, y up), texture corners on the
/// soft dot picture, its colour, and its strength at each corner.
#[derive(Clone, Copy, Debug)]
pub struct Glow {
    pub corners: [[f32; 3]; 4],
    pub uv: [[f32; 2]; 4],
    pub rgb: [f32; 3],
    pub alpha: [f32; 4],
}

/// The soft dot picture: white in the middle, see-through towards the edge (0..1 per pixel).
pub fn dot_alpha(n: usize) -> Vec<f32> {
    let mut out = Vec::with_capacity(n * n);
    for y in 0..n {
        for x in 0..n {
            let dx = (x as f32 + 0.5) / n as f32 * 2.0 - 1.0;
            let dy = (y as f32 + 0.5) / n as f32 * 2.0 - 1.0;
            let d2 = dx * dx + dy * dy;
            // a bright little centre in a soft glow, reaching zero at the edge
            let mut a = (-d2 * 9.0).exp() * 0.75 + (-d2 * 40.0).exp() * 0.25;
            a *= (1.0 - d2).max(0.0);
            out.push(((a * 255.0 + 0.5) as i32).min(255) as f32 / 255.0);
        }
    }
    out
}

struct Sink<'a> {
    out: &'a mut Vec<Glow>,
    /// the camera's right and up directions (dots always face you)
    right: [f32; 3],
    up: [f32; 3],
    scale: f32,
    /// added to every corner (trail pieces are drawn from the player's feet)
    shift: [f32; 3],
    // the aura colour, and the colour of the bright centres (towards white)
    c: [f32; 3],
    w: [f32; 3],
}

impl Sink<'_> {
    fn color(&mut self, rgb: u32) {
        let c = [
            ((rgb >> 16) & 255) as f32 / 255.0,
            ((rgb >> 8) & 255) as f32 / 255.0,
            (rgb & 255) as f32 / 255.0,
        ];
        self.c = c;
        self.w = c.map(|v| v + (1.0 - v) * 0.55);
    }

    fn push(&mut self, pts: [[f32; 3]; 4], uv: [[f32; 2]; 4], rgb: [f32; 3], alpha: [f32; 4]) {
        let (s, sh) = (self.scale, self.shift);
        self.out.push(Glow {
            corners: pts.map(|p| [p[0] * s + sh[0], p[1] * s + sh[1], p[2] * s + sh[2]]),
            uv,
            rgb,
            alpha,
        });
    }

    /// A soft dot facing the camera.
    fn dot(&mut self, x: f32, y: f32, z: f32, s: f32, rgb: [f32; 3], a: f32) {
        if a <= 0.004 {
            return;
        }
        let (r, u) = (self.right, self.up);
        let a1 = [(r[0] + u[0]) * s, (r[1] + u[1]) * s, (r[2] + u[2]) * s];
        let b1 = [(r[0] - u[0]) * s, (r[1] - u[1]) * s, (r[2] - u[2]) * s];
        self.push(
            [
                [x - a1[0], y - a1[1], z - a1[2]],
                [x - b1[0], y - b1[1], z - b1[2]],
                [x + a1[0], y + a1[1], z + a1[2]],
                [x + b1[0], y + b1[1], z + b1[2]],
            ],
            [[0.0, 1.0], [0.0, 0.0], [1.0, 0.0], [1.0, 1.0]],
            rgb,
            [a; 4],
        );
    }

    /// A soft round glow lying flat at a point.
    fn flat_at(&mut self, x: f32, y: f32, z: f32, s: f32, rgb: [f32; 3], a: f32) {
        if a <= 0.004 {
            return;
        }
        self.push(
            [[x - s, y, z - s], [x - s, y, z + s], [x + s, y, z + s], [x + s, y, z - s]],
            [[0.0, 0.0], [0.0, 1.0], [1.0, 1.0], [1.0, 0.0]],
            rgb,
            [a; 4],
        );
    }

    /// A soft round glow lying flat (on the ground).
    fn flat(&mut self, y: f32, s: f32, rgb: [f32; 3], a: f32) {
        self.flat_at(0.0, y, 0.0, s, rgb, a);
    }

    /// A thin flat ring with soft edges. Its brightness is base + peak * (closeness to the angle
    /// "phase") ^ sharp, so part of it can glow brighter (sharp = 0: even all round).
    #[allow(clippy::too_many_arguments)]
    fn ring(&mut self, y: f32, rad: f32, half_w: f32, segs: usize, rgb: [f32; 3], base: f32, peak: f32, phase: f32, sharp: u32) {
        let (ri, ro) = ((rad - half_w).max(0.0), rad + half_w);
        let (mut pc, mut ps, mut pa) = (1.0f32, 0.0f32, ring_alpha(0.0, base, peak, phase, sharp));
        for i in 1..=segs {
            let ang = i as f32 * std::f32::consts::TAU / segs as f32;
            let (c, s, a) = (ang.cos(), ang.sin(), ring_alpha(ang, base, peak, phase, sharp));
            if pa > 0.004 || a > 0.004 {
                self.push(
                    [[pc * ri, y, ps * ri], [pc * ro, y, ps * ro], [c * ro, y, s * ro], [c * ri, y, s * ri]],
                    [[0.5, 0.0], [0.5, 1.0], [0.5, 1.0], [0.5, 0.0]],
                    rgb,
                    [pa, pa, a, a],
                );
            }
            pc = c;
            ps = s;
            pa = a;
        }
    }
}

fn ring_alpha(ang: f32, base: f32, peak: f32, phase: f32, sharp: u32) -> f32 {
    if peak <= 0.0 {
        return base;
    }
    let k = 0.5 + 0.5 * (ang - phase).cos();
    let mut p = k;
    for _ in 1..sharp {
        p *= k;
    }
    base + peak * p
}

fn sin(v: f32) -> f32 {
    (v as f64).sin() as f32
}
fn cos(v: f32) -> f32 {
    (v as f64).cos() as f32
}
fn frac(v: f32) -> f32 {
    v - v.floor()
}
fn h(i: i32, k: i32) -> f32 {
    super::capeart::hash(i, k, 404)
}

const PI: f32 = std::f32::consts::PI;

/// Adds a player's aura. `seed` keeps two players' auras from moving in step.
pub fn aura(look: &Look, ms: u64, fade: f32, seed: f32, right: [f32; 3], up: [f32; 3], out: &mut Vec<Glow>) {
    if !look.has_aura() {
        return;
    }
    let mut k = Sink { out, right, up, scale: look.aura_scale(), shift: [0.0; 3], c: [1.0; 3], w: [1.0; 3] };
    let t = (ms % 3_600_000) as f32 / 1000.0 * look.aura_speed_mul();
    k.color(look.aura_rgb(ms, 0.0));
    match look.aura.as_str() {
        "halo" => halo(&mut k, t, fade, seed),
        "orbit" => orbit(&mut k, look, ms, t, fade, seed),
        "rising" => rising(&mut k, t, fade, seed),
        "ripple" => ripple(&mut k, t, fade),
        "helix" => helix(&mut k, look, ms, t, fade, seed),
        "custom" => custom(&mut k, look, ms, t, fade, seed),
        _ => {}
    }
}

/// A thin glowing ring round your waist, with a bright arc running round it.
fn halo(k: &mut Sink, t: f32, fade: f32, seed: f32) {
    let y = 1.0 + 0.035 * sin(t * 1.3);
    let (rad, ph) = (0.62, t * 1.25 + seed);
    let (c, w) = (k.c, k.w);
    k.ring(y, rad, 0.13, 48, c, 0.14 * fade, 0.0, 0.0, 0);
    k.ring(y, rad, 0.026, 48, w, 0.3 * fade, 0.7 * fade, ph, 6);
    k.ring(y, rad, 0.026, 48, c, 0.0, 0.4 * fade, ph + PI, 10);
    let (hx, hz) = (rad * cos(ph), rad * sin(ph));
    k.dot(hx, y, hz, 0.2, c, 0.35 * fade);
    k.dot(hx, y, hz, 0.055, [1.0; 3], 0.9 * fade);
}

/// Three small lights circling at different heights, with short fading trails.
fn orbit(k: &mut Sink, look: &Look, ms: u64, t: f32, fade: f32, seed: f32) {
    let hs = [0.45, 1.05, 1.7];
    let rs = [0.78, 0.7, 0.55];
    let ws = [1.7f32, -1.3, 2.1];
    for i in 0..3 {
        if look.color == "rainbow" {
            k.color(look.aura_rgb(ms, i as f32 * 0.33));
        }
        let ang = t * ws[i] + i as f32 * 2.1 + seed;
        let yk = hs[i] + 0.07 * sin(t * 1.7 + i as f32);
        let dir = if ws[i] > 0.0 { 1.0 } else { -1.0 };
        for s in (1..=10).rev() {
            let a = ang - dir * s as f32 * 0.075;
            let k2 = 1.0 - s as f32 / 11.0;
            k.dot(rs[i] * cos(a), yk, rs[i] * sin(a), 0.065 * (1.0 - s as f32 / 15.0), k.c, 0.8 * k2 * k2 * fade);
        }
        let (x, z) = (rs[i] * cos(ang), rs[i] * sin(ang));
        k.dot(x, yk, z, 0.22, k.c, 0.32 * fade);
        k.dot(x, yk, z, 0.05, k.w, 0.95 * fade);
    }
}

/// Soft dots floating gently up from your feet.
fn rising(k: &mut Sink, t: f32, fade: f32, seed: f32) {
    k.flat(0.02, 0.75, k.c, 0.14 * fade);
    rising_dots(k, 18, t, fade, seed);
}

fn rising_dots(k: &mut Sink, n: i32, t: f32, fade: f32, seed: f32) {
    for i in 0..n {
        let per = 2.4 + h(i, 1) * 1.6;
        let f = frac(t / per + h(i, 2));
        let yy = f * 2.1;
        let ang = h(i, 3) * 6.2832 + f * 1.2 + seed;
        let rr = 0.3 + 0.38 * h(i, 4);
        let a = sin(PI * f).max(0.0).powf(1.5) * 0.85 * fade;
        let size = 0.03 + 0.03 * h(i, 5);
        let (x, z) = (rr * cos(ang), rr * sin(ang));
        k.dot(x, yy, z, size * 3.2, k.c, a * 0.28);
        k.dot(x, yy, z, size, k.w, a);
    }
}

/// A soft circle spreading out on the ground, again and again.
fn ripple(k: &mut Sink, t: f32, fade: f32) {
    k.flat(0.02, 0.5, k.c, (0.13 + 0.06 * sin(t * 2.6)) * fade);
    for i in 0..3 {
        let f = frac(t / 2.4 + i as f32 / 3.0);
        let e = 1.0 - (1.0 - f) * (1.0 - f);
        let rad = 0.18 + 1.15 * e;
        let a = (1.0 - f).powf(1.4) * fade;
        let (c, w) = (k.c, k.w);
        k.ring(0.03, rad, 0.11, 56, c, 0.28 * a, 0.0, 0.0, 0);
        k.ring(0.03, rad, 0.028, 56, w, 0.85 * a, 0.0, 0.0, 0);
    }
}

/// Two thin strands spiralling round you, with a light running up each one.
fn helix(k: &mut Sink, look: &Look, ms: u64, t: f32, fade: f32, seed: f32) {
    helix_strands(k, 22, true, |k, s| {
        if look.color == "rainbow" {
            k.color(look.aura_rgb(ms, s as f32 * 0.5));
        }
    }, t, fade, seed);
}

fn helix_strands(k: &mut Sink, n: i32, halos: bool, mut recolor: impl FnMut(&mut Sink, i32), t: f32, fade: f32, seed: f32) {
    for s in 0..2 {
        recolor(k, s);
        let hl = frac(t * 0.45 + s as f32 * 0.5) * 2.3 - 0.2;
        for j in 0..n {
            let yy = 0.08 + j as f32 * (1.85 / (n - 1) as f32);
            let ang = yy * PI * 1.7 + t * 1.5 + s as f32 * PI + seed;
            let rr = 0.6 - 0.08 * (yy / 1.9);
            let boost = (-((yy - hl) / 0.18).powi(2)).exp();
            let ends = sin(PI * (j as f32 + 0.5) / n as f32).max(0.0).powf(0.7);
            let a = (0.32 + 0.68 * boost) * ends * fade;
            let (x, z) = (rr * cos(ang), rr * sin(ang));
            if halos && boost > 0.25 {
                k.dot(x, yy, z, 0.18, k.c, 0.3 * boost * fade);
            }
            let rgb = if boost > 0.5 { k.w } else { k.c };
            k.dot(x, yy, z, 0.032 + 0.03 * boost, rgb, a);
        }
    }
}

/// An aura put together in the Maker: a base in colour 1, particles in colour 2, at a height.
fn custom(k: &mut Sink, look: &Look, ms: u64, t: f32, fade: f32, seed: f32) {
    let p = look.ak_parts();
    let part = |i: usize| p.get(i).copied().unwrap_or("");
    let hgt = match part(4) {
        "low" => 0.3,
        "head" => 2.05,
        _ => 1.0,
    };
    k.color(look.color_rgb(part(2), ms, 0.0));
    let (c, w) = (k.c, k.w);
    match part(0) {
        "ring" => {
            let (y, rad, ph) = (hgt + 0.035 * sin(t * 1.3), 0.62, t * 1.25 + seed);
            k.ring(y, rad, 0.13, 48, c, 0.14 * fade, 0.0, 0.0, 0);
            k.ring(y, rad, 0.026, 48, w, 0.3 * fade, 0.7 * fade, ph, 6);
        }
        "glow" => {
            k.flat(0.02, 0.8, c, (0.16 + 0.05 * sin(t * 2.0)) * fade);
            k.flat(0.025, 0.35, w, 0.12 * fade);
        }
        "ripple" => ripple(k, t, fade),
        "double" => {
            for i in 0..2 {
                let y = hgt + if i == 0 { -0.22 } else { 0.22 };
                let ph = if i == 0 { 1.0 } else { -1.0 } * t * 1.4 + seed + i as f32;
                k.ring(y, 0.6, 0.11, 44, c, 0.11 * fade, 0.0, 0.0, 0);
                k.ring(y, 0.6, 0.024, 44, w, 0.25 * fade, 0.65 * fade, ph, 6);
            }
        }
        _ => {}
    }
    let c2 = part(3);
    k.color(look.color_rgb(c2, ms, 0.5));
    match part(1) {
        "orbit" => {
            let dh = [-0.45, 0.0, 0.45];
            let ws = [1.7f32, -1.3, 2.1];
            for i in 0..3 {
                if c2 == "rainbow" {
                    k.color(look.color_rgb(c2, ms, i as f32 * 0.33));
                }
                let ang = t * ws[i] + i as f32 * 2.1 + seed;
                let yk = (hgt + dh[i]).max(0.1) + 0.07 * sin(t * 1.7 + i as f32);
                let (r, dir) = (0.72, if ws[i] > 0.0 { 1.0 } else { -1.0 });
                for s in (1..=8).rev() {
                    let a = ang - dir * s as f32 * 0.08;
                    let k2 = 1.0 - s as f32 / 9.0;
                    k.dot(r * cos(a), yk, r * sin(a), 0.06 * (1.0 - s as f32 / 14.0), k.c, 0.75 * k2 * k2 * fade);
                }
                k.dot(r * cos(ang), yk, r * sin(ang), 0.2, k.c, 0.3 * fade);
                k.dot(r * cos(ang), yk, r * sin(ang), 0.05, k.w, 0.95 * fade);
            }
        }
        "rising" => rising_dots(k, 16, t, fade, seed),
        "helix" => helix_strands(k, 20, false, |k, s| {
            if c2 == "rainbow" {
                k.color(look.color_rgb(c2, ms, s as f32 * 0.5));
            }
        }, t, fade, seed),
        "sparkle" => {
            for i in 0..16 {
                let mut tw = sin(t * (1.6 + h(i, 6)) * 2.0 + h(i, 7) * 6.28).max(0.0);
                tw = tw * tw * tw;
                let ang = h(i, 8) * 6.2832 + seed;
                let (rr, y) = (0.4 + 0.35 * h(i, 9), 0.1 + h(i, 10) * 2.0);
                let (x, z) = (rr * cos(ang), rr * sin(ang));
                k.dot(x, y, z, 0.13, k.c, 0.25 * tw * fade);
                k.dot(x, y, z, 0.04, [1.0; 3], 0.9 * tw * fade);
            }
        }
        "snow" => {
            for i in 0..16 {
                let per = 3.2 + h(i, 11) * 1.8;
                let f = frac(t / per + h(i, 12));
                let ang = h(i, 13) * 6.2832 + seed;
                let rr = 0.25 + 0.45 * h(i, 14);
                let x = rr * cos(ang) + 0.08 * sin(t * 1.3 + i as f32);
                let z = rr * sin(ang) + 0.08 * cos(t * 1.1 + i as f32);
                let y = 2.4 - f * 2.4;
                let a = sin(PI * f).max(0.0).powf(0.8) * 0.8 * fade;
                k.dot(x, y, z, 0.1, k.c, a * 0.25);
                k.dot(x, y, z, 0.035, k.w, a);
            }
        }
        _ => {}
    }
}

// ------------------------------------------------------------------ boot trails

const PRINT_FROST: u8 = 1;
const PRINT_MAGMA: u8 = 2;
const PUFF: u8 = 3;
const FLAME: u8 = 4;
const SPARK: u8 = 5;
const TRAIL_N: usize = 48;

/// The footprints and puffs a player has left behind (world positions).
pub struct Trail {
    pos: [[f64; 3]; TRAIL_N],
    born: [u64; TRAIL_N],
    kind: [u8; TRAIL_N],
    next: usize,
    side: f64,
    /// how far they've walked (like Minecraft's distanceWalkedModified: 0.6 per block)
    walk: f64,
    last_walk: f64,
    last_pos: Option<[f64; 3]>,
    last_ground: bool,
    last_emit: u64,
}

impl Default for Trail {
    fn default() -> Self {
        Trail {
            pos: [[0.0; 3]; TRAIL_N],
            born: [0; TRAIL_N],
            kind: [0; TRAIL_N],
            next: 0,
            side: 1.0,
            walk: 0.0,
            last_walk: 0.0,
            last_pos: None,
            last_ground: true,
            last_emit: 0,
        }
    }
}

impl Trail {
    fn add(&mut self, p: [f64; 3], kind: u8, now: u64) {
        self.pos[self.next] = p;
        self.born[self.next] = now.max(1);
        self.kind[self.next] = kind;
        self.next = (self.next + 1) % TRAIL_N;
    }

    pub fn is_empty(&self, now: u64) -> bool {
        self.born.iter().all(|&b| b == 0 || now.saturating_sub(b) > 3000)
    }

    /// Adds new footprints as the player moves. `feet` is where their feet are now (world),
    /// `body_yaw` the way their body faces (degrees).
    pub fn update(&mut self, look: &Look, feet: [f64; 3], body_yaw: f32, ground: bool, sprinting: bool, now: u64) {
        if let Some(last) = self.last_pos {
            let (dx, dz) = (feet[0] - last[0], feet[2] - last[2]);
            let d = (dx * dx + dz * dz).sqrt();
            if d < 4.0 {
                self.walk += d * 0.6;
            } else {
                self.last_walk = self.walk; // a teleport: no footprints all the way there
            }
        }
        self.last_pos = Some(feet);
        let yaw = (body_yaw as f64).to_radians();
        let (px, pz) = (yaw.cos(), yaw.sin()); // to the side
        let (fx, fz) = (-yaw.sin(), yaw.cos()); // forwards
        let [x, y, z] = feet;
        match look.trail_kind().unwrap_or("") {
            kind @ ("frost" | "magma" | "prints") => {
                if ground && self.walk - self.last_walk >= 0.62 {
                    self.last_walk = self.walk;
                    self.side = -self.side;
                    let s = self.side;
                    let k = if kind == "magma" { PRINT_MAGMA } else { PRINT_FROST };
                    self.add([x + px * 0.14 * s, y + 0.016, z + pz * 0.14 * s], k, now);
                }
            }
            "puffs" => {
                if ground != self.last_ground {
                    for i in 0..3u64 {
                        let a = i as f64 * 2.1 + now as f64 * 0.001;
                        self.add([x + a.cos() * 0.22, y + 0.08, z + a.sin() * 0.22], PUFF, now.saturating_sub(i * 60));
                    }
                }
            }
            "flames" => {
                if (!ground || sprinting) && now.saturating_sub(self.last_emit) >= 45 {
                    self.last_emit = now;
                    for s in [-1.0, 1.0] {
                        self.add([x + px * 0.12 * s - fx * 0.14, y - 0.02, z + pz * 0.12 * s - fz * 0.14], FLAME, now);
                    }
                }
            }
            "sparks" => {
                if !ground && now.saturating_sub(self.last_emit) >= 70 {
                    self.last_emit = now;
                    self.side = -self.side;
                    let s = self.side;
                    self.add([x + px * 0.3 * s - fx * 0.1, y + 0.5, z + pz * 0.3 * s - fz * 0.1], SPARK, now);
                }
            }
            _ => {}
        }
        if self.walk < self.last_walk {
            self.last_walk = self.walk;
        }
        self.last_ground = ground;
    }

    /// Adds the trail's glows, drawn from `feet` (world).
    pub fn draw(&self, look: &Look, feet: [f64; 3], fade: f32, now: u64, right: [f32; 3], up: [f32; 3], out: &mut Vec<Glow>) {
        let tint = look.trail_tint(now);
        let mut k = Sink { out, right, up, scale: 1.0, shift: [0.0; 3], c: [1.0; 3], w: [1.0; 3] };
        let rgb = |c: u32| [((c >> 16) & 255) as f32 / 255.0, ((c >> 8) & 255) as f32 / 255.0, (c & 255) as f32 / 255.0];
        for i in 0..TRAIL_N {
            if self.born[i] == 0 {
                continue;
            }
            let age = now.saturating_sub(self.born[i]) as f32 / 1000.0;
            let p = self.pos[i];
            let (x, y, z) = ((p[0] - feet[0]) as f32, (p[1] - feet[1]) as f32, (p[2] - feet[2]) as f32);
            match self.kind[i] {
                PRINT_FROST if age <= 3.0 => {
                    let a = (1.0 - age / 3.0).powf(1.2) * fade;
                    k.flat_at(x, y, z, 0.17, rgb(tint.unwrap_or(0x9FDFFF)), 0.55 * a);
                    k.flat_at(x, y + 0.002, z, 0.07, [1.0; 3], 0.5 * a);
                }
                PRINT_MAGMA if age <= 2.2 => {
                    let f = 1.0 - age / 2.2;
                    let a = f * fade;
                    k.flat_at(x, y, z, 0.18, rgb(tint.unwrap_or(0xFF6A1A)), 0.6 * a);
                    k.dot(x, y + 0.05 + age * 0.35, z, 0.035, [1.0, 0.7, 0.3], 0.9 * a * f);
                }
                PUFF if age <= 1.1 => {
                    let f = age / 1.1;
                    k.dot(x, y + f * 0.15, z, 0.12 + 0.25 * f, [1.0; 3], 0.32 * (1.0 - f) * fade);
                }
                FLAME if age <= 0.45 => {
                    let f = age / 0.45;
                    k.dot(x, y - age * 0.25, z, 0.03 + 0.09 * (1.0 - f), [1.0, 0.85 - 0.55 * f, 0.35 - 0.3 * f], 0.85 * (1.0 - f) * fade);
                }
                SPARK if age <= 0.6 => {
                    let f = age / 0.6;
                    k.dot(x, y - age * 0.3, z, 0.045, [1.0, 1.0, 0.9], 0.9 * (1.0 - f) * fade);
                }
                _ => {}
            }
        }
    }
}
