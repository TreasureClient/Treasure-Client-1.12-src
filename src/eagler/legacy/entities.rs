//! Treasure Client: 1.12.2 players, mobs and objects, turned into modern entities.
//!
//! Covers spawning, moving, turning, teleporting, removing, swinging, hurt/status
//! events, the tab list (which also carries player and NPC skins), and the basic
//! metadata every entity shares (invisible, on fire, sneaking, custom name), so
//! hologram armor stands stay invisible and NPCs show their names. Held items, armor
//! and type-specific metadata come with inventories (stage 5).

use std::sync::Arc;

use azalea::entity::{EntityDataItem, EntityDataValue, EntityMetadataItems, LookDirection};
use azalea_auth::game_profile::{GameProfile, GameProfileProperties, ProfilePropertyValue};
use azalea_chat::FormattedText;
use azalea_chat::text_component::TextComponent;
use azalea_core::delta::{LpVec3, PositionDelta8};
use azalea_core::entity_id::MinecraftEntityId;
use azalea_core::game_type::GameMode;
use azalea_core::position::Vec3;
use azalea_protocol::common::movements::PositionMoveRotation;
use azalea_protocol::packets::game::{
    ClientboundAddEntity, ClientboundAnimate, ClientboundEntityEvent,
    ClientboundEntityPositionSync, ClientboundMoveEntityPos, ClientboundMoveEntityPosRot,
    ClientboundMoveEntityRot, ClientboundPlayerInfoRemove, ClientboundPlayerInfoUpdate,
    ClientboundRemoveEntities, ClientboundRotateHead, ClientboundSetEntityData,
    ClientboundSetEntityMotion,
    c_animate::AnimationAction,
    c_move_entity_pos_rot::CompactLookDirection,
    c_player_info_update::{ActionEnumSet, PlayerInfoEntry},
};
use azalea_registry::builtin::EntityKind;
use uuid::Uuid;

use super::game;
use super::wire::In;

/// 1.12.2 clientbound entity packet ids.
pub(super) mod cb {
    pub const SPAWN_OBJECT: i32 = 0x00;
    pub const SPAWN_MOB: i32 = 0x03;
    pub const SPAWN_PLAYER: i32 = 0x05;
    pub const ANIMATION: i32 = 0x06;
    pub const ENTITY_STATUS: i32 = 0x1B;
    pub const REL_MOVE: i32 = 0x26;
    pub const LOOK_REL_MOVE: i32 = 0x27;
    pub const LOOK: i32 = 0x28;
    pub const PLAYER_LIST: i32 = 0x2E;
    pub const DESTROY: i32 = 0x32;
    pub const HEAD_LOOK: i32 = 0x36;
    pub const METADATA: i32 = 0x3C;
    pub const VELOCITY: i32 = 0x3E;
    pub const TELEPORT: i32 = 0x4C;
    pub const TEAMS: i32 = 0x44;
    pub const EQUIPMENT: i32 = 0x3F;
}

fn id(raw: i32) -> MinecraftEntityId {
    MinecraftEntityId(raw)
}

fn look(yaw: i8, pitch: i8) -> CompactLookDirection {
    CompactLookDirection { y_rot: yaw, x_rot: pitch }
}

fn angle(byte: i8) -> f32 {
    f32::from(byte) * 360.0 / 256.0
}

/// 1.12 velocities are 1/8000 of a block per tick.
fn velocity(r: &mut In) -> Option<LpVec3> {
    let x = f64::from(r.i16()?) / 8000.0;
    let y = f64::from(r.i16()?) / 8000.0;
    let z = f64::from(r.i16()?) / 8000.0;
    Some(LpVec3::from(Vec3::new(x, y, z)))
}

fn uuid(r: &mut In) -> Option<Uuid> {
    let hi = r.i64()? as u64;
    let lo = r.i64()? as u64;
    Some(Uuid::from_u64_pair(hi, lo))
}

fn add(out: &mut Vec<Vec<u8>>, raw: i32, uuid: Uuid, kind: EntityKind, pos: Vec3, rot: (i8, i8, i8), motion: LpVec3, data: i32) {
    game(out, ClientboundAddEntity {
        id: id(raw),
        uuid,
        entity_type: kind,
        modded_type: None,
        position: pos,
        movement: motion,
        x_rot: rot.1,
        y_rot: rot.0,
        y_head_rot: rot.2,
        data,
    });
}

