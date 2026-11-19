//! Treasure Client: the rest of a 1.12.2 world. Sounds, particles, world events,
//! scoreboards, boss bars, the tab list header, potion effects, attributes, block break
//! progress, explosions, the world border, item pickups, deaths, passengers and signs.

use std::collections::HashMap;
use std::str::FromStr;
use std::sync::OnceLock;

use azalea::entity::MobEffectData;
use azalea::entity::particle::{
    BlockParticle, ColorParticle, DustParticle, ItemParticle, Particle, SpellParticle,
};
use azalea_chat::FormattedText;
use azalea_core::color::{ArgbColor, RgbColor};
use azalea_core::entity_id::MinecraftEntityId;
use azalea_core::position::{BlockPos, Vec3};
use azalea_core::sound::CustomSound;
use azalea_protocol::packets::game::c_boss_event::{
    AddOperation, BossBarColor, BossBarOverlay, Operation, Properties, Style,
};
use azalea_protocol::packets::game::c_set_display_objective::DisplaySlot;
use azalea_protocol::packets::game::c_set_objective::Method;
use azalea_protocol::packets::game::c_sound::SoundSource;
use azalea_protocol::packets::game::c_update_attributes::AttributeSnapshot;
use azalea_protocol::packets::game::{
    ClientboundBlockDestruction, ClientboundBossEvent, ClientboundExplode,
    ClientboundInitializeBorder, ClientboundLevelEvent, ClientboundLevelParticles,
    ClientboundOpenSignEditor, ClientboundPlayerCombatKill, ClientboundRemoveMobEffect,
    ClientboundResetScore, ClientboundSetBorderCenter, ClientboundSetBorderLerpSize,
    ClientboundSetBorderSize, ClientboundSetBorderWarningDelay,
    ClientboundSetBorderWarningDistance, ClientboundSetDisplayObjective, ClientboundSetObjective,
    ClientboundSetPassengers, ClientboundSetScore, ClientboundSound, ClientboundTabList,
    ClientboundTakeItemEntity, ClientboundUpdateAttributes, ClientboundUpdateMobEffect,
};
use azalea_registry::builtin::{Attribute, MobEffect, SoundEvent};
use azalea_registry::identifier::Identifier;
use azalea_registry::{Holder, Registry};
use uuid::Uuid;

use super::wire::In;
use super::{chunk, game, items, text};

mod cb {
    pub const BLOCK_BREAK: i32 = 0x08;
    pub const BOSS_BAR: i32 = 0x0C;
    pub const NAMED_SOUND: i32 = 0x19;
    pub const EXPLOSION: i32 = 0x1C;
    pub const WORLD_EVENT: i32 = 0x21;
    pub const PARTICLE: i32 = 0x22;
    pub const OPEN_SIGN: i32 = 0x2A;
    pub const COMBAT: i32 = 0x2D;
    pub const REMOVE_EFFECT: i32 = 0x33;
    pub const WORLD_BORDER: i32 = 0x38;
    pub const DISPLAY_OBJECTIVE: i32 = 0x3B;
    pub const OBJECTIVE: i32 = 0x42;
    pub const PASSENGERS: i32 = 0x43;
    pub const SCORE: i32 = 0x45;
    pub const SOUND: i32 = 0x49;
    pub const TAB_HEADER: i32 = 0x4A;
    pub const COLLECT: i32 = 0x4B;
    pub const ATTRIBUTES: i32 = 0x4E;
    pub const EFFECT: i32 = 0x4F;
}

const SOUNDS_TEXT: &str = include_str!("sounds_1_12.tsv");

struct Sounds {
    by_id: HashMap<i32, SoundEvent>,
    by_name: HashMap<&'static str, &'static str>,
}

