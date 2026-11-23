//! Treasure Client cosmetics: auras and boot trails on players in the world (the shapes are in
//! `crate::tc_cos::auras`, the same as the JS and WASM editions).
//!
//! Each player has one small glowing mesh, built again every frame while they have an aura or a
//! trail to show. It's added on top of the picture (it glows, it never darkens), only the 16
//! nearest players' auras are drawn, and they fade out from 28 to 40 blocks away. Your own isn't
//! drawn in first person (the whole player model is hidden then).

use std::sync::Mutex;

use bevy::asset::RenderAssetUsages;
use bevy::mesh::{Indices, PrimitiveTopology};
use bevy::prelude::*;

use super::{ItemCtx, LayerQuery, PartQuery};
use crate::renderer::anim::AnimSample;
use crate::renderer::entity_material::{EntityMaterial, LightMode, Lit};
use crate::tc_cos::{Look, auras};

const MAX_PER_FRAME: usize = 16;
const FAR: f32 = 40.0;
const FADE_FROM: f32 = 28.0;

pub(super) struct AuraRig {
    node: Entity,
    mesh: Handle<Mesh>,
    material: Handle<EntityMaterial>,
    shown: bool,
    textured: bool,
    trail: auras::Trail,
    scratch: Vec<auras::Glow>,
}

fn empty_mesh() -> Mesh {
    let mut mesh = Mesh::new(PrimitiveTopology::TriangleList, RenderAssetUsages::RENDER_WORLD);
    fill(&mut mesh, &[]);
    mesh
}

/// sRGB to the linear light the renderer adds up.
fn lin(c: f32) -> f32 {
    crate::util::mth::srgb_to_linear(c.clamp(0.0, 1.0))
}

/// Puts the glows into the mesh. The colours are multiplied by their strength already, and the
/// alpha is 0, so the material's blending simply adds them to the picture.
fn fill(mesh: &mut Mesh, glows: &[auras::Glow]) {
    let mut pos = Vec::with_capacity(glows.len() * 4);
    let mut nor = Vec::with_capacity(glows.len() * 4);
    let mut uv = Vec::with_capacity(glows.len() * 4);
    let mut col = Vec::with_capacity(glows.len() * 4);
    let mut idx: Vec<u32> = Vec::with_capacity(glows.len() * 6);
    for g in glows {
        let base = pos.len() as u32;
        let rgb = g.rgb.map(lin);
        for i in 0..4 {
            pos.push(g.corners[i]);
            nor.push([0.0, 1.0, 0.0]);
            uv.push(g.uv[i]);
            // the blending happens in linear light: the strength is turned the same way, so it
            // looks like the browser editions (which add in screen colours)
            let a = lin(g.alpha[i]);
            col.push([rgb[0] * a, rgb[1] * a, rgb[2] * a, 0.0]);
        }
        idx.extend_from_slice(&[base, base + 1, base + 2, base, base + 2, base + 3]);
    }
    mesh.insert_attribute(Mesh::ATTRIBUTE_POSITION, pos);
    mesh.insert_attribute(Mesh::ATTRIBUTE_NORMAL, nor);
    mesh.insert_attribute(Mesh::ATTRIBUTE_UV_0, uv);
    mesh.insert_attribute(Mesh::ATTRIBUTE_COLOR, col);
    mesh.insert_indices(Indices::U32(idx));
}

/// The soft dot picture, made once. Its colour is the dot's shape (the texture is read as sRGB,
/// so the shape is turned into linear light along with the colours) and its alpha is 0.
fn dot_image(images: &mut Assets<Image>) -> Handle<Image> {
    static DOT: Mutex<Option<Handle<Image>>> = Mutex::new(None);
    let mut held = DOT.lock().unwrap_or_else(|e| e.into_inner());
    if let Some(h) = held.as_ref()
        && images.get(h).is_some()
    {
        return h.clone();
    }
    const N: usize = 64;
    let mut data = Vec::with_capacity(N * N * 4);
    for a in auras::dot_alpha(N) {
        let v = (a * 255.0 + 0.5) as u8;
        data.extend_from_slice(&[v, v, v, 0]);
    }
    let mut image = crate::renderer::rgba_image(N as u32, N as u32, data, RenderAssetUsages::RENDER_WORLD);
    // smooth when stretched; clamped so the edges never wrap round
    image.sampler = bevy::image::ImageSampler::linear();
    let handle = images.add(image);
    *held = Some(handle.clone());
    handle
}