/// Translates one entity packet; false when `id` is not an entity packet.
pub(super) fn translate(
    id_: i32,
    r: &mut In,
    out: &mut Vec<Vec<u8>>,
    items: &super::items::Ctx,
) -> Option<bool> {
    match id_ {
        cb::SPAWN_PLAYER => {
            let raw = r.varint()?;
            let uuid = uuid(r)?;
            let pos = Vec3::new(r.f64()?, r.f64()?, r.f64()?);
            let yaw = r.i8()?;
            let pitch = r.i8()?;
            crate::log_info!("net", "1.12: player or NPC #{raw} at {:.1} {:.1} {:.1}", pos.x, pos.y, pos.z);
            add(out, raw, uuid, EntityKind::Player, pos, (yaw, pitch, yaw), LpVec3::default(), 0);
            metadata(raw, r, out, items);
        }
        cb::SPAWN_MOB => {
            let raw = r.varint()?;
            let uuid = uuid(r)?;
            let kind = mob(r.varint()?);
            let pos = Vec3::new(r.f64()?, r.f64()?, r.f64()?);
            let yaw = r.i8()?;
            let pitch = r.i8()?;
            let head = r.i8()?;
            let motion = velocity(r)?;
            if let Some(kind) = kind {
                crate::log_info!("net", "1.12: {kind:?} #{raw} at {:.1} {:.1} {:.1}", pos.x, pos.y, pos.z);
                let (mut meta, name) = read_metadata(r, items);
                // Treasure Client: ViaMobs, a ViaBackwards stand-in spawns as the real mob.
                let real = super::via_mobs::on_spawn(raw, kind, uuid, pos, (yaw, pitch, head), name.as_deref());
                if real.is_some() {
                    without_name(&mut meta);
                }
                add(out, raw, uuid, real.unwrap_or(kind), pos, (yaw, pitch, head), motion, 0);
                emit_metadata(raw, meta, out);
            }
        }
        cb::SPAWN_OBJECT => {
            let raw = r.varint()?;
            let uuid = uuid(r)?;
            let kind = r.u8()?;
            let pos = Vec3::new(r.f64()?, r.f64()?, r.f64()?);
            let pitch = r.i8()?;
            let yaw = r.i8()?;
            let data = r.i32()?;
            let motion = velocity(r)?;
            if let Some((kind, data)) = object(kind, data) {
                add(out, raw, uuid, kind, pos, (yaw, pitch, yaw), motion, data);
            }
        }
        cb::REL_MOVE => {
            let raw = r.varint()?;
            let delta = PositionDelta8 { xa: r.i16()?, ya: r.i16()?, za: r.i16()? };
            super::via_mobs::moved(raw, (delta.xa, delta.ya, delta.za));
            let on_ground = r.bool()?;
            game(out, ClientboundMoveEntityPos { entity_id: id(raw), delta, on_ground });
        }
        cb::LOOK_REL_MOVE => {
            let raw = r.varint()?;
            let delta = PositionDelta8 { xa: r.i16()?, ya: r.i16()?, za: r.i16()? };
            super::via_mobs::moved(raw, (delta.xa, delta.ya, delta.za));
            let yaw = r.i8()?;
            let pitch = r.i8()?;
            let on_ground = r.bool()?;
            game(out, ClientboundMoveEntityPosRot {
                entity_id: id(raw),
                delta,
                look_direction: look(yaw, pitch),
                on_ground,
            });
        }
        cb::LOOK => {
            let raw = r.varint()?;
            let yaw = r.i8()?;
            let pitch = r.i8()?;
            let on_ground = r.bool()?;
            game(out, ClientboundMoveEntityRot {
                entity_id: id(raw),
                look_direction: look(yaw, pitch),
                on_ground,
            });
        }
        cb::TELEPORT => {
            let raw = r.varint()?;
            let pos = Vec3::new(r.f64()?, r.f64()?, r.f64()?);
            let yaw = r.i8()?;
            let pitch = r.i8()?;
            super::via_mobs::teleported(raw, pos, yaw, pitch);
            let on_ground = r.bool()?;
            game(out, ClientboundEntityPositionSync {
                id: id(raw),
                values: PositionMoveRotation {
                    pos,
                    delta: Vec3::ZERO,
                    look_direction: LookDirection::new(angle(yaw), angle(pitch)),
                },
                on_ground,
            });
        }
        cb::HEAD_LOOK => {
            let raw = r.varint()?;
            let head = r.i8()?;
            game(out, ClientboundRotateHead { entity_id: id(raw), y_head_rot: head });
        }
        cb::DESTROY => {
            let count = usize::try_from(r.varint()?).ok()?;
            let mut ids = Vec::with_capacity(count.min(1024));
            for _ in 0..count {
                let raw = r.varint()?;
                super::via_mobs::removed(raw);
                ids.push(id(raw));
            }
            game(out, ClientboundRemoveEntities { entity_ids: ids });
        }
        cb::VELOCITY => {
            let raw = r.varint()?;
            let delta = velocity(r)?;
            game(out, ClientboundSetEntityMotion { id: id(raw), delta });
        }
        cb::ANIMATION => {
            let raw = r.varint()?;
            let action = match r.u8()? {
                0 => AnimationAction::SwingMainHand,
                1 => AnimationAction::Hurt,
                2 => AnimationAction::WakeUp,
                3 => AnimationAction::SwingOffHand,
                4 => AnimationAction::CriticalHit,
                5 => AnimationAction::MagicCriticalHit,
                _ => return Some(true),
            };
            game(out, ClientboundAnimate { id: id(raw), action });
        }
        cb::ENTITY_STATUS => {
            let raw = r.i32()?;
            let event_id = r.u8()?;
            game(out, ClientboundEntityEvent { entity_id: id(raw), event_id });
        }
        cb::METADATA => {
            let raw = r.varint()?;
            let (mut meta, name) = read_metadata(r, items);
            match super::via_mobs::on_metadata(raw, name.as_deref()) {
                super::via_mobs::Update::Keep => {}
                super::via_mobs::Update::HideName => without_name(&mut meta),
                super::via_mobs::Update::Swap(tr) => {
                    // its name only came now: replace the stand-in with the real mob
                    without_name(&mut meta);
                    game(out, ClientboundRemoveEntities { entity_ids: vec![id(raw)] });
                    let real = tr.real.unwrap_or(tr.kind);
                    add(out, raw, tr.uuid, real, tr.pos, tr.rot, LpVec3::default(), 0);
                }
            }
            emit_metadata(raw, meta, out);
        }
        cb::PLAYER_LIST => player_list(r, out)?,
        cb::EQUIPMENT => {
            use azalea_inventory::components::EquipmentSlot;
            use azalea_protocol::packets::game::{
                ClientboundSetEquipment, c_set_equipment::EquipmentSlots,
            };
            let raw = r.varint()?;
            let slot = EquipmentSlot::from_byte(r.varint()?.clamp(0, 5) as u8)?;
            let item = super::items::read(r, items)?.item;
            game(out, ClientboundSetEquipment {
                entity_id: id(raw),
                slots: EquipmentSlots { slots: vec![(slot, item)] },
            });
        }
        cb::TEAMS => teams(r, out)?,
        _ => return Some(false),
    }
    Some(true)
}

