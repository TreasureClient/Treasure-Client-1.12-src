use crate::gui::painter::Painter;

// Treasure Client: menus use the theme's background picture instead of dirt.
pub fn background(p: &mut Painter, vw: f32, vh: f32) {
    crate::gui::tc_style::background(p, vw, vh);
}

pub fn dirt_background(p: &mut Painter, vw: f32, vh: f32) {
    crate::gui::tc_style::background(p, vw, vh);
}

// Treasure Client: lists sit on a dark see-through panel with accent lines above and below.
pub fn list_background(p: &mut Painter, x: f32, y: f32, w: f32, h: f32, _scroll: f32) {
    p.fill(x, y, w, h, 0x7800_0000);
    let line = (crate::gui::tc_style::accent() & 0x00FF_FFFF) | 0x9000_0000;
    p.fill(x, y - 1.0, w, 1.0, line);
    p.fill(x, y + h, w, 1.0, line);
}