fn sounds() -> &'static Sounds {
    static SOUNDS: OnceLock<Sounds> = OnceLock::new();
    SOUNDS.get_or_init(|| {
        let mut by_id = HashMap::new();
        let mut by_name = HashMap::new();
        for line in SOUNDS_TEXT.lines().filter(|l| !l.starts_with('#')) {
            let mut parts = line.split('\t');
            let (Some(id), Some(old), Some(new)) = (parts.next(), parts.next(), parts.next()) else {
                continue;
            };
            by_name.insert(old, new);
            if let (Ok(id), Ok(sound)) = (id.parse(), SoundEvent::from_str(&format!("minecraft:{new}"))) {
                by_id.insert(id, sound);
            }
        }
        Sounds { by_id, by_name }
    })
}

/// A 1.12 sound name (from a plugin or a resource pack) as a modern sound.
fn named_sound(name: &str) -> Holder<SoundEvent, CustomSound> {
    let short = name.strip_prefix("minecraft:").unwrap_or(name);
    let modern = sounds().by_name.get(short).copied().unwrap_or(short);
    match SoundEvent::from_str(&format!("minecraft:{modern}")) {
        Ok(sound) => Holder::Reference(sound),
        Err(_) => Holder::Direct(CustomSound {
            sound_id: Identifier::new(if name.contains(':') {
                name.to_owned()
            } else {
                format!("minecraft:{name}")
            }),
            range: None,
        }),
    }
}

fn source(category: i32) -> SoundSource {
    match category {
        1 => SoundSource::Music,
        2 => SoundSource::Records,
        3 => SoundSource::Weather,
        4 => SoundSource::Blocks,
        5 => SoundSource::Hostile,
        6 => SoundSource::Neutral,
        7 => SoundSource::Players,
        8 => SoundSource::Ambient,
        9 => SoundSource::Voice,
        _ => SoundSource::Master,
    }
}

/// What this part of the translator remembers.
#[derive(Default)]
pub(super) struct World {
    seed: u64,
}

impl World {
    fn seed(&mut self) -> u64 {
        self.seed = self.seed.wrapping_mul(6364136223846793005).wrapping_add(1442695040888963407);
        self.seed
    }
}

