//! Treasure Client cosmetics: the shapes of the hats and boots (all original designs). A copy of
//! the JS and WASM editions' `TCWearShapes`, so they look the same in every edition.
//!
//! Coordinates are model pixels with y pointing down, like the player model: the head is
//! x -4..4, y -8..0, z -4..4 (its top at y = -8, the face towards -z); a leg is x -2..2,
//! y 0..12, z -2..2 (the foot at y = 12).

use std::f32::consts::PI;

/// One face of a little box: four corners, which way it faces, its colour, and whether it glows
/// (glowing faces ignore the dark).
#[derive(Clone, Copy, Debug)]
pub struct Quad {
    pub corners: [[f32; 3]; 4],
    pub normal: [f32; 3],
    pub rgb: [f32; 3],
    pub glow: bool,
}

/// Collects the faces of the boxes a hat or boot is built from.
#[derive(Default)]
pub struct Shapes {
    pub quads: Vec<Quad>,
    turn: Option<([f32; 9], [f32; 3])>,
}

pub fn shade(rgb: u32, k: f32) -> u32 {
    let ch = |s: u32| ((((rgb >> s) & 255) as f32 * k) as u32).min(255);
    (ch(16) << 16) | (ch(8) << 8) | ch(0)
}

pub fn mix(a: u32, b: u32, k: f32) -> u32 {
    let ch = |s: u32| (((a >> s) & 255) as f32 * (1.0 - k) + ((b >> s) & 255) as f32 * k) as u32;
    (ch(16) << 16) | (ch(8) << 8) | ch(0)
}

fn sin(x: f32) -> f32 {
    x.sin()
}

pub fn hat_glows(id: &str) -> bool {
    matches!(id, "crown" | "wizard" | "angel" | "horns")
}

pub fn boots_glow(id: &str) -> bool {
    matches!(id, "rocket" | "frost" | "magma")
}

pub fn custom_hat_glows(top: &str, trim: &str) -> bool {
    matches!(top, "antenna" | "halo") || matches!(trim, "gems" | "stars")
}

pub fn custom_boots_glow(extra: &str) -> bool {
    matches!(extra, "crystals" | "cracks" | "nozzle")
}

/// A hat's own colour (used when its colour is "natural").
pub fn natural_hat(id: &str) -> u32 {
    match id {
        "crown" => 0xF2C14E,
        "tophat" => 0xB3263A,
        "wizard" => 0x3E2C86,
        "beanie" => 0xE04C4C,
        "catears" => 0x2E2E34,
        "horns" => 0x6E2222,
        "propeller" => 0xE8434F,
        "angel" => 0xFFE7A0,
        _ => 0xFFFFFF,
    }
}

/// A pair of boots' own colour (used when their colour is "natural").
pub fn natural_boots(id: &str) -> u32 {
    match id {
        "sneakers" => 0x4A8CFF,
        "rocket" => 0xFF7A3C,
        "winged" => 0xFFD76B,
        "cloud" => 0xF4F6FA,
        "frost" => 0x9FDFFF,
        "magma" => 0xFF6A1A,
        _ => 0xFFFFFF,
    }
}

impl Shapes {
    pub fn new() -> Shapes {
        Shapes::default()
    }

    // ------------------------------------------------------------------ building blocks

    /// Turns the next boxes round a pivot (degrees; applied as Ry * Rx * Rz).
    fn rot(&mut self, px: f32, py: f32, pz: f32, ax: f32, ay: f32, az: f32) {
        let (a, b, c) = (ax.to_radians(), ay.to_radians(), az.to_radians());
        let (ca, sa) = (a.cos(), a.sin());
        let (cb, sb) = (b.cos(), b.sin());
        let (cc, sc) = (c.cos(), c.sin());
        // Rx * Rz
        let (r00, r01, r02) = (cc, -sc, 0.0);
        let (r10, r11, r12) = (ca * sc, ca * cc, -sa);
        let (r20, r21, r22) = (sa * sc, sa * cc, ca);
        // Ry * (Rx * Rz)
        let m = [
            cb * r00 + sb * r20,
            cb * r01 + sb * r21,
            cb * r02 + sb * r22,
            r10,
            r11,
            r12,
            -sb * r00 + cb * r20,
            -sb * r01 + cb * r21,
            -sb * r02 + cb * r22,
        ];
        self.turn = Some((m, [px, py, pz]));
    }

    fn rot_reset(&mut self) {
        self.turn = None;
    }

    fn bx(&mut self, x1: f32, y1: f32, z1: f32, x2: f32, y2: f32, z2: f32, rgb: u32) {
        self.emit(x1, y1, z1, x2, y2, z2, rgb, false);
    }

    fn glow_box(&mut self, x1: f32, y1: f32, z1: f32, x2: f32, y2: f32, z2: f32, rgb: u32) {
        self.emit(x1, y1, z1, x2, y2, z2, rgb, true);
    }

