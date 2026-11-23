//! Treasure Client cosmetics: animated capes on the player model (the pictures come from
//! `crate::tc_cos::capeart`, the same designs as the JS and WASM editions).
//!
//! A cape's 64 pictures are drawn into one texture the first time someone wears it (a few
//! pictures per frame, about 3 ms, one cape at a time; until then the normal cape shows). Each
//! player then gets their own copy of the cape mesh whose texture coordinates are pointed at the
//! current picture, 16 times a second. Nothing is redrawn or uploaded while you play.

use std::collections::HashMap;

use bevy::asset::RenderAssetUsages;
use bevy::prelude::*;

use super::{ItemCtx, LayerQuery, Rig};
use crate::tc_cos::capeart;

/// At most this many different capes are kept.
const MAX_KEPT: usize = 10;
/// Time a frame may spend drawing cape pictures (at least one picture is drawn).
const BUDGET_MS: f64 = 3.0;

struct Entry {
    builder: Option<Box<capeart::Builder>>,
    image: Option<Handle<Image>>,
    last_used: u64,
}

#[derive(Default)]
pub(super) struct CapeCache {
    entries: HashMap<String, Entry>,
    stepped: bool,
}

impl CapeCache {
    /// Call once per frame (only one cape is drawn per frame).
    pub(super) fn begin_frame(&mut self) {
        self.stepped = false;
    }

    /// The cape's texture, or None while it's still being drawn (or unknown).
    fn get(&mut self, id: &str, accent: u32, images: &mut Assets<Image>) -> Option<Handle<Image>> {
        if !capeart::known(id) {
            return None;
        }
        let key = if capeart::uses_theme(id) {
            format!("{id}@{:06x}", accent & 0xFF_FFFF)
        } else {
            id.to_owned()
        };
        let now = crate::tc_cos::now_ms();
        if !self.entries.contains_key(&key) {
            if self.entries.len() >= MAX_KEPT {
                // every kept cape was used a moment ago (lots of different capes around): don't
                // throw one away just to make another, or they'd be redrawn over and over
                let (old_key, old_at) = self
                    .entries
                    .iter()
                    .min_by_key(|(_, e)| e.last_used)
                    .map(|(k, e)| (k.clone(), e.last_used))?;
                if old_at + 1500 > now {
                    return None;
                }
                self.entries.remove(&old_key);
            }
            self.entries.insert(
                key.clone(),
                Entry {
                    builder: Some(Box::new(capeart::Builder::new(id, accent))),
                    image: None,
                    last_used: now,
                },
            );
        }
        let e = self.entries.get_mut(&key)?;
        e.last_used = now;
        if e.image.is_none() {
            if self.stepped {
                return None;
            }
            self.stepped = true;
            let builder = e.builder.as_mut()?;
            let start = now_precise();
            let mut done = builder.step(1);
            while !done && now_precise() - start < BUDGET_MS {
                done = builder.step(1);
            }
            if !done {
                return None;
            }
            let pixels = e.builder.take()?.pixels;
            let mut data = Vec::with_capacity(pixels.len() * 4);
            for c in pixels {
                data.extend_from_slice(&[(c >> 16) as u8, (c >> 8) as u8, c as u8, (c >> 24) as u8]);
            }
            let image = crate::renderer::rgba_image(
                capeart::TEX_W as u32,
                capeart::TEX_H as u32,
                data,
                RenderAssetUsages::RENDER_WORLD,
            );
            e.image = Some(images.add(image));
        }
        e.image.clone()
    }
}

fn now_precise() -> f64 {
    static START: std::sync::OnceLock<crate::platform::time::Instant> = std::sync::OnceLock::new();
    let start = *START.get_or_init(crate::platform::time::Instant::now);
    crate::platform::time::Instant::now().duration_since(start).as_secs_f64() * 1000.0
}