/// Translates the packets this file knows. `None` means a packet was cut short,
/// `Some(false)` that it is not one of these.
pub(super) fn translate(
    world: &mut World,
    id_: i32,
    r: &mut In,
    out: &mut Vec<Vec<u8>>,
    ctx: &items::Ctx,
) -> Option<bool> {
    match id_ {
        cb::SOUND | cb::NAMED_SOUND => {
            let sound = if id_ == cb::SOUND {
                let id = r.varint()?;
                match sounds().by_id.get(&id) {
                    Some(sound) => Holder::Reference(*sound),
                    None => return Some(true),
                }
            } else {
                named_sound(&r.string()?)
            };
            let source = source(r.varint()?);
            let (x, y, z) = (r.i32()?, r.i32()?, r.i32()?);
            let (volume, pitch) = (r.f32()?, r.f32()?);
            let seed = world.seed();
            game(out, ClientboundSound { sound, source, x, y, z, volume, pitch, seed });
        }
        cb::TAB_HEADER => {
            let header = text(&r.string()?);
            let footer = text(&r.string()?);
            game(out, ClientboundTabList { header, footer });
        }
        cb::BOSS_BAR => boss_bar(r, out)?,
        cb::OBJECTIVE => {
            let objective_name = r.string()?;
            let method = match r.i8()? {
                1 => Method::Remove,
                mode => {
                    let display_name = FormattedText::from(r.string()?.as_str());
                    let render_type = match r.string()?.as_str() {
                        "hearts" => azalea_core::objectives::ObjectiveCriteria::Hearts,
                        _ => azalea_core::objectives::ObjectiveCriteria::Integer,
                    };
                    if mode == 0 {
                        Method::Add { display_name, render_type, number_format: None }
                    } else {
                        Method::Change { display_name, render_type, number_format: None }
                    }
                }
            };
            game(out, ClientboundSetObjective { objective_name, method });
        }
        cb::DISPLAY_OBJECTIVE => {
            let slot = display_slot(r.i8()?)?;
            let objective_name = r.string()?;
            game(out, ClientboundSetDisplayObjective { slot, objective_name });
        }
        cb::SCORE => {
            let owner = r.string()?;
            let action = r.varint()?;
            let objective_name = r.string()?;
            if action == 1 {
                let objective_name = (!objective_name.is_empty()).then_some(objective_name);
                game(out, ClientboundResetScore { owner, objective_name });
            } else {
                let score = r.varint()? as u32;
                game(out, ClientboundSetScore {
                    owner,
                    objective_name,
                    score,
                    display: None,
                    number_format: None,
                });
            }
        }
        cb::EFFECT => {
            let entity_id = MinecraftEntityId(r.varint()?);
            let effect = r.u8()?;
            let amplifier = i32::from(r.u8()?);
            let duration = r.varint()?;
            let flags = r.u8()?;
            let Some(mob_effect) = effect.checked_sub(1).and_then(|e| MobEffect::from_u32(u32::from(e)))
            else {
                return Some(true);
            };
            let mut data = MobEffectData { amplifier, duration, flags: Default::default() };
            data.flags.ambient = flags & 1 != 0;
            data.flags.show_particles = flags & 2 != 0;
            data.flags.show_icon = true;
            game(out, ClientboundUpdateMobEffect { entity_id, mob_effect, data });
        }
        cb::REMOVE_EFFECT => {
            let entity_id = MinecraftEntityId(r.varint()?);
            let effect = r.u8()?;
            if let Some(effect) = effect.checked_sub(1).and_then(|e| MobEffect::from_u32(u32::from(e))) {
                game(out, ClientboundRemoveMobEffect { entity_id, effect });
            }
        }
        cb::ATTRIBUTES => attributes(r, out)?,
        cb::BLOCK_BREAK => {
            let id = MinecraftEntityId(r.varint()?);
            let (x, y, z) = r.position()?;
            let progress = r.i8()? as u8;
            game(out, ClientboundBlockDestruction { id, pos: BlockPos::new(x, y, z), progress });
        }
        cb::EXPLOSION => {
            let (x, y, z) = (r.f32()?, r.f32()?, r.f32()?);
            let radius = r.f32()?;
            let count = usize::try_from(r.i32()?).ok()?;
            let (bx, by, bz) = (x.floor() as i32, y.floor() as i32, z.floor() as i32);
            // 1.12 clients removed the blocks themselves; modern ones wait for updates.
            for _ in 0..count {
                let (dx, dy, dz) = (r.i8()?, r.i8()?, r.i8()?);
                let pos = (bx + i32::from(dx), by + i32::from(dy), bz + i32::from(dz));
                game(out, chunk::block_update(pos, chunk::state(0)));
            }
            let push = Vec3::new(f64::from(r.f32()?), f64::from(r.f32()?), f64::from(r.f32()?));
            let player_knockback = (push != Vec3::ZERO).then_some(push);
            game(out, ClientboundExplode {
                center: Vec3::new(f64::from(x), f64::from(y), f64::from(z)),
                radius,
                block_count: count as i32,
                player_knockback,
                explosion_particle: if radius >= 2.0 { Particle::ExplosionEmitter } else { Particle::Explosion },
                explosion_sound: Holder::Reference(SoundEvent::EntityGenericExplode),
                block_particles: Vec::new(),
            });
        }
        cb::WORLD_EVENT => {
            let event = r.i32()?;
            let (x, y, z) = r.position()?;
            let data = r.i32()?;
            let global_event = r.bool()?;
            let (event_type, data) = match event {
                // Block broken: 1.12 sends `id | meta << 12`.
                2001 => {
                    let global = ((data & 0xFFF) << 4 | ((data >> 12) & 0xF)) as u32;
                    (2001, u32::from(chunk::state(global).id()))
                }
                2005 => (1505, data as u32),
                1010 => return Some(true),
                1000..=1036 | 2000 | 2002..=2004 | 3000 => (event as u32, data as u32),
                _ => return Some(true),
            };
            game(out, ClientboundLevelEvent { event_type, pos: BlockPos::new(x, y, z), data, global_event });
        }
        cb::PARTICLE => particles(r, out, ctx)?,
        cb::COLLECT => {
            let item_id = r.varint()? as u32;
            let player_id = MinecraftEntityId(r.varint()?);
            let amount = r.varint()?.max(1) as u32;
            game(out, ClientboundTakeItemEntity { item_id, player_id, amount });
        }
        cb::COMBAT => {
            if r.varint()? == 2 {
                let player_id = MinecraftEntityId(r.varint()?);
                let _killer = r.i32()?;
                let message = text(&r.string()?);
                game(out, ClientboundPlayerCombatKill { player_id, message });
            }
        }
        cb::PASSENGERS => {
            let vehicle = MinecraftEntityId(r.varint()?);
            let count = usize::try_from(r.varint()?).ok()?;
            let mut passengers = Vec::with_capacity(count.min(64));
            for _ in 0..count {
                passengers.push(MinecraftEntityId(r.varint()?));
            }
            game(out, ClientboundSetPassengers { vehicle, passengers });
        }
        cb::OPEN_SIGN => {
            let (x, y, z) = r.position()?;
            game(out, ClientboundOpenSignEditor { pos: BlockPos::new(x, y, z), is_front_text: true });
        }
        cb::WORLD_BORDER => border(r, out)?,
        _ => return Some(false),
    }
    Some(true)
}