    #[allow(clippy::too_many_arguments)]
    fn emit(&mut self, x1: f32, y1: f32, z1: f32, x2: f32, y2: f32, z2: f32, rgb: u32, glow: bool) {
        let (x1, x2) = (x1.min(x2), x1.max(x2));
        let (y1, y2) = (y1.min(y2), y1.max(y2));
        let (z1, z2) = (z1.min(z2), z1.max(z2));
        let c = [
            ((rgb >> 16) & 255) as f32 / 255.0,
            ((rgb >> 8) & 255) as f32 / 255.0,
            (rgb & 255) as f32 / 255.0,
        ];
        // top (y1, facing up = -y), bottom, front (-z), back, left (-x), right
        self.face([[x1, y1, z1], [x2, y1, z1], [x2, y1, z2], [x1, y1, z2]], [0.0, -1.0, 0.0], c, glow);
        self.face([[x1, y2, z2], [x2, y2, z2], [x2, y2, z1], [x1, y2, z1]], [0.0, 1.0, 0.0], c, glow);
        self.face([[x1, y2, z1], [x2, y2, z1], [x2, y1, z1], [x1, y1, z1]], [0.0, 0.0, -1.0], c, glow);
        self.face([[x2, y2, z2], [x1, y2, z2], [x1, y1, z2], [x2, y1, z2]], [0.0, 0.0, 1.0], c, glow);
        self.face([[x1, y2, z2], [x1, y2, z1], [x1, y1, z1], [x1, y1, z2]], [-1.0, 0.0, 0.0], c, glow);
        self.face([[x2, y2, z1], [x2, y2, z2], [x2, y1, z2], [x2, y1, z1]], [1.0, 0.0, 0.0], c, glow);
    }

    fn face(&mut self, mut corners: [[f32; 3]; 4], mut n: [f32; 3], rgb: [f32; 3], glow: bool) {
        if let Some((m, p)) = self.turn {
            let mul = |v: [f32; 3]| {
                [
                    m[0] * v[0] + m[1] * v[1] + m[2] * v[2],
                    m[3] * v[0] + m[4] * v[1] + m[5] * v[2],
                    m[6] * v[0] + m[7] * v[1] + m[8] * v[2],
                ]
            };
            n = mul(n);
            for c in &mut corners {
                let t = mul([c[0] - p[0], c[1] - p[1], c[2] - p[2]]);
                *c = [t[0] + p[0], t[1] + p[1], t[2] + p[2]];
            }
        }
        self.quads.push(Quad {
            corners,
            normal: n,
            rgb,
            glow,
        });
    }

    // ------------------------------------------------------------------ the hats

    /// Draws a hat; main = its colour, natural = true if that's its own colour.
    pub fn hat(&mut self, id: &str, main: u32, natural: bool, t: f32) {
        match id {
            "crown" => self.crown(main, t),
            "tophat" => self.top_hat(main),
            "wizard" => self.wizard(main, t),
            "beanie" => self.beanie(main),
            "catears" => self.cat_ears(main, t),
            "horns" => self.horns(main, t),
            "propeller" => self.propeller(natural, main, t),
            "angel" => self.angel(main, t),
            _ => {}
        }
    }

    fn crown(&mut self, gold: u32, t: f32) {
        let (dark, velvet) = (shade(gold, 0.72), 0x8E1F3A);
        let (o, i) = (4.7, 4.1);
        self.bx(-o, -10.2, -o, o, -8.0, -i, gold);
        self.bx(-o, -10.2, i, o, -8.0, o, gold);
        self.bx(-o, -10.2, -i, -i, -8.0, i, gold);
        self.bx(i, -10.2, -i, o, -8.0, i, gold);
        self.bx(-o - 0.06, -8.7, -o - 0.06, o + 0.06, -8.0, o + 0.06, dark);
        self.bx(-i, -9.3, -i, i, -8.0, i, velvet);
        self.bx(-2.8, -9.9, -2.8, 2.8, -9.3, 2.8, velvet);
        for (k, a) in [-3.3f32, 0.0, 3.3].into_iter().enumerate() {
            let top = if k == 1 { -12.7 } else { -11.8 };
            self.bx(a - 0.7, top, -o, a + 0.7, -10.2, -i, gold);
            self.bx(a - 0.7, top, i, a + 0.7, -10.2, o, gold);
            self.bx(-o, top, a - 0.7, -i, -10.2, a + 0.7, gold);
            self.bx(i, top, a - 0.7, o, -10.2, a + 0.7, gold);
            if k == 1 {
                self.bx(-0.45, top - 0.8, -o - 0.05, 0.45, top, -i + 0.05, gold);
            }
        }
        let p = 0.8 + 0.2 * sin(t * 2.6);
        self.glow_box(-0.75, -9.8, -o - 0.25, 0.75, -8.6, -o, shade(0xFF3B5C, p));
        self.glow_box(-3.3, -9.6, -o - 0.2, -2.5, -8.8, -o, shade(0x5AB8FF, p));
        self.glow_box(2.5, -9.6, -o - 0.2, 3.3, -8.8, -o, shade(0x5AB8FF, p));
        self.glow_box(-o - 0.2, -9.7, -0.6, -o, -8.7, 0.6, shade(0x56E39A, p));
        self.glow_box(o, -9.7, -0.6, o + 0.2, -8.7, 0.6, shade(0x56E39A, p));
        self.glow_box(-0.6, -9.7, o, 0.6, -8.7, o + 0.2, shade(0xFF3B5C, p));
    }