/// Each player's own Treasure Client cape state.
#[derive(Default)]
pub(super) struct CapeTc {
    /// This player's copy of the cape mesh (its texture coordinates point at one picture).
    mesh: Option<Handle<Mesh>>,
    /// The cape mesh's own texture coordinates (for Minecraft's 64x32 cape layout).
    base_uv: Vec<[f32; 2]>,
    frame: Option<usize>,
    image: Option<Handle<Image>>,
    pub(super) on: bool,
}

/// Puts this player's Treasure Client cape on (it takes the place of their normal cape), or
/// gives the normal cape back. Call right after the skin is updated.
pub(super) fn update(
    rig: &mut Rig,
    look: Option<&crate::tc_cos::Look>,
    items: &mut ItemCtx,
    layers: &mut LayerQuery<'_, '_>,
) {
    let want = look.filter(|l| l.has_cape()).map(|l| (l.cape_art_id(), l.accent()));
    let image = want.and_then(|(id, acc)| items.tc_capes.get(&id, acc, items.images));
    let Some(image) = image else {
        if rig.cape.tc.on {
            // back to the normal cape: the skin is applied again next frame
            rig.cape.tc.on = false;
            rig.cape.tc.image = None;
            rig.cape.tc.frame = None;
            if let Ok((_, Some(mut mesh))) = layers.get_mut(rig.cape.node) {
                mesh.0 = items.model.cape.clone();
            }
            rig.seen_textures = rig.seen_textures.wrapping_add(1);
            rig.wings.worn = None;
        }
        return;
    };

    if rig.cape.tc.mesh.is_none() {
        let Some(base) = items.meshes.get(&items.model.cape).cloned() else {
            return;
        };
        rig.cape.tc.base_uv = match base.attribute(Mesh::ATTRIBUTE_UV_0) {
            Some(bevy::mesh::VertexAttributeValues::Float32x2(uv)) => uv.clone(),
            _ => return,
        };
        rig.cape.tc.mesh = Some(items.meshes.add(base));
    }
    let Some(handle) = rig.cape.tc.mesh.clone() else { return };

    let frame = capeart::frame_at(crate::tc_cos::now_ms());
    if rig.cape.tc.frame != Some(frame) {
        rig.cape.tc.frame = Some(frame);
        let (ox, oy) = ((frame % capeart::COLS * capeart::CELL_W) as f32, (frame / capeart::COLS * capeart::CELL_H) as f32);
        let (sw, sh) = (super::super::skin::CAPE_SHEET[0], super::super::skin::CAPE_SHEET[1]);
        let (tw, th) = (capeart::TEX_W as f32, capeart::TEX_H as f32);
        let s = capeart::S as f32;
        let uv: Vec<[f32; 2]> = rig
            .cape
            .tc
            .base_uv
            .iter()
            .map(|[u, v]| [(ox + u * sw * s) / tw, (oy + v * sh * s) / th])
            .collect();
        if let Some(mut mesh) = items.meshes.get_mut(&handle) {
            mesh.insert_attribute(Mesh::ATTRIBUTE_UV_0, uv);
        }
    }

    let fresh = !rig.cape.tc.on || rig.cape.tc.image.as_ref() != Some(&image);
    if fresh {
        rig.cape.tc.on = true;
        rig.cape.tc.image = Some(image.clone());
        if let Ok((_, Some(mut mesh))) = layers.get_mut(rig.cape.node)
            && mesh.0 != handle
        {
            mesh.0 = handle;
        }
    }
    // the skin may have just put the normal cape back on this material
    let current = items.entity_materials.get(&rig.cape.material).and_then(|m| m.texture.clone());
    if current.as_ref() != Some(&image)
        && let Some(mut material) = items.entity_materials.get_mut(&rig.cape.material)
    {
        material.texture = Some(image);
    }
    if !rig.cape.ready || fresh {
        rig.cape.ready = true;
        rig.wings.worn = None; // shows the cape (unless an elytra is worn)
    }
}
