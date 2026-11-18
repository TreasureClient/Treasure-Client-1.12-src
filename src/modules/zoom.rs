use std::sync::atomic::{AtomicBool, Ordering};

use super::registry::{Id, zoom as setting};
use super::store;

static TOUCH: AtomicBool = AtomicBool::new(false);

#[cfg(feature = "mobile_ui")]
pub fn set_touch(on: bool) {
    TOUCH.store(on, Ordering::Relaxed);
}

#[derive(Clone, Copy)]
pub struct Settings {
    pub on: bool,
    pub fov: f32,
    pub duration: f32,
}

pub fn settings() -> Settings {
    let s = store();
    let mut settings = Settings {
        on: s.enabled(Id::Zoom) || TOUCH.load(Ordering::Relaxed),
        fov: s.num(setting::FOV),
        duration: s.num(setting::DURATION).max(0.001),
    };
    // Treasure Client: the menu's Zoom mod (its key, strength and smooth zoom).
    if tc_zoom() {
        let tc = crate::tc_mods::get();
        settings.on = true;
        settings.fov = tc_base_fov() * tc.zoom_level / 100.0;
        settings.duration = if tc.smooth_zoom { (1.2 / tc.zoom_speed).max(0.02) } else { 0.001 };
    }
    settings
}

static BASE_FOV: std::sync::atomic::AtomicU32 = std::sync::atomic::AtomicU32::new(70);

/// The FOV setting, so the Treasure Client zoom strength is a share of it.
pub fn set_base_fov(fov: u32) {
    BASE_FOV.store(fov, Ordering::Relaxed);
}

fn tc_base_fov() -> f32 {
    BASE_FOV.load(Ordering::Relaxed) as f32
}

/// Treasure Client: the Zoom key of the Treasure Client menu (held, with the Zoom mod on).
fn tc_zoom() -> bool {
    #[cfg(target_arch = "wasm32")]
    {
        crate::gui::tc_bridge::zooming()
    }
    #[cfg(not(target_arch = "wasm32"))]
    {
        false
    }
}