    fn top_hat(&mut self, band: u32) {
        let (body, edge) = (0x1C1C21, 0x2A2A31);
        self.rot(0.0, -8.0, 0.0, 0.0, 0.0, 5.0);
        self.bx(-6.2, -8.7, -6.2, 6.2, -8.0, 6.2, edge);
        self.bx(-6.2, -9.2, -6.2, -5.5, -8.7, 6.2, edge);
        self.bx(5.5, -9.2, -6.2, 6.2, -8.7, 6.2, edge);
        self.bx(-3.9, -15.6, -3.9, 3.9, -8.7, 3.9, body);
        self.bx(-4.0, -10.5, -4.0, 4.0, -8.7, 4.0, band);
        self.bx(-0.9, -10.4, -4.15, 0.9, -8.8, -4.0, 0xE8B94A);
        self.bx(-0.4, -9.95, -4.2, 0.4, -9.25, -4.1, 0x1C1C21);
        self.rot_reset();
    }

    fn wizard(&mut self, cloth: u32, t: f32) {
        self.bx(-6.6, -8.65, -6.6, 6.6, -8.0, 6.6, shade(cloth, 0.85));
        self.bx(-4.4, -9.7, -4.4, 4.4, -8.65, 4.4, 0xE8B94A);
        self.rot(0.0, -8.65, 0.0, sin(t * 1.4) * 3.0, 0.0, sin(t * 1.1) * 2.0);
        let seg: [[f32; 4]; 8] = [
            [4.3, -10.7, -9.7, 0.0],
            [3.6, -12.6, -10.7, 0.2],
            [2.9, -14.4, -12.6, 0.6],
            [2.2, -16.0, -14.4, 1.2],
            [1.5, -17.3, -16.0, 2.0],
            [0.95, -18.3, -17.3, 3.0],
            [0.55, -18.9, -18.3, 4.1],
            [0.35, -19.1, -18.6, 5.0],
        ];
        for s in seg {
            self.bx(-s[0], s[1], -s[0] + s[3], s[0], s[2], s[0] + s[3], cloth);
        }
        let star: [[f32; 3]; 4] = [[-1.9, -11.6, 1.0], [1.5, -13.5, 2.0], [-0.8, -15.2, 3.0], [1.1, -10.2, 0.0]];
        for (k, s) in star.iter().enumerate() {
            let part = seg[s[2] as usize];
            let z = part[3] - part[0];
            let tw = 0.55 + 0.45 * sin(t * 2.2 + k as f32 * 1.7).max(0.0);
            let c = shade(0xFFE58A, tw);
            self.glow_box(s[0] - 0.55, s[1] - 0.15, z - 0.2, s[0] + 0.55, s[1] + 0.15, z, c);
            self.glow_box(s[0] - 0.15, s[1] - 0.55, z - 0.2, s[0] + 0.15, s[1] + 0.55, z, c);
        }
        self.rot_reset();
    }

    fn beanie(&mut self, knit: u32) {
        self.bx(-4.7, -9.3, -4.7, 4.7, -5.6, 4.7, knit);
        self.bx(-3.8, -9.9, -3.8, 3.8, -9.3, 3.8, knit);
        self.bx(-4.95, -6.6, -4.95, 4.95, -5.0, 4.95, shade(knit, 0.78));
        for k in (-4..=4).step_by(2) {
            let k = k as f32;
            self.bx(k - 0.3, -6.5, -5.05, k + 0.3, -5.1, -4.95, shade(knit, 0.66));
        }
        self.bx(-1.5, -12.3, -1.5, 1.5, -9.8, 1.5, 0xF2F2F2);
        self.bx(-1.1, -12.7, -1.1, 1.1, -12.3, 1.1, 0xF2F2F2);
    }

    fn cat_ears(&mut self, fur: u32, t: f32) {
        let inner = 0xF29CB8;
        self.bx(-4.65, -8.45, -1.2, 4.65, -7.95, -0.3, fur);
        self.bx(-4.8, -8.45, -1.2, -4.6, -4.6, -0.3, fur);
        self.bx(4.6, -8.45, -1.2, 4.8, -4.6, -0.3, fur);
        for s in [-1.0f32, 1.0] {
            let (cx, cz) = (s * 2.6, -0.7);
            let period = if s < 0.0 { 4.7 } else { 6.1 };
            let ph = (t + if s < 0.0 { 0.0 } else { 2.3 }) % period;
            let twitch = if ph < 0.35 { (ph / 0.35 * PI).sin() * 16.0 } else { 0.0 };
            self.rot(cx, -8.0, cz, -twitch, 0.0, -s * 13.0);
            for q in [[2.0, -9.4, -8.0], [1.6, -10.8, -9.4], [1.15, -12.1, -10.8], [0.7, -13.2, -12.1], [0.3, -13.9, -13.2]] {
                self.bx(cx - q[0], q[1], cz - 0.6, cx + q[0], q[2], cz + 0.6, fur);
            }
            for q in [[1.3, -9.8, -8.3], [0.95, -11.1, -9.8], [0.55, -12.3, -11.1], [0.2, -13.0, -12.3]] {
                self.bx(cx - q[0], q[1], cz - 0.74, cx + q[0], q[2], cz - 0.6, inner);
            }
            self.rot_reset();
        }
    }

