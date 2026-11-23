//! Treasure Client cosmetics: hats and boots on the player model (the shapes are in
//! `crate::tc_cos::shapes`, the same designs as the JS and WASM editions).
//!
//! Each of the head and the two legs gets two small meshes: the solid parts (lit like the
//! player) and the glowing parts (gems, stars, flames; they ignore the dark). The meshes are
//! only built again when the look changes, or about 30 times a second while something on them
//! moves (a propeller, flames, a twitching ear).

use std::sync::Arc;

use bevy::asset::RenderAssetUsages;
use bevy::mesh::{Indices, PrimitiveTopology};
use bevy::prelude::*;

use super::{ItemCtx, LayerQuery, PART_HEAD, RigPart};
use crate::renderer::anim::AnimSample;
use crate::renderer::entity_material::{EntityMaterial, LightMode, Lit};
use crate::tc_cos::{Look, shapes};

const PART_RIGHT_LEG: usize = 4;
const PART_LEFT_LEG: usize = 5;
const HEAD_SLOT: usize = 0;
const FEET_SLOT: usize = 3;
/// How often moving hats and boots are built again (ms).
const ANIM_MS: u64 = 33;

pub(super) struct WearRig {
    /// [head, right leg, left leg] x [solid, glow]
    nodes: [[Entity; 2]; 3],
    meshes: [[Handle<Mesh>; 2]; 3],
    pub(super) solid: Handle<EntityMaterial>,
    look: Option<Arc<Look>>,
    built_at: u64,
    active: bool,
    shown: [bool; 3],
}

fn empty_mesh() -> Mesh {
    let mut mesh = Mesh::new(PrimitiveTopology::TriangleList, RenderAssetUsages::RENDER_WORLD);
    fill(&mut mesh, &[], false, [0.0; 3]);
    mesh
}

/// Puts the faces (solid or glowing ones) into a mesh, moved by `shift` (model pixels).
fn fill(mesh: &mut Mesh, quads: &[shapes::Quad], glow: bool, shift: [f32; 3]) {
    let n = quads.iter().filter(|q| q.glow == glow).count();
    let mut pos = Vec::with_capacity(n * 4);
    let mut nor = Vec::with_capacity(n * 4);
    let mut uv = Vec::with_capacity(n * 4);
    let mut col = Vec::with_capacity(n * 4);
    let mut idx: Vec<u32> = Vec::with_capacity(n * 6);
    for q in quads.iter().filter(|q| q.glow == glow) {
        let base = pos.len() as u32;
        for c in q.corners {
            pos.push([c[0] + shift[0], c[1] + shift[1], c[2] + shift[2]]);
            nor.push(q.normal);
            uv.push([0.5, 0.5]);
            col.push([q.rgb[0], q.rgb[1], q.rgb[2], 1.0]);
        }
        idx.extend_from_slice(&[base, base + 1, base + 2, base, base + 2, base + 3]);
    }
    mesh.insert_attribute(Mesh::ATTRIBUTE_POSITION, pos);
    mesh.insert_attribute(Mesh::ATTRIBUTE_NORMAL, nor);
    mesh.insert_attribute(Mesh::ATTRIBUTE_UV_0, uv);
    mesh.insert_attribute(Mesh::ATTRIBUTE_COLOR, col);
    mesh.insert_indices(Indices::U32(idx));
}

pub(super) fn spawn(
    commands: &mut Commands,
    meshes: &mut Assets<Mesh>,
    materials: &mut Assets<EntityMaterial>,
    parts: &[Entity],
    lit: Lit,
) -> WearRig {
    let solid = materials.add(EntityMaterial::cutout(None, LightMode::Cardinal, lit));
    let glow = materials.add(EntityMaterial::cutout(None, LightMode::Emissive, Lit::full_bright()));
    let mut handles: [[Handle<Mesh>; 2]; 3] = Default::default();
    let mut nodes = [[Entity::PLACEHOLDER; 2]; 3];
    for (i, part) in [PART_HEAD, PART_RIGHT_LEG, PART_LEFT_LEG].into_iter().enumerate() {
        for k in 0..2 {
            let mesh = meshes.add(empty_mesh());
            let entity = commands
                .spawn((
                    Mesh3d(mesh.clone()),
                    MeshMaterial3d(if k == 0 { solid.clone() } else { glow.clone() }),
                    Transform::default(),
                    Visibility::Hidden,
                    bevy::camera::primitives::Aabb::from_min_max(Vec3::splat(-32.0), Vec3::splat(32.0)),
                    RigPart,
                    Name::new("tc_wear"),
                ))
                .id();
            commands.entity(parts[part]).add_child(entity);
            handles[i][k] = mesh;
            nodes[i][k] = entity;
        }
    }
    WearRig {
        nodes,
        meshes: handles,
        solid,
        look: None,
        built_at: 0,
        active: false,
        shown: [false; 3],
    }
}

/// Puts this player's hat and boots on (or takes them off). Call after the armor is updated.
pub(super) fn update(
    rig: &mut WearRig,
    look: Option<Arc<Look>>,
    sample: &AnimSample,
    body_hidden: bool,
    items: &mut ItemCtx,
    layers: &mut LayerQuery<'_, '_>,
) {
    let look = look.filter(|l| l.has_hat() || l.has_boots());
    let hat_ok = !body_hidden && sample.armor.items[HEAD_SLOT].is_none() && sample.head.is_empty();
    let boots_ok = !body_hidden && sample.armor.items[FEET_SLOT].is_none();
    let want = match &look {
        Some(l) => [hat_ok && l.has_hat(), boots_ok && l.has_boots(), boots_ok && l.has_boots()],
        None => [false; 3],
    };
    for i in 0..3 {
        if want[i] != rig.shown[i] {
            rig.shown[i] = want[i];
            for entity in rig.nodes[i] {
                if let Ok((mut vis, _)) = layers.get_mut(entity) {
                    *vis = if want[i] {
                        Visibility::Inherited
                    } else {
                        Visibility::Hidden
                    };
                }
            }
        }
    }
    let Some(look) = look else {
        rig.look = None;
        return;
    };
    if !want.iter().any(|w| *w) {
        return;
    }
    let ms = crate::tc_cos::now_ms();
    let active = sample.sprinting || sample.fall_flying || sample.flying_y_rot.is_some();
    let changed = rig.look.as_ref().is_none_or(|old| **old != *look) || active != rig.active;
    if !changed && !(shapes::animated(&look) && ms.saturating_sub(rig.built_at) >= ANIM_MS) {
        return;
    }
    rig.look = Some(look.clone());
    rig.active = active;
    rig.built_at = ms;
    let t = (ms % 3_600_000) as f32 / 1000.0;
    let built = [
        shapes::hat_of(&look, t, ms),
        shapes::boot_of(&look, t, ms, -1.0, active),
        shapes::boot_of(&look, t, ms, 1.0, active),
    ];
    for (i, s) in built.iter().enumerate() {
        if !want[i] {
            continue;
        }
        for (k, glow) in [false, true].into_iter().enumerate() {
            if let Some(mut mesh) = items.meshes.get_mut(&rig.meshes[i][k]) {
                fill(&mut mesh, &s.quads, glow, [0.0; 3]);
            }
        }
    }
}
