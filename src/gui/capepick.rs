//! Treasure Client: capes, the same as the Eaglercraft Edit Cape screen. The 25 Eaglercraft
//! preset capes ("No Cape" first) and capes you add from a PNG. Each profile (account)
//! remembers its own cape; on Eagler servers it is sent at login as `cape_v1`, so other
//! Eaglercraft players see it.

use std::collections::HashMap;
use std::sync::Arc;

use base64::Engine as _;
use image::RgbaImage;

/// The preset capes, in the Eaglercraft id order (id 0 = no cape).
pub(crate) const PRESETS: [&str; 26] = [
    "No Cape",
    "Minecon 2011",
    "Minecon 2012",
    "Minecon 2013",
    "Minecon 2015",
    "Minecon 2016",
    "Microsoft Account",
    "Realms Mapmaker",
    "Mojang Old",
    "Mojang New",
    "Jira Moderator",
    "Mojang Very Old",
    "Scrolls",
    "Cobalt",
    "Lang Translator",
    "Millionth Player",
    "Prismarine",
    "Snowman",
    "Spade",
    "Birthday",
    "dB",
    "15th Anniversary",
    "Vanilla",
    "TikTok",
    "Purple Heart",
    "Cherry Blossom",
];

const PRESET_PNGS: [&[u8]; 25] = [
    include_bytes!("../eagler/capes/01.minecon_2011.png"),
    include_bytes!("../eagler/capes/02.minecon_2012.png"),
    include_bytes!("../eagler/capes/03.minecon_2013.png"),
    include_bytes!("../eagler/capes/04.minecon_2015.png"),
    include_bytes!("../eagler/capes/05.minecon_2016.png"),
    include_bytes!("../eagler/capes/06.microsoft_account.png"),
    include_bytes!("../eagler/capes/07.mapmaker.png"),
    include_bytes!("../eagler/capes/08.mojang_old.png"),
    include_bytes!("../eagler/capes/09.mojang_new.png"),
    include_bytes!("../eagler/capes/10.jira_mod.png"),
    include_bytes!("../eagler/capes/11.mojang_very_old.png"),
    include_bytes!("../eagler/capes/12.scrolls.png"),
    include_bytes!("../eagler/capes/13.cobalt.png"),
    include_bytes!("../eagler/capes/14.translator.png"),
    include_bytes!("../eagler/capes/15.millionth_account.png"),
    include_bytes!("../eagler/capes/16.prismarine.png"),
    include_bytes!("../eagler/capes/17.snowman.png"),
    include_bytes!("../eagler/capes/18.spade.png"),
    include_bytes!("../eagler/capes/19.birthday.png"),
    include_bytes!("../eagler/capes/20.db.png"),
    include_bytes!("../eagler/capes/21.15th_anniversary.png"),
    include_bytes!("../eagler/capes/22.vanilla.png"),
    include_bytes!("../eagler/capes/23.tiktok.png"),
    include_bytes!("../eagler/capes/24.purple_heart.png"),
    include_bytes!("../eagler/capes/25.cherry_blossom.png"),
];

const STORE_KEY: &str = "treasureClient.rustCapes";

/// A cape as a 64x64 RGBA picture (the cape in the top-left 64x32, like a cape texture).
pub(crate) struct Cape {
    pub name: String,
    pub rgba: Arc<Vec<u8>>,
}

/// Every cape you can pick: the custom ones first, then the presets (like Eaglercraft).
#[derive(Default)]
pub struct CapeList {
    loaded: bool,
    pub(crate) custom: Vec<Cape>,
    presets: Vec<Arc<Vec<u8>>>,
    picks: HashMap<String, String>,
}

impl CapeList {
    pub fn load(&mut self) {
        if self.loaded {
            return;
        }
        self.loaded = true;
        let (picks, custom) = read();
        self.picks = picks;
        self.custom = custom;
        self.presets = (0..PRESETS.len()).map(|id| Arc::new(preset_rgba(id))).collect();
    }

    pub fn names(&self) -> Vec<String> {
        self.custom
            .iter()
            .map(|c| c.name.clone())
            .chain(PRESETS.iter().map(|s| (*s).to_owned()))
            .collect()
    }

    pub fn len(&self) -> usize {
        self.custom.len() + PRESETS.len()
    }

    /// The picture for list entry `index`.
    pub fn rgba(&self, index: usize) -> Option<Arc<Vec<u8>>> {
        if index < self.custom.len() {
            return Some(self.custom[index].rgba.clone());
        }
        self.presets.get(index - self.custom.len()).cloned()
    }

    pub fn is_none(&self, index: usize) -> bool {
        index == self.custom.len()
    }

    /// The list entry this account wears ("No Cape" when it has never picked one).
    pub fn index_for(&self, account: &str) -> usize {
        self.picks
            .get(account)
            .and_then(|n| self.names().iter().position(|s| s == n))
            .unwrap_or(self.custom.len())
    }

    pub fn pick(&mut self, old: Option<&str>, account: &str, index: usize) {
        let Some(name) = self.names().get(index).cloned() else {
            return;
        };
        if let Some(old) = old.filter(|o| *o != account) {
            self.picks.remove(old);
        }
        self.picks.insert(account.to_owned(), name);
        self.save();
    }

    pub fn forget(&mut self, account: &str) {
        self.picks.remove(account);
        self.save();
    }

    /// Adds a cape from a PNG (64x32 cape textures, or Eaglercraft's 32x32 ones). Returns
    /// its list index.
    pub fn add(&mut self, file: &str, bytes: &[u8]) -> Option<usize> {
        let rgba = decode_png(bytes)?;
        let stem = std::path::Path::new(file)
            .file_stem()
            .and_then(|s| s.to_str())
            .unwrap_or("Cape");
        let mut name = stem.to_string();
        for n in 2.. {
            if !self.names().iter().any(|s| *s == name) {
                break;
            }
            name = format!("{stem} ({n})");
        }
        self.custom.push(Cape { name, rgba: Arc::new(rgba) });
        self.save();
        Some(self.custom.len() - 1)
    }