fn display_slot(slot: i8) -> Option<DisplaySlot> {
    use DisplaySlot as D;
    const TEAMS: [DisplaySlot; 16] = [
        D::TeamBlack,
        D::TeamDarkBlue,
        D::TeamDarkGreen,
        D::TeamDarkAqua,
        D::TeamDarkRed,
        D::TeamDarkPurple,
        D::TeamGold,
        D::TeamGray,
        D::TeamDarkGray,
        D::TeamBlue,
        D::TeamGreen,
        D::TeamAqua,
        D::TeamRed,
        D::TeamLightPurple,
        D::TeamYellow,
        D::TeamWhite,
    ];
    Some(match slot {
        0 => D::List,
        1 => D::Sidebar,
        2 => D::BelowName,
        3..=18 => TEAMS[(slot - 3) as usize],
        _ => return None,
    })
}

fn boss_bar(r: &mut In, out: &mut Vec<Vec<u8>>) -> Option<()> {
    let id = Uuid::from_u64_pair(r.i64()? as u64, r.i64()? as u64);
    let style = |r: &mut In| -> Option<Style> {
        let color = match r.varint()? {
            0 => BossBarColor::Pink,
            1 => BossBarColor::Blue,
            2 => BossBarColor::Red,
            3 => BossBarColor::Green,
            4 => BossBarColor::Yellow,
            5 => BossBarColor::Purple,
            _ => BossBarColor::White,
        };
        let overlay = match r.varint()? {
            1 => BossBarOverlay::Notched6,
            2 => BossBarOverlay::Notched10,
            3 => BossBarOverlay::Notched12,
            4 => BossBarOverlay::Notched20,
            _ => BossBarOverlay::Progress,
        };
        Some(Style { color, overlay })
    };
    let properties = |flags: u8| Properties {
        darken_screen: flags & 1 != 0,
        play_music: flags & 2 != 0,
        create_world_fog: flags & 4 != 0,
    };
    let operation = match r.varint()? {
        0 => {
            let name = text(&r.string()?);
            let progress = r.f32()?;
            let style = style(r)?;
            let properties = properties(r.u8()?);
            Operation::Add(AddOperation { name, progress, style, properties })
        }
        1 => Operation::Remove,
        2 => Operation::UpdateProgress(r.f32()?),
        3 => Operation::UpdateName(text(&r.string()?)),
        4 => Operation::UpdateStyle(style(r)?),
        5 => Operation::UpdateProperties(properties(r.u8()?)),
        _ => return Some(()),
    };
    game(out, ClientboundBossEvent { id, operation });
    Some(())
}

/// The vanilla sprint speed modifier; the modern client adds its own.
const SPRINT_UUID: u128 = 0x662a6b8d_da3e_4c1c_8813_96ea6097278d;