/// 1.12 metadata: the shared part (indices 0-5 are the same in both versions), and the
/// item of item entities, item frames, potions and fireworks (1.12 index 6, modern 8).
fn metadata(raw: i32, r: &mut In, out: &mut Vec<Vec<u8>>, ctx: &super::items::Ctx) {
    let (items, _) = read_metadata(r, ctx);
    emit_metadata(raw, items, out);
}

/// Sends translated metadata, if there is any.
fn emit_metadata(raw: i32, items: Vec<EntityDataItem>, out: &mut Vec<Vec<u8>>) {
    if !items.is_empty() {
        game(out, ClientboundSetEntityData { id: id(raw), packed_items: EntityMetadataItems(items) });
    }
}

/// The custom name (index 2) and whether it shows (index 3) are left out: ViaMobs hides
/// the name of a mob it turned into its modern self.
fn without_name(items: &mut Vec<EntityDataItem>) {
    items.retain(|i| i.index != 2 && i.index != 3);
}

/// Reads 1.12 metadata: the translated items and the raw custom name, if one was sent.
fn read_metadata(r: &mut In, ctx: &super::items::Ctx) -> (Vec<EntityDataItem>, Option<String>) {
    let mut name_raw = None;
    let mut items = Vec::new();
    loop {
        let Some(index) = r.u8() else { break };
        if index == 0xFF {
            break;
        }
        let Some(kind) = r.varint() else { break };
        if index > 5 {
            if kind == 5 {
                let Some(slot) = super::items::read(r, ctx) else { break };
                items.push(EntityDataItem { index: 8, value: EntityDataValue::ItemStack(slot.item) });
            } else if skip_value(r, kind).is_none() {
                break;
            }
            continue;
        }
        let value = match (index, kind) {
            (0, 0) => r.u8().map(EntityDataValue::Byte),
            (1, 1) => r.varint().map(EntityDataValue::Int),
            (2, 3) => r.string().map(|name| {
                name_raw = Some(name.clone());
                EntityDataValue::OptionalFormattedText(
                    (!name.is_empty()).then(|| Box::new(FormattedText::Text(TextComponent::new(name)))),
                )
            }),
            (3 | 4 | 5, 6) => r.bool().map(EntityDataValue::Boolean),
            _ => None,
        };
        let Some(value) = value else { break };
        items.push(EntityDataItem { index, value });
    }
    (items, name_raw)
}