    fn horns(&mut self, base: u32, t: f32) {
        let seg: [[f32; 4]; 7] = [
            [3.2, -8.3, -2.2, 0.95],
            [3.9, -9.3, -2.4, 0.85],
            [4.6, -10.3, -2.5, 0.75],
            [5.0, -11.4, -2.4, 0.62],
            [5.1, -12.4, -2.0, 0.5],
            [4.8, -13.2, -1.4, 0.4],
            [4.3, -13.7, -0.8, 0.3],
        ];
        let tip = 0x160B0B;
        for s in [-1.0f32, 1.0] {
            for (k, q) in seg.iter().enumerate() {
                let (x, h) = (s * q[0], q[3]);
                let c = mix(base, tip, k as f32 / (seg.len() - 1) as f32);
                self.bx(x - h, q[1] - h, q[2] - h, x + h, q[1] + h, q[2] + h, c);
            }
            let q = seg[seg.len() - 1];
            let p = 0.5 + 0.25 * sin(t * 2.0 + s);
            self.glow_box(s * q[0] - 0.18, q[1] - 0.48, q[2] - 0.18, s * q[0] + 0.18, q[1] - 0.25, q[2] + 0.18, shade(0xFF5A3C, p));
        }
    }

    fn propeller(&mut self, natural: bool, main: u32, t: f32) {
        let q = if natural {
            [0xE8434F, 0xFFD84A, 0x4A8CFF, 0x45C46A]
        } else {
            [main, shade(main, 0.75), main, shade(main, 0.75)]
        };
        let o = 4.65;
        self.bx(-o, -9.0, -o, 0.0, -6.8, 0.0, q[0]);
        self.bx(0.0, -9.0, -o, o, -6.8, 0.0, q[1]);
        self.bx(0.0, -9.0, 0.0, o, -6.8, o, q[2]);
        self.bx(-o, -9.0, 0.0, 0.0, -6.8, o, q[3]);
        self.bx(-3.4, -9.7, -3.4, 0.0, -9.0, 0.0, q[0]);
        self.bx(0.0, -9.7, -3.4, 3.4, -9.0, 0.0, q[1]);
        self.bx(0.0, -9.7, 0.0, 3.4, -9.0, 3.4, q[2]);
        self.bx(-3.4, -9.7, 0.0, 0.0, -9.0, 3.4, q[3]);
        self.bx(-3.6, -7.3, -7.4, 3.6, -6.8, -o, q[0]);
        self.bx(-0.3, -11.2, -0.3, 0.3, -9.7, 0.3, 0x9A9AA2);
        self.bx(-0.6, -11.7, -0.6, 0.6, -11.1, 0.6, q[0]);
        self.rot(0.0, -11.4, 0.0, 0.0, (t * 760.0) % 360.0, 0.0);
        self.bx(-4.6, -11.55, -0.55, -0.6, -11.2, 0.55, q[1]);
        self.bx(0.6, -11.55, -0.55, 4.6, -11.2, 0.55, q[2]);
        self.rot_reset();
    }

    fn ring(&mut self, y: f32, c: u32, t: f32) {
        let spin = (t * 25.0) % 360.0;
        let n = 18;
        let r = 3.7;
        let half = (PI * r / n as f32) * 1.12;
        for k in 0..n {
            self.rot(0.0, y, 0.0, 7.0, spin + k as f32 * 360.0 / n as f32, 0.0);
            self.glow_box(-half, y - 0.28, -r - 0.32, half, y + 0.28, -r + 0.32, c);
        }
        self.rot_reset();
    }

    fn angel(&mut self, c: u32, t: f32) {
        let y = -11.4 + 0.35 * sin(t * 2.0);
        let col = shade(c, 0.85 + 0.15 * sin(t * 3.0));
        self.ring(y, col, t);
    }

    // ------------------------------------------------------------------ the Maker's hat and boots