pub(super) fn spawn(
    commands: &mut Commands,
    meshes: &mut Assets<Mesh>,
    materials: &mut Assets<EntityMaterial>,
    root: Entity,
) -> AuraRig {
    let material = materials.add(EntityMaterial::new(
        None,
        LightMode::Emissive,
        AlphaMode::Add,
        0.0,
        Lit::full_bright(),
        0,
    ));
    let mesh = meshes.add(empty_mesh());
    let node = commands
        .spawn((
            Mesh3d(mesh.clone()),
            MeshMaterial3d(material.clone()),
            Transform::default(),
            Visibility::Hidden,
            bevy::camera::primitives::Aabb::from_min_max(Vec3::splat(-24.0), Vec3::splat(24.0)),
            super::RigPart,
            Name::new("tc_aura"),
        ))
        .id();
    commands.entity(root).add_child(node);
    AuraRig {
        node,
        mesh,
        material,
        shown: false,
        textured: false,
        trail: auras::Trail::default(),
        scratch: Vec::new(),
    }
}

/// Where the glows are drawn from, for one player this frame.
pub(super) struct Place {
    /// the rig root's transform (the aura is drawn in world directions, so this is undone)
    pub root: Transform,
    /// their feet: where the renderer draws them, and in the world
    pub feet: Vec3,
    pub feet_world: [f64; 3],
    pub entity_id: i32,
}

/// Shows (or hides) this player's aura and boot trail. Call once per frame for every player.
pub(super) fn update(
    rig: &mut AuraRig,
    look: Option<&Look>,
    sample: &AnimSample,
    body_hidden: bool,
    place: Place,
    items: &mut ItemCtx,
    parts: &mut PartQuery<'_, '_>,
    layers: &mut LayerQuery<'_, '_>,
) {
    let now = crate::tc_cos::now_ms();
    let look = look.filter(|l| !body_hidden && !sample.sleeping && (l.has_aura() || l.has_trail()));
    let mut draw = false;
    rig.scratch.clear();
    if let Some(look) = look {
        // footprints are added even when they're too far to draw, so a trail never has gaps
        if look.has_trail() {
            rig.trail.update(look, place.feet_world, sample.body_yaw, sample.on_ground, sample.sprinting, now);
        }
        let eye = items.tc_eye;
        let centre = Vec3::new(
            (place.feet_world[0] - eye.x) as f32,
            (place.feet_world[1] + 1.0 - eye.y) as f32,
            (place.feet_world[2] - eye.z) as f32,
        );
        let dist = centre.length();
        if dist <= FAR && items.tc_auras < MAX_PER_FRAME {
            items.tc_auras += 1;
            let fade = if dist < FADE_FROM { 1.0 } else { (FAR - dist) / (FAR - FADE_FROM) };
            // dots face the camera
            let dir = centre.normalize_or(Vec3::Z);
            let right = dir.cross(Vec3::Y).try_normalize().unwrap_or(Vec3::X);
            let up = right.cross(dir).normalize_or(Vec3::Y);
            if look.has_trail() {
                rig.trail.draw(look, place.feet_world, fade, now, right.to_array(), up.to_array(), &mut rig.scratch);
            }
            auras::aura(look, now, fade, place.entity_id as f32 * 0.618, right.to_array(), up.to_array(), &mut rig.scratch);
            draw = !rig.scratch.is_empty();
        }
    }

    if draw != rig.shown {
        rig.shown = draw;
        if let Ok((mut vis, _)) = layers.get_mut(rig.node) {
            *vis = if draw { Visibility::Inherited } else { Visibility::Hidden };
        }
    }
    if !draw {
        return;
    }
    if !rig.textured {
        rig.textured = true;
        let dot = dot_image(items.images);
        if let Some(mut material) = items.entity_materials.get_mut(&rig.material) {
            material.texture = Some(dot);
        }
    }
    // drawn in world directions from the feet: undo the root's turn, lean and size
    let target = Transform::from_translation(place.feet);
    let local = Transform::from_matrix(place.root.to_matrix().inverse() * target.to_matrix());
    if let Ok(mut transform) = parts.get_mut(rig.node) {
        *transform = local;
    }
    if let Some(mut mesh) = items.meshes.get_mut(&rig.mesh) {
        fill(&mut mesh, &rig.scratch);
    }
}