    pub fn clear_custom(&mut self) {
        self.custom.clear();
        self.save();
    }

    fn save(&self) {
        let capes: Vec<serde_json::Value> = self
            .custom
            .iter()
            .filter_map(|c| {
                let mut png = std::io::Cursor::new(Vec::new());
                RgbaImage::from_raw(64, 64, c.rgba.to_vec())?
                    .write_to(&mut png, image::ImageFormat::Png)
                    .ok()?;
                let png = base64::engine::general_purpose::STANDARD.encode(png.into_inner());
                Some(serde_json::json!({ "name": c.name, "png": png }))
            })
            .collect();
        let doc = serde_json::json!({ "picks": self.picks, "capes": capes });
        store(&doc.to_string());
    }
}

/// A 64x64 picture with the cape's 64x32 texture (or a 32x32 Eaglercraft cape) at the top.
fn decode_png(bytes: &[u8]) -> Option<Vec<u8>> {
    let img = image::load_from_memory(bytes).ok()?.to_rgba8();
    let (w, h) = img.dimensions();
    if !((w == 64 && h == 32) || (w == 32 && h == 32) || (w == 64 && h == 64)) {
        return None;
    }
    let mut out = RgbaImage::new(64, 64);
    for y in 0..h.min(32) {
        for x in 0..w {
            out.put_pixel(x, y, *img.get_pixel(x, y));
        }
    }
    Some(out.into_raw())
}

fn preset_rgba(id: usize) -> Vec<u8> {
    if id == 0 {
        return vec![0; 64 * 64 * 4];
    }
    PRESET_PNGS
        .get(id - 1)
        .and_then(|b| decode_png(b))
        .unwrap_or_else(|| vec![0; 64 * 64 * 4])
}

#[cfg(target_arch = "wasm32")]
fn load_text() -> Option<String> {
    web_sys::window()?.local_storage().ok()??.get_item(STORE_KEY).ok()?
}

#[cfg(target_arch = "wasm32")]
fn store(text: &str) {
    if let Some(s) = web_sys::window().and_then(|w| w.local_storage().ok().flatten()) {
        let _ = s.set_item(STORE_KEY, text);
    }
}

#[cfg(not(target_arch = "wasm32"))]
fn load_text() -> Option<String> {
    let _ = STORE_KEY;
    None
}

#[cfg(not(target_arch = "wasm32"))]
fn store(_text: &str) {}

fn read() -> (HashMap<String, String>, Vec<Cape>) {
    let doc: serde_json::Value = load_text()
        .and_then(|t| serde_json::from_str(&t).ok())
        .unwrap_or_default();
    let picks = doc
        .get("picks")
        .and_then(|p| p.as_object())
        .map(|o| {
            o.iter()
                .filter_map(|(k, v)| Some((k.clone(), v.as_str()?.to_string())))
                .collect()
        })
        .unwrap_or_default();
    let capes = doc
        .get("capes")
        .and_then(|s| s.as_array())
        .map(|a| {
            a.iter()
                .filter_map(|c| {
                    let png = base64::engine::general_purpose::STANDARD
                        .decode(c.get("png")?.as_str()?)
                        .ok()?;
                    Some(Cape {
                        name: c.get("name")?.as_str()?.to_string(),
                        rgba: Arc::new(decode_png(&png)?),
                    })
                })
                .collect()
        })
        .unwrap_or_default();
    (picks, capes)
}

/// What an account sends at login (`cape_v1`) and wears in the world.
pub(crate) struct Worn {
    pub wire: Vec<u8>,
    pub image: Option<RgbaImage>,
}

pub(crate) fn for_account(name: &str) -> Worn {
    let mut list = CapeList::default();
    list.load();
    let index = list.index_for(name);
    if index >= list.custom.len() {
        let id = (index - list.custom.len()) as u32;
        let mut wire = vec![1];
        wire.extend(id.to_be_bytes());
        let image = (id != 0)
            .then(|| RgbaImage::from_raw(64, 64, preset_rgba(id as usize)))
            .flatten()
            .map(to_cape_texture);
        return Worn { wire, image };
    }
    let rgba = list.custom[index].rgba.clone();
    // The server's custom cape format: 23x17 pixels, blue, green, red (no alpha). The 22x17
    // cape, then one column of the elytra (x 22, y 11..22) in the last column, rows 6..17.
    let px = |x: usize, y: usize| {
        let i = (y * 64 + x) * 4;
        if rgba[i + 3] == 0 { [0, 0, 0] } else { [rgba[i + 2], rgba[i + 1], rgba[i]] }
    };
    let mut wire = vec![2];
    let mut cape = vec![0u8; 23 * 17 * 3];
    for y in 0..17 {
        for x in 0..22 {
            cape[(y * 23 + x) * 3..][..3].copy_from_slice(&px(x, y));
        }
    }
    for y in 0..11 {
        cape[((y + 6) * 23 + 22) * 3..][..3].copy_from_slice(&px(22, y + 11));
    }
    wire.extend(cape);
    let image = RgbaImage::from_raw(64, 64, rgba.to_vec()).map(to_cape_texture);
    Worn { wire, image }
}

/// A 64x64 picture back to a 64x32 cape texture.
fn to_cape_texture(img: RgbaImage) -> RgbaImage {
    image::imageops::crop_imm(&img, 0, 0, 64, 32).to_image()
}