    /// A hat put together in the Maker: a base, something on top, a trim, two colours.
    pub fn custom_hat(&mut self, base: &str, top: &str, trim: &str, c1: u32, c2: u32, t: f32) {
        let (top_y, band_lo, band_hi, band_r);
        match base {
            "cap" => {
                self.bx(-4.65, -9.0, -4.65, 4.65, -6.8, 4.65, c1);
                self.bx(-3.4, -9.7, -3.4, 3.4, -9.0, 3.4, c1);
                self.bx(-3.6, -7.3, -7.4, 3.6, -6.8, -4.65, if trim == "stripe" { c2 } else { shade(c1, 0.85) });
                (top_y, band_lo, band_hi, band_r) = (-9.7, -7.7, -6.9, 4.75);
            }
            "beanie" => {
                self.bx(-4.7, -9.3, -4.7, 4.7, -5.6, 4.7, c1);
                self.bx(-3.8, -9.9, -3.8, 3.8, -9.3, 3.8, c1);
                self.bx(-4.95, -6.6, -4.95, 4.95, -5.0, 4.95, shade(c1, 0.78));
                (top_y, band_lo, band_hi, band_r) = (-9.9, -6.65, -4.95, 5.0);
            }
            "brim" => {
                self.bx(-6.0, -8.6, -6.0, 6.0, -8.0, 6.0, shade(c1, 0.85));
                self.bx(-4.0, -12.0, -4.0, 4.0, -8.6, 4.0, c1);
                self.bx(-3.4, -12.6, -3.4, 3.4, -12.0, 3.4, c1);
                (top_y, band_lo, band_hi, band_r) = (-12.6, -9.8, -8.6, 4.1);
            }
            "crown" => {
                let (o, i) = (4.7, 4.1);
                self.bx(-o, -10.2, -o, o, -8.0, -i, c1);
                self.bx(-o, -10.2, i, o, -8.0, o, c1);
                self.bx(-o, -10.2, -i, -i, -8.0, i, c1);
                self.bx(i, -10.2, -i, o, -8.0, i, c1);
                self.bx(-i, -9.3, -i, i, -8.0, i, shade(c1, 0.55));
                for (k, a) in [-3.3f32, 0.0, 3.3].into_iter().enumerate() {
                    let tp = if k == 1 { -12.4 } else { -11.6 };
                    self.bx(a - 0.7, tp, -o, a + 0.7, -10.2, -i, c1);
                    self.bx(a - 0.7, tp, i, a + 0.7, -10.2, o, c1);
                    self.bx(-o, tp, a - 0.7, -i, -10.2, a + 0.7, c1);
                    self.bx(i, tp, a - 0.7, o, -10.2, a + 0.7, c1);
                }
                (top_y, band_lo, band_hi, band_r) = (-9.3, -9.0, -8.2, 4.8);
            }
            "band" => {
                self.bx(-4.75, -7.6, -4.75, 4.75, -6.2, -4.4, c1);
                self.bx(-4.75, -7.6, 4.4, 4.75, -6.2, 4.75, c1);
                self.bx(-4.75, -7.6, -4.4, -4.4, -6.2, 4.4, c1);
                self.bx(4.4, -7.6, -4.4, 4.75, -6.2, 4.4, c1);
                (top_y, band_lo, band_hi, band_r) = (-8.0, -7.2, -6.6, 4.8);
            }
            _ => (top_y, band_lo, band_hi, band_r) = (-8.0, 0.0, 0.0, 0.0),
        }
        // the trim
        if band_r > 0.0 {
            match trim {
                "stripe" => self.bx(-band_r, band_lo, -band_r, band_r, band_hi, band_r, c2),
                "gems" => {
                    let p = 0.8 + 0.2 * sin(t * 2.6);
                    let y = (band_lo + band_hi) / 2.0;
                    let g = shade(c2, p);
                    self.glow_box(-0.7, y - 0.6, -band_r - 0.25, 0.7, y + 0.6, -band_r, g);
                    self.glow_box(-3.1, y - 0.4, -band_r - 0.2, -2.3, y + 0.4, -band_r, g);
                    self.glow_box(2.3, y - 0.4, -band_r - 0.2, 3.1, y + 0.4, -band_r, g);
                    self.glow_box(-band_r - 0.2, y - 0.5, -0.5, -band_r, y + 0.5, 0.5, g);
                    self.glow_box(band_r, y - 0.5, -0.5, band_r + 0.2, y + 0.5, 0.5, g);
                }
                "stars" => {
                    let y = (band_lo + band_hi) / 2.0 - if base == "brim" { 1.6 } else { 0.9 };
                    for (k, x) in [-2.4f32, 0.4, 2.6].into_iter().enumerate() {
                        let tw = 0.55 + 0.45 * sin(t * 2.2 + k as f32 * 1.9).max(0.0);
                        let g = shade(mix(c2, 0xFFFFFF, 0.35), tw);
                        let yy = y - if k == 1 { 0.8 } else { 0.0 };
                        let z = -band_r;
                        self.glow_box(x - 0.5, yy - 0.14, z - 0.2, x + 0.5, yy + 0.14, z, g);
                        self.glow_box(x - 0.14, yy - 0.5, z - 0.2, x + 0.14, yy + 0.5, z, g);
                    }
                }
                _ => {}
            }
        }
        // the top (a floating ring or an antenna goes above a crown's points)
        let above = if base == "crown" { -12.4 } else { top_y };
        match top {
            "pompom" => {
                self.bx(-1.5, top_y - 2.5, -1.5, 1.5, top_y + 0.1, 1.5, c2);
                self.bx(-1.1, top_y - 2.9, -1.1, 1.1, top_y - 2.5, 1.1, c2);
            }
            "cone" => {
                self.rot(0.0, top_y, 0.0, sin(t * 1.4) * 3.0, 0.0, 0.0);
                let mut y = top_y;
                for q in [[2.6, 1.6, 0.0], [2.0, 1.5, 0.3], [1.4, 1.4, 0.8], [0.85, 1.2, 1.5], [0.45, 0.9, 2.4]] {
                    self.bx(-q[0], y - q[1], -q[0] + q[2], q[0], y, q[0] + q[2], c2);
                    y -= q[1];
                }
                self.rot_reset();
            }
            "propeller" => {
                self.bx(-0.3, top_y - 1.5, -0.3, 0.3, top_y, 0.3, 0x9A9AA2);
                self.bx(-0.6, top_y - 2.0, -0.6, 0.6, top_y - 1.4, 0.6, c1);
                self.rot(0.0, top_y - 1.7, 0.0, 0.0, (t * 760.0) % 360.0, 0.0);
                self.bx(-4.6, top_y - 1.85, -0.55, -0.6, top_y - 1.5, 0.55, c2);
                self.bx(0.6, top_y - 1.85, -0.55, 4.6, top_y - 1.5, 0.55, shade(c2, 0.8));
                self.rot_reset();
            }
            "ears" => {
                for sd in [-1.0f32, 1.0] {
                    let (cx, cz) = (sd * 2.6, -0.7);
                    let b = top_y.min(-8.0) + 0.4;
                    self.rot(cx, b, cz, 0.0, 0.0, -sd * 13.0);
                    let mut y = b;
                    for q in [[1.8, 1.3], [1.4, 1.3], [0.95, 1.2], [0.5, 1.0]] {
                        self.bx(cx - q[0], y - q[1], cz - 0.6, cx + q[0], y, cz + 0.6, c1);
                        if q[0] > 0.6 {
                            self.bx(cx - q[0] + 0.55, y - q[1], cz - 0.74, cx + q[0] - 0.55, y, cz - 0.6, c2);
                        }
                        y -= q[1];
                    }
                    self.rot_reset();
                }
            }
            "horns" => {
                let sg: [[f32; 4]; 6] = [
                    [3.2, -0.3, -2.2, 0.95],
                    [3.9, -1.3, -2.4, 0.85],
                    [4.6, -2.3, -2.5, 0.75],
                    [5.0, -3.4, -2.4, 0.62],
                    [5.1, -4.4, -2.0, 0.5],
                    [4.8, -5.2, -1.4, 0.4],
                ];
                let b = top_y.min(-8.0);
                for sd in [-1.0f32, 1.0] {
                    for (k, q) in sg.iter().enumerate() {
                        let c = mix(c2, shade(c2, 0.3), k as f32 / (sg.len() - 1) as f32);
                        self.bx(sd * q[0] - q[3], b + q[1] - q[3], q[2] - q[3], sd * q[0] + q[3], b + q[1] + q[3], q[2] + q[3], c);
                    }
                }
            }
            "antenna" => {
                let bob = 0.3 * sin(t * 2.4);
                self.bx(-0.25, above - 4.0, -0.25, 0.25, top_y, 0.25, 0x9A9AA2);
                let p = 0.8 + 0.2 * sin(t * 3.0);
                self.glow_box(-0.8, above - 5.4 + bob, -0.8, 0.8, above - 3.8 + bob, 0.8, shade(c2, p));
            }
            "halo" => {
                let y = above - 2.6 + 0.35 * sin(t * 2.0);
                let col = shade(c2, 0.85 + 0.15 * sin(t * 3.0));
                self.ring(y, col, t);
            }
            _ => {}
        }
    }