fn attributes(r: &mut In, out: &mut Vec<Vec<u8>>) -> Option<()> {
    use azalea_core::attribute_modifier_operation::AttributeModifierOperation as Op;
    use azalea_inventory::components::AttributeModifier;
    let entity_id = MinecraftEntityId(r.varint()?);
    let count = usize::try_from(r.i32()?).ok()?;
    let mut values = Vec::new();
    for _ in 0..count {
        let key = r.string()?;
        let base = r.f64()?;
        let n = usize::try_from(r.varint()?).ok()?;
        let mut modifiers = Vec::new();
        for _ in 0..n {
            let uuid = Uuid::from_u64_pair(r.i64()? as u64, r.i64()? as u64);
            let amount = r.f64()?;
            let operation = match r.u8()? {
                1 => Op::AddMultipliedBase,
                2 => Op::AddMultipliedTotal,
                _ => Op::AddValue,
            };
            let id = if uuid.as_u128() == SPRINT_UUID {
                Identifier::new("minecraft:sprinting")
            } else {
                Identifier::new(format!("minecraft:legacy_{}", uuid.simple()))
            };
            modifiers.push(AttributeModifier { id, amount, operation });
        }
        let attribute = match key.as_str() {
            "generic.maxHealth" => Attribute::MaxHealth,
            "generic.followRange" => Attribute::FollowRange,
            "generic.knockbackResistance" => Attribute::KnockbackResistance,
            "generic.movementSpeed" => Attribute::MovementSpeed,
            "generic.flyingSpeed" => Attribute::FlyingSpeed,
            "generic.attackDamage" => Attribute::AttackDamage,
            "generic.attackSpeed" => Attribute::AttackSpeed,
            "generic.armor" => Attribute::Armor,
            "generic.armorToughness" => Attribute::ArmorToughness,
            "generic.luck" => Attribute::Luck,
            "horse.jumpStrength" => Attribute::JumpStrength,
            _ => continue,
        };
        values.push(AttributeSnapshot { attribute, base, modifiers });
    }
    if !values.is_empty() {
        game(out, ClientboundUpdateAttributes { entity_id, values });
    }
    Some(())
}