/// Steps over a 1.12 metadata value this translator does not use.
fn skip_value(r: &mut In, kind: i32) -> Option<()> {
    match kind {
        0 => drop(r.u8()?),
        1 | 10 | 12 => drop(r.varint()?),
        2 => drop(r.f32()?),
        3 | 4 => drop(r.string()?),
        6 => drop(r.bool()?),
        7 => drop(r.bytes(12)?),
        8 => drop(r.i64()?),
        9 => {
            if r.bool()? {
                r.i64()?;
            }
        }
        11 => {
            if r.bool()? {
                r.bytes(16)?;
            }
        }
        13 => drop(super::items::read_root(r)?),
        _ => return None,
    }
    Some(())
}

fn player_list(r: &mut In, out: &mut Vec<Vec<u8>>) -> Option<()> {
    let action = r.varint()?;
    let count = usize::try_from(r.varint()?).ok()?;
    if action == 4 {
        let mut ids = Vec::with_capacity(count.min(1024));
        for _ in 0..count {
            ids.push(uuid(r)?);
        }
        game(out, ClientboundPlayerInfoRemove { profile_ids: ids });
        return Some(());
    }
    let mut actions = ActionEnumSet {
        add_player: false,
        initialize_chat: false,
        update_game_mode: false,
        update_listed: false,
        update_latency: false,
        update_display_name: false,
        update_list_order: false,
        update_hat: false,
    };
    let mut entries = Vec::with_capacity(count.min(1024));
    for _ in 0..count {
        let uuid = uuid(r)?;
        let mut entry = PlayerInfoEntry {
            profile: GameProfile {
                uuid,
                name: String::new(),
                properties: Arc::new(GameProfileProperties::default()),
            },
            chat_session: None,
            game_mode: GameMode::Survival,
            listed: true,
            latency: 0,
            display_name: None,
            list_order: 0,
            update_hat: true,
        };
        match action {
            0 => {
                entry.profile.name = r.string()?;
                let mut properties = GameProfileProperties::default();
                for _ in 0..r.varint()? {
                    let name = r.string()?;
                    let value = r.string()?;
                    let signature = if r.bool()? { Some(r.string()?) } else { None };
                    properties.map.insert(name, ProfilePropertyValue { value, signature });
                }
                entry.profile.properties = Arc::new(properties);
                entry.game_mode = game_mode(r.varint()?);
                entry.latency = r.varint()?;
                entry.display_name = display_name(r)?;
                actions.add_player = true;
                actions.update_game_mode = true;
                actions.update_listed = true;
                actions.update_latency = true;
                actions.update_display_name = true;
            }
            1 => {
                entry.game_mode = game_mode(r.varint()?);
                actions.update_game_mode = true;
            }
            2 => {
                entry.latency = r.varint()?;
                actions.update_latency = true;
            }
            3 => {
                entry.display_name = display_name(r)?;
                actions.update_display_name = true;
            }
            _ => return Some(()),
        }
        entries.push(entry);
    }
    game(out, ClientboundPlayerInfoUpdate { actions, entries });
    Some(())
}