    /// Boots put together in the Maker: a shape, an extra, two colours (the trail is drawn elsewhere).
    #[allow(clippy::too_many_arguments)]
    pub fn custom_boot(&mut self, shape: &str, extra: &str, c1: u32, c2: u32, t: f32, side: f32, active: bool) {
        match shape {
            "boot" => {
                self.bx(-2.35, 6.4, -2.35, 2.35, 11.8, 2.35, c1);
                self.bx(-2.35, 10.5, -3.1, 2.35, 11.8, -2.35, c1);
                self.bx(-2.45, 11.75, -3.2, 2.45, 12.35, 2.45, c2);
                self.bx(-2.5, 6.2, -2.5, 2.5, 7.0, 2.5, c2);
            }
            "chunky" => {
                self.bx(-2.35, 8.6, -2.35, 2.35, 11.4, 2.35, c1);
                self.bx(-2.35, 10.2, -3.1, 2.35, 11.4, -2.35, c1);
                self.bx(-2.6, 11.3, -3.4, 2.6, 13.0, 2.7, c2);
                self.bx(-2.42, 8.4, -2.42, 2.42, 9.0, 2.42, c2);
            }
            "cloud" => {
                self.cloud(c1, t, side);
                self.bx(-1.2, 12.3, -2.4, 1.6, 12.8, 2.0, c2);
            }
            _ => {
                self.bx(-2.35, 8.6, -2.35, 2.35, 11.8, 2.35, c1);
                self.bx(-2.35, 10.5, -3.1, 2.35, 11.8, -2.35, c1);
                self.bx(-2.45, 11.75, -3.2, 2.45, 12.35, 2.45, mix(c2, 0xF0F0EA, 0.5));
                self.bx(-2.42, 8.4, -2.42, 2.42, 9.0, 2.42, c2);
                self.bx(side * 2.35, 9.9, -2.0, side * 2.43, 10.7, 1.8, c2);
            }
        }
        match extra {
            "wings" => self.wing(c2, t, side, active, false),
            "crystals" => {
                let g = shade(mix(c2, 0xFFFFFF, 0.4), 0.55 + 0.2 * sin(t * 1.8 + side));
                let x = side * 2.35;
                self.crystal(x, 9.6, -1.1, side, 1.5, g);
                self.crystal(x, 10.6, 0.9, side, 1.1, g);
                self.crystal(x, 8.7, 1.6, side, 0.8, g);
            }
            "cracks" => {
                let g = shade(c2, 0.6 + 0.4 * sin(t * 2.4 + side * 0.8));
                self.glow_box(-1.6, 9.2, -2.45, -1.2, 11.6, -2.35, g);
                self.glow_box(-1.2, 10.2, -2.45, 0.9, 10.6, -2.35, g);
                self.glow_box(0.9, 9.0, -2.45, 1.3, 10.6, -2.35, g);
                let x = side * 2.35;
                self.glow_box(x - 0.06, 9.4, -1.8, x + 0.06, 9.8, 1.6, g);
                self.glow_box(x - 0.06, 9.8, 0.6, x + 0.06, 11.4, 1.0, g);
            }
            "nozzle" => self.nozzle(mix(c2, 0xFF7A1A, 0.5), t, active),
            _ => {}
        }
    }

    // ------------------------------------------------------------------ the boots