fn particles(r: &mut In, out: &mut Vec<Vec<u8>>, ctx: &items::Ctx) -> Option<()> {
    let kind = r.i32()?;
    let long = r.bool()?;
    let pos = Vec3::new(f64::from(r.f32()?), f64::from(r.f32()?), f64::from(r.f32()?));
    let (x_dist, y_dist, z_dist) = (r.f32()?, r.f32()?, r.f32()?);
    let max_speed = r.f32()?;
    let count = r.i32()?.max(0) as u32;
    let block = |r: &mut In| -> Option<BlockParticle> {
        let v = r.varint()? as u32;
        // 1.12 block particles send `id | meta << 12`.
        let global = (v & 0xFFF) << 4 | ((v >> 12) & 0xF);
        Some(BlockParticle { block_state: chunk::state(global) })
    };
    let color = |x: f32, y: f32, z: f32| {
        let c = |v: f32| (v.clamp(0.0, 1.0) * 255.0) as u8;
        (c(x), c(y), c(z))
    };
    let particle = match kind {
        0 => Particle::Poof,
        1 => Particle::Explosion,
        2 => Particle::ExplosionEmitter,
        3 => Particle::Firework,
        4 => Particle::Bubble,
        5 => Particle::Splash,
        6 => Particle::Fishing,
        7 | 8 => Particle::Underwater,
        9 => Particle::Crit,
        10 => Particle::EnchantedHit,
        11 => Particle::Smoke,
        12 => Particle::LargeSmoke,
        13 => Particle::Effect(SpellParticle { color: RgbColor::new(255, 255, 255), power: 1.0 }),
        14 => Particle::InstantEffect(SpellParticle { color: RgbColor::new(255, 255, 255), power: 1.0 }),
        15 | 16 => {
            // With a count of 0, 1.12 puts the colour in the spread.
            let (cr, cg, cb) = if count == 0 { color(x_dist, y_dist, z_dist) } else { (255, 255, 255) };
            Particle::EntityEffect(ColorParticle { color: ArgbColor::new(255, cr, cg, cb) })
        }
        17 => Particle::Witch,
        18 => Particle::DrippingWater,
        19 => Particle::DrippingLava,
        20 => Particle::AngryVillager,
        21 => Particle::HappyVillager,
        22 => Particle::Mycelium,
        23 => Particle::Note,
        24 => Particle::Portal,
        25 => Particle::Enchant,
        26 => Particle::Flame,
        27 => Particle::Lava,
        29 => Particle::Cloud,
        30 => {
            let (cr, cg, cb) = if count == 0 {
                // 1.12 adds 1 to a red of 0 (so "no colour" means red).
                let red = if x_dist == 0.0 { 1.0 } else { x_dist };
                color(red, y_dist, z_dist)
            } else {
                (255, 0, 0)
            };
            Particle::Dust(DustParticle { color: RgbColor::new(cr, cg, cb), scale: 1.0 })
        }
        31 | 32 => Particle::ItemSnowball,
        33 => Particle::ItemSlime,
        34 => Particle::Heart,
        35 => Particle::BlockMarker(BlockParticle { block_state: azalea::block::BlockState::from(azalea_registry::builtin::BlockKind::Barrier) }),
        36 => {
            let id = r.varint()?;
            let damage = r.varint()?;
            let mut bytes = Vec::new();
            bytes.extend_from_slice(&(id as i16).to_be_bytes());
            bytes.push(1);
            bytes.extend_from_slice(&(damage as i16).to_be_bytes());
            bytes.push(0);
            let item = items::read(&mut In::new(&bytes), ctx)?.item;
            Particle::Item(ItemParticle { item })
        }
        37 | 38 => Particle::Block(block(r)?),
        39 => Particle::Rain,
        41 => Particle::ElderGuardian,
        43 => Particle::EndRod,
        44 => Particle::DamageIndicator,
        45 => Particle::SweepAttack,
        46 => Particle::FallingDust(block(r)?),
        47 => Particle::TotemOfUndying,
        48 => Particle::Spit,
        _ => return Some(()),
    };
    // Coloured particles used the spread for the colour, so they have none.
    let coloured = count == 0 && matches!(kind, 15 | 16 | 30);
    let (x_dist, y_dist, z_dist, count) =
        if coloured { (0.0, 0.0, 0.0, 1) } else { (x_dist, y_dist, z_dist, count) };
    game(out, ClientboundLevelParticles {
        override_limiter: long,
        always_show: long,
        pos,
        x_dist,
        y_dist,
        z_dist,
        max_speed,
        count,
        particle,
    });
    Some(())
}

fn border(r: &mut In, out: &mut Vec<Vec<u8>>) -> Option<()> {
    match r.varint()? {
        0 => game(out, ClientboundSetBorderSize { size: r.f64()? }),
        1 => {
            let old_size = r.f64()?;
            let new_size = r.f64()?;
            let lerp_time = r.varlong()? as u64;
            game(out, ClientboundSetBorderLerpSize { old_size, new_size, lerp_time });
        }
        2 => {
            let new_center_x = r.f64()?;
            let new_center_z = r.f64()?;
            game(out, ClientboundSetBorderCenter { new_center_x, new_center_z });
        }
        3 => {
            let new_center_x = r.f64()?;
            let new_center_z = r.f64()?;
            let old_size = r.f64()?;
            let new_size = r.f64()?;
            let lerp_time = r.varlong()? as u64;
            let new_absolute_max_size = r.varint()? as u32;
            let warning_time = r.varint()? as u32;
            let warning_blocks = r.varint()? as u32;
            game(out, ClientboundInitializeBorder {
                new_center_x,
                new_center_z,
                old_size,
                new_size,
                lerp_time,
                new_absolute_max_size,
                warning_blocks,
                warning_time,
            });
        }
        4 => game(out, ClientboundSetBorderWarningDelay { warning_delay: r.varint()? as u32 }),
        5 => game(out, ClientboundSetBorderWarningDistance { warning_blocks: r.varint()? as u32 }),
        _ => {}
    }
    Some(())
}