/// Teams carry name tag colours, prefixes and hiding (NPCs use them to hide their
/// internal names).
fn teams(r: &mut In, out: &mut Vec<Vec<u8>>) -> Option<()> {
    use azalea_chat::style::ChatFormatting;
    use azalea_protocol::packets::game::ClientboundSetPlayerTeam;
    use azalea_protocol::packets::game::c_set_player_team::{
        CollisionRule, Method, NameTagVisibility, Parameters,
    };
    let legacy = |s: String| FormattedText::Text(TextComponent::new(s));
    let name = r.string()?;
    let mode = r.u8()?;
    let parameters = |r: &mut In| -> Option<Parameters> {
        let display_name = legacy(r.string()?);
        let player_prefix = legacy(r.string()?);
        let player_suffix = legacy(r.string()?);
        let options = r.u8()?;
        let nametag_visibility = match r.string()?.as_str() {
            "never" => NameTagVisibility::Never,
            "hideForOtherTeams" => NameTagVisibility::HideForOtherTeams,
            "hideForOwnTeam" => NameTagVisibility::HideForOwnTeam,
            _ => NameTagVisibility::Always,
        };
        let collision_rule = match r.string()?.as_str() {
            "never" => CollisionRule::Never,
            "pushOtherTeams" => CollisionRule::PushOtherTeams,
            "pushOwnTeam" => CollisionRule::PushOwnTeam,
            _ => CollisionRule::Always,
        };
        let color = usize::try_from(r.i8()?)
            .ok()
            .and_then(|c| ChatFormatting::FORMATTERS.get(c).copied())
            .filter(|c| (*c as usize) < 16);
        Some(Parameters {
            display_name,
            player_prefix,
            player_suffix,
            nametag_visibility,
            collision_rule,
            color,
            options,
        })
    };
    let players = |r: &mut In| -> Option<Vec<String>> {
        let count = usize::try_from(r.varint()?).ok()?;
        (0..count).map(|_| r.string()).collect()
    };
    let method = match mode {
        0 => {
            let p = parameters(r)?;
            Method::Add((p, players(r)?))
        }
        1 => Method::Remove,
        2 => Method::Change(parameters(r)?),
        3 => Method::Join(players(r)?),
        4 => Method::Leave(players(r)?),
        _ => return Some(()),
    };
    game(out, ClientboundSetPlayerTeam { name, method });
    Some(())
}

fn display_name(r: &mut In) -> Option<Option<Box<FormattedText>>> {
    Some(if r.bool()? { Some(Box::new(super::text(&r.string()?))) } else { None })
}

fn game_mode(mode: i32) -> GameMode {
    super::game_mode_of(mode.clamp(0, 3) as u8)
}

/// 1.12 living entity type ids.
fn mob(kind: i32) -> Option<EntityKind> {
    use EntityKind as E;
    Some(match kind {
        4 => E::ElderGuardian,
        5 => E::WitherSkeleton,
        6 => E::Stray,
        23 => E::Husk,
        27 => E::ZombieVillager,
        28 => E::SkeletonHorse,
        29 => E::ZombieHorse,
        30 => E::ArmorStand,
        31 => E::Donkey,
        32 => E::Mule,
        34 => E::Evoker,
        35 => E::Vex,
        36 => E::Vindicator,
        37 => E::Illusioner,
        50 => E::Creeper,
        51 => E::Skeleton,
        52 => E::Spider,
        53 => E::Giant,
        54 => E::Zombie,
        55 => E::Slime,
        56 => E::Ghast,
        57 => E::ZombifiedPiglin,
        58 => E::Enderman,
        59 => E::CaveSpider,
        60 => E::Silverfish,
        61 => E::Blaze,
        62 => E::MagmaCube,
        63 => E::EnderDragon,
        64 => E::Wither,
        65 => E::Bat,
        66 => E::Witch,
        67 => E::Endermite,
        68 => E::Guardian,
        69 => E::Shulker,
        90 => E::Pig,
        91 => E::Sheep,
        92 => E::Cow,
        93 => E::Chicken,
        94 => E::Squid,
        95 => E::Wolf,
        96 => E::Mooshroom,
        97 => E::SnowGolem,
        98 => E::Ocelot,
        99 => E::IronGolem,
        100 => E::Horse,
        101 => E::Rabbit,
        102 => E::PolarBear,
        103 => E::Llama,
        105 => E::Parrot,
        120 => E::Villager,
        _ => return None,
    })
}