    /// Draws one boot; side = -1 for the right leg, 1 for the left (the outside of the foot).
    pub fn boot(&mut self, id: &str, main: u32, t: f32, side: f32, active: bool) {
        match id {
            "sneakers" => self.sneakers(main, side),
            "rocket" => self.rocket(main, t, active),
            "winged" => self.winged(main, t, side, active),
            "cloud" => self.cloud(main, t, side),
            "frost" => self.frost(main, t, side),
            "magma" => self.magma(main, t, side),
            _ => {}
        }
    }

    fn shell(&mut self, upper: u32, sole: u32) {
        self.bx(-2.35, 8.6, -2.35, 2.35, 11.8, 2.35, upper);
        self.bx(-2.35, 10.5, -3.1, 2.35, 11.8, -2.35, upper);
        self.bx(-2.45, 11.75, -3.2, 2.45, 12.35, 2.45, sole);
    }

    fn sneakers(&mut self, accent: u32, side: f32) {
        self.shell(0xF1F1EE, 0xE3E1DA);
        self.bx(-2.5, 12.2, -3.3, 2.5, 12.45, 2.55, 0xB98C5A);
        self.bx(-2.42, 8.4, -2.42, 2.42, 9.0, 2.42, accent);
        self.bx(side * 2.35, 9.9, -2.0, side * 2.43, 10.7, 1.8, accent);
        for k in 0..3 {
            let y = 9.2 + k as f32 * 0.65;
            self.bx(-1.2, y, -2.45, 1.2, y + 0.25, -2.35, 0x34343A);
        }
    }

    fn nozzle(&mut self, flame: u32, t: f32, active: bool) {
        self.bx(-1.1, 10.3, 2.35, 1.1, 11.9, 3.3, 0x3A3E45);
        let flick = 0.75 + 0.25 * sin(t * 40.0) * sin(t * 23.0 + 1.0);
        let len = if active { 2.2 + 1.4 * flick } else { 0.6 + 0.3 * flick };
        self.glow_box(-0.75, 11.9, 2.45, 0.75, 11.9 + len, 3.15, flame);
        self.glow_box(-0.4, 11.9, 2.6, 0.4, 11.9 + len * 0.6, 3.0, 0xFFE07A);
    }

    fn rocket(&mut self, accent: u32, t: f32, active: bool) {
        self.shell(0xA7B0BA, 0x4A4F57);
        self.bx(-2.42, 8.4, -2.42, 2.42, 9.1, 2.42, accent);
        self.bx(-2.42, 10.2, -3.18, 2.42, 10.6, -2.35, accent);
        self.nozzle(0xFF7A1A, t, active);
    }

    fn wing(&mut self, trim: u32, t: f32, side: f32, active: bool, long: bool) {
        let flap = sin(t * if active { 14.0 } else { 4.0 }) * if active { 26.0 } else { 10.0 };
        let x = side * 2.4;
        self.rot(x, 9.4, 0.4, 0.0, side * (20.0 + flap), 0.0);
        let (x0, x1) = if side > 0.0 { (x, x + 0.3) } else { (x - 0.3, x) };
        self.bx(x0, 7.6, 0.4, x1, 8.6, 4.6, 0xFFFFFF);
        self.bx(x0, 8.6, 0.4, x1, 9.5, 3.8, 0xF4F1EA);
        self.bx(x0, 9.5, 0.4, x1, 10.3, 3.0, 0xE9E4DA);
        if long {
            self.bx(x0, 10.3, 0.4, x1, 10.9, 2.2, 0xDDD6C8);
        }
        self.bx(x0 - 0.02, 7.5, 0.3, x1 + 0.02, 7.85, 4.7, trim);
        self.rot_reset();
    }

    fn winged(&mut self, trim: u32, t: f32, side: f32, active: bool) {
        self.shell(0xEDE6D6, shade(trim, 0.8));
        self.bx(-2.42, 8.4, -2.42, 2.42, 9.0, 2.42, trim);
        self.wing(trim, t, side, active, true);
    }

    fn cloud(&mut self, c: u32, t: f32, side: f32) {
        let b = 0.25 * sin(t * 2.2 + side);
        let (lo, lo2) = (shade(c, 0.9), shade(c, 0.8));
        self.bx(-3.0, 10.4 + b, -3.6, 3.0, 12.3 + b, 3.0, c);
        self.bx(-2.4, 9.4 + b, -2.9, 2.4, 10.4 + b, 2.4, c);
        // a puff round the top (outside the leg, so it never flickers through it)
        self.bx(-2.6, 8.8 + b, -2.7, 2.6, 9.4 + b, 2.5, c);
        self.bx(-3.8, 10.8 + b, -2.6, -3.0, 12.0 + b, 2.0, lo);
        self.bx(3.0, 10.6 + b, -2.2, 3.8, 11.9 + b, 2.4, lo);
        self.bx(-2.2, 10.7 + b, 3.0, 2.0, 12.0 + b, 3.8, lo);
        self.bx(-2.4, 10.8 + b, -4.4, 1.8, 12.1 + b, -3.6, lo);
        self.bx(-1.2, 12.3 + b, -2.4, 1.6, 12.8 + b, 2.0, lo2);
        self.bx(side * 2.2 - 0.9, 9.0 + b, -1.0, side * 2.2 + 0.9, 10.4 + b, 0.9, c);
    }

    fn frost(&mut self, ice: u32, t: f32, side: f32) {
        self.shell(ice, shade(ice, 0.7));
        self.bx(-2.45, 8.3, -2.45, 2.45, 8.9, 2.45, 0xE6F8FF);
        let g = shade(0xE0F6FF, 0.55 + 0.2 * sin(t * 1.8 + side));
        let x = side * 2.35;
        self.crystal(x, 9.6, -1.1, side, 1.5, g);
        self.crystal(x, 10.6, 0.9, side, 1.1, g);
        self.crystal(x, 8.7, 1.6, side, 0.8, g);
        self.glow_box(-0.35, 10.5, -3.5, 0.35, 11.3, -3.1, g);
    }

    #[allow(clippy::too_many_arguments)]
    fn crystal(&mut self, x: f32, y: f32, z: f32, side: f32, len: f32, c: u32) {
        self.rot(x, y, z, 0.0, 0.0, side * -28.0);
        let (x1, x2) = if side > 0.0 { (x, x + len) } else { (x - len, x) };
        self.glow_box(x1, y - 0.3, z - 0.3, x2, y + 0.3, z + 0.3, c);
        let tip = if side > 0.0 { x2 } else { x1 };
        self.glow_box(tip - 0.35, y - 0.15, z - 0.15, tip + 0.35, y + 0.15, z + 0.15, c);
        self.rot_reset();
    }

    fn magma(&mut self, glow: u32, t: f32, side: f32) {
        self.shell(0x2A1B17, 0x1A100D);
        let p = 0.6 + 0.4 * sin(t * 2.4 + side * 0.8);
        let (g, g2) = (shade(glow, p), shade(mix(glow, 0xFFE07A, 0.5), p));
        self.glow_box(-1.6, 9.2, -2.45, -1.2, 11.6, -2.35, g);
        self.glow_box(-1.2, 10.2, -2.45, 0.9, 10.6, -2.35, g2);
        self.glow_box(0.9, 9.0, -2.45, 1.3, 10.6, -2.35, g);
        let x = side * 2.35;
        self.glow_box(x - 0.06, 9.4, -1.8, x + 0.06, 9.8, 1.6, g);
        self.glow_box(x - 0.06, 9.8, 0.6, x + 0.06, 11.4, 1.0, g2);
        self.glow_box(-1.8, 11.8, -3.25, 1.8, 12.0, -3.15, g);
    }
}

/// Everything a look puts on the head (model pixels, y down), at time t (seconds).
pub fn hat_of(look: &super::Look, t: f32, ms: u64) -> Shapes {
    let mut s = Shapes::new();
    if look.hat == "custom" {
        let p = look.hk_parts();
        if p.len() == 5 {
            let acc = look.accent();
            s.custom_hat(p[0], p[1], p[2], super::palette_rgb(p[3], acc), super::palette_rgb(p[4], acc), t);
        }
    } else if look.has_hat() {
        let main = look
            .wear_rgb(&look.hat_color, Some(natural_hat(&look.hat)), ms)
            .unwrap_or(0xFFFFFF);
        s.hat(&look.hat, main, look.hat_color == "natural", t);
    }
    s
}

/// One boot (side -1 = right leg, 1 = left), model pixels with y down.
pub fn boot_of(look: &super::Look, t: f32, ms: u64, side: f32, active: bool) -> Shapes {
    let mut s = Shapes::new();
    if look.boots == "custom" {
        let p = look.bk_parts();
        if p.len() == 5 {
            let acc = look.accent();
            s.custom_boot(p[0], p[1], super::palette_rgb(p[3], acc), super::palette_rgb(p[4], acc), t, side, active);
        }
    } else if look.has_boots() {
        let main = look
            .wear_rgb(&look.boot_color, Some(natural_boots(&look.boots)), ms)
            .unwrap_or(0xFFFFFF);
        s.boot(&look.boots, main, t, side, active);
    }
    s
}

/// True if what this look wears moves (so it has to be built again every few frames).
pub fn animated(look: &super::Look) -> bool {
    let hat = match look.hat.as_str() {
        "none" | "tophat" | "beanie" => false,
        "custom" => {
            let p = look.hk_parts();
            p.len() == 5 && (p[1] != "none" && p[1] != "pompom" && p[1] != "ears" && p[1] != "horns" || p[2] == "gems" || p[2] == "stars")
        }
        _ => true,
    };
    let boots = match look.boots.as_str() {
        "none" | "sneakers" => false,
        "custom" => {
            let p = look.bk_parts();
            p.len() == 5 && (p[0] == "cloud" || p[1] != "none")
        }
        _ => true,
    };
    hat || boots || look.hat_color == "rainbow" || look.boot_color == "rainbow"
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn every_hat_and_boot_builds() {
        for id in super::super::HATS {
            let mut s = Shapes::new();
            s.hat(id, 0xFFD76B, true, 1.3);
            assert!(id == &"none" || id == &"custom" || !s.quads.is_empty(), "{id}");
        }
        for id in super::super::BOOTS {
            let mut s = Shapes::new();
            s.boot(id, 0xFFD76B, 1.3, -1.0, true);
            assert!(id == &"none" || id == &"custom" || !s.quads.is_empty(), "{id}");
        }
        let mut s = Shapes::new();
        s.custom_hat("crown", "halo", "stars", 0xFFD76B, 0xFFFFFF, 0.4);
        assert!(s.quads.iter().any(|q| q.glow));
        // a turned box keeps unit normals
        for q in &s.quads {
            let n = q.normal;
            assert!(((n[0] * n[0] + n[1] * n[1] + n[2] * n[2]).sqrt() - 1.0).abs() < 1e-3);
        }
    }
}