/// 1.12 object type ids, and their extra data turned into the modern meaning.
fn object(kind: u8, data: i32) -> Option<(EntityKind, i32)> {
    use EntityKind as E;
    Some(match kind {
        1 => (E::OakBoat, 0),
        2 => (E::Item, 0),
        3 => (E::AreaEffectCloud, 0),
        10 => (
            match data {
                1 => E::ChestMinecart,
                2 => E::FurnaceMinecart,
                3 => E::TntMinecart,
                4 => E::SpawnerMinecart,
                5 => E::HopperMinecart,
                6 => E::CommandBlockMinecart,
                _ => E::Minecart,
            },
            0,
        ),
        50 => (E::Tnt, 0),
        51 => (E::EndCrystal, 0),
        60 => (E::Arrow, data),
        61 => (E::Snowball, 0),
        62 => (E::Egg, 0),
        63 => (E::Fireball, 0),
        64 => (E::SmallFireball, 0),
        65 => (E::EnderPearl, 0),
        66 => (E::WitherSkull, 0),
        67 => (E::ShulkerBullet, 0),
        68 => (E::LlamaSpit, 0),
        // 1.12 packs the falling block as `id | meta << 12`.
        70 => {
            let global = ((data & 0xFFF) << 4 | (data >> 12) & 0xF) as u32;
            (E::FallingBlock, i32::from(super::chunk::state(global).id()))
        }
        // 1.12 item frames face 0 south, 1 west, 2 north, 3 east.
        71 => (E::ItemFrame, match data { 0 => 3, 1 => 4, 2 => 2, _ => 5 }),
        72 => (E::EyeOfEnder, 0),
        73 => (E::SplashPotion, 0),
        75 => (E::ExperienceBottle, 0),
        76 => (E::FireworkRocket, 0),
        77 => (E::LeashKnot, 0),
        78 => (E::ArmorStand, 0),
        79 => (E::EvokerFangs, 0),
        90 => (E::FishingBobber, data),
        91 => (E::SpectralArrow, data),
        93 => (E::DragonFireball, 0),
        _ => return None,
    })
}

/// Treasure Client: TuffX+ "ViaEntities" (`viaentities:data`), the same messages as the JS
/// and WASM editions. This engine has every modern mob, so each one the server streams is
/// shown as itself. `INIT_PALETTE` (int n, n x UTF type), `SPAWN_ENTITY` (int id, short
/// palette index, double x/y/z, float yaw/pitch), `DESTROY_ENTITY` (int id).
pub(super) fn tuff_entities(data: &[u8], out: &mut Vec<Vec<u8>>) {
    use std::str::FromStr as _;
    static PALETTE: std::sync::Mutex<Vec<Option<EntityKind>>> = std::sync::Mutex::new(Vec::new());

    struct Buf<'a>(&'a [u8], usize);
    impl Buf<'_> {
        fn take<const N: usize>(&mut self) -> Option<[u8; N]> {
            let s = self.0.get(self.1..self.1 + N)?;
            self.1 += N;
            s.try_into().ok()
        }
        fn utf(&mut self) -> Option<String> {
            let n = usize::from(u16::from_be_bytes(self.take::<2>()?));
            let s = self.0.get(self.1..self.1 + n)?;
            self.1 += n;
            Some(String::from_utf8_lossy(s).into_owned())
        }
        fn int(&mut self) -> Option<i32> {
            self.take::<4>().map(i32::from_be_bytes)
        }
        fn f64(&mut self) -> Option<f64> {
            self.take::<8>().map(f64::from_be_bytes)
        }
        fn f32(&mut self) -> Option<f32> {
            self.take::<4>().map(f32::from_be_bytes)
        }
    }

    if !crate::tc_mods::get().via {
        return;
    }
    let mut b = Buf(data, 0);
    let _ = (|| -> Option<()> {
        match b.utf()?.as_str() {
            "INIT_PALETTE" => {
                let n = b.int()?.max(0);
                let mut list = Vec::new();
                for _ in 0..n {
                    list.push(EntityKind::from_str(&b.utf()?).ok());
                }
                *PALETTE.lock().unwrap() = list;
            }
            "SPAWN_ENTITY" => {
                let raw = b.int()?;
                let index = i16::from_be_bytes(b.take::<2>()?);
                let pos = Vec3::new(b.f64()?, b.f64()?, b.f64()?);
                let (yaw, pitch) = (b.f32()?, b.f32()?);
                let kind = usize::try_from(index).ok().and_then(|i| PALETTE.lock().unwrap().get(i).copied().flatten())?;
                let angle = |a: f32| (a * 256.0 / 360.0).round() as i32 as i8;
                let uuid = Uuid::from_u128(0x7463_u128 << 112 | u128::from(raw as u32));
                add(out, raw, uuid, kind, pos, (angle(yaw), angle(pitch), angle(yaw)), LpVec3::default(), 0);
            }
            "DESTROY_ENTITY" => {
                let raw = b.int()?;
                game(out, ClientboundRemoveEntities { entity_ids: vec![id(raw)] });
            }
            _ => {} // metadata and animations: ignored, as in the WASM edition
        }
        Some(())
    })();
}
