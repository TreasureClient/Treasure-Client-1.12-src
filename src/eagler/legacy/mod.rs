//! Treasure Client: playing on 1.12.2 servers (protocol 340).
//!
//! The game speaks today's protocol. When an Eaglercraft server picks protocol 340 in
//! the handshake, this translator sits in the Eagler bridge (`super::stream`) and
//! rewrites every packet in both directions:
//!
//! - server -> game: 1.12 packets become the modern packets the game understands, and
//!   the configuration phase 1.12 never had is built from bundled data (`registries`).
//!   Server packets are held until the game has finished configuring.
//! - game -> server: modern packets become 1.12 ones, or are dropped when 1.12 has
//!   nothing like them (chat signing, chunk batch acks, tick ends and so on).
//!
//! Stage 2 covers joining: login, respawn, keep-alive, position, time, chat, health,
//! experience, abilities, held slot, game state, disconnects, and empty placeholder
//! chunks so the loading screen can finish. Later stages add terrain, entities and
//! inventories (see TREASURE_1.12_PORT.md).

mod chunk;
mod blockents;
mod entities;
mod items;
mod registries;
mod via_guess;
mod via_items;
mod via_mobs;
mod via_tuff;
mod wire;
mod world;

use std::io::Cursor;

use azalea_chat::FormattedText;
use azalea_core::difficulty::Difficulty;
use azalea_core::game_type::{GameMode, OptionalGameType};
use azalea_core::position::{BlockPos, GlobalPos, Vec3};
use azalea::entity::LookDirection;
use azalea_protocol::common::client_information::ClientInformation;
use azalea_protocol::common::movements::{PositionMoveRotation, RelativeMovements};
use azalea_protocol::packets::{Packet, ProtocolPacket};
use azalea_protocol::packets::common::CommonPlayerSpawnInfo;
use azalea_protocol::packets::config::ServerboundConfigPacket;
use azalea_protocol::packets::game::{
    ClientboundChangeDifficulty, ClientboundDisconnect, ClientboundForgetLevelChunk,
    ClientboundGameEvent, ClientboundGamePacket, ClientboundKeepAlive,
    ClientboundLogin, ClientboundPlayerAbilities,
    ClientboundPlayerPosition, ClientboundRespawn, ClientboundSetChunkCacheCenter,
    ClientboundSetDefaultSpawnPosition, ClientboundSetExperience, ClientboundSetHealth,
    ClientboundSetHeldSlot, ClientboundSetTime, ClientboundSystemChat, ServerboundGamePacket,
    c_game_event::EventType,
    c_player_abilities::PlayerAbilitiesFlags,
    c_set_time::ClockState,
};
use azalea_registry::DataRegistry;
use azalea_registry::data::{DimensionKind, WorldClock};
use azalea_registry::identifier::Identifier;

use wire::{In, Out};

pub(crate) use via_guess::set_server;

/// The Minecraft protocol number of 1.12.2.
pub(crate) const PROTOCOL: u16 = 340;

/// Servers where the modern login failed and 1.12.2 worked (until the page reloads).
static PREFERS: std::sync::Mutex<Vec<String>> = std::sync::Mutex::new(Vec::new());

pub(crate) fn prefers(url: &str) -> bool {
    PREFERS.lock().map(|list| list.iter().any(|u| u == url)).unwrap_or(false)
}

pub(crate) fn remember(url: &str) {
    if let Ok(mut list) = PREFERS.lock() {
        if !list.iter().any(|u| u == url) {
            list.push(url.to_owned());
        }
    }
}

/// 1.12.2 clientbound play packet ids.
mod cb {
    pub const CHAT: i32 = 0x0F;
    pub const BLOCK_ENTITY: i32 = 0x09;
    pub const BLOCK_CHANGE: i32 = 0x0B;
    pub const DIFFICULTY: i32 = 0x0D;
    pub const MULTI_BLOCK_CHANGE: i32 = 0x10;
    pub const CONFIRM_TRANSACTION: i32 = 0x11;
    pub const CLOSE_WINDOW: i32 = 0x12;
    pub const OPEN_WINDOW: i32 = 0x13;
    pub const WINDOW_ITEMS: i32 = 0x14;
    pub const WINDOW_PROPERTY: i32 = 0x15;
    pub const SET_SLOT: i32 = 0x16;
    pub const DISCONNECT: i32 = 0x1A;
    pub const UNLOAD_CHUNK: i32 = 0x1D;
    pub const GAME_STATE: i32 = 0x1E;
    pub const KEEP_ALIVE: i32 = 0x1F;
    pub const CHUNK_DATA: i32 = 0x20;
    pub const JOIN_GAME: i32 = 0x23;
    pub const ABILITIES: i32 = 0x2C;
    pub const POSITION: i32 = 0x2F;
    pub const RESPAWN: i32 = 0x35;
    pub const HELD_SLOT: i32 = 0x3A;
    pub const EXPERIENCE: i32 = 0x40;
    pub const HEALTH: i32 = 0x41;
    pub const SPAWN_POSITION: i32 = 0x46;
    pub const TIME: i32 = 0x47;
    pub const TITLE: i32 = 0x48;
    pub const PLUGIN_MESSAGE: i32 = 0x18;
}

/// 1.12.2 serverbound play packet ids.
mod sb {
    pub const TELEPORT_CONFIRM: i32 = 0x00;
    pub const CHAT: i32 = 0x02;
    pub const CLIENT_STATUS: i32 = 0x03;
    pub const CONFIRM_TRANSACTION: i32 = 0x05;
    pub const CLICK_WINDOW: i32 = 0x07;
    pub const UPDATE_SIGN: i32 = 0x1C;
    pub const CLOSE_WINDOW: i32 = 0x08;
    pub const SETTINGS: i32 = 0x04;
    pub const PLUGIN_MESSAGE: i32 = 0x09;
    pub const USE_ENTITY: i32 = 0x0A;
    pub const KEEP_ALIVE: i32 = 0x0B;
    pub const DIGGING: i32 = 0x14;
    pub const ENTITY_ACTION: i32 = 0x15;
    pub const PLACE_BLOCK: i32 = 0x1F;
    pub const USE_ITEM: i32 = 0x20;
    pub const PLAYER: i32 = 0x0C;
    pub const POSITION: i32 = 0x0D;
    pub const POSITION_LOOK: i32 = 0x0E;
    pub const LOOK: i32 = 0x0F;
    pub const HELD_SLOT: i32 = 0x1A;
    pub const ANIMATION: i32 = 0x1D;
}

/// What leaves the translator after one packet: 1.12 bodies for the server and modern
/// bodies for the game.
#[derive(Default)]
pub(crate) struct Moved {
    pub(crate) to_server: Vec<Vec<u8>>,
    pub(crate) to_game: Vec<Vec<u8>>,
}

pub(crate) struct Legacy {
    /// True once the game has finished the configuration phase and is playing.
    playing: bool,
    /// 1.12 packets that arrived while the game was still configuring.
    held: Vec<Vec<u8>>,
    sent: registries::Sent,
    settings: Option<ClientInformation>,
    brand: Option<String>,
    dimension: i32,
    game_mode: u8,
    plains: u32,
    /// 1.12 biome id -> index in the biome registry we sent.
    biomes: Vec<u32>,
    /// Our own entity id (1.12 asks for it in sprint and sneak packets).
    entity: i32,
    sneaking: bool,
    /// Server packets with no translation yet (logged once each).
    untranslated: std::collections::HashSet<i32>,
    items: items::Ctx,
    /// Each open window's slots as the server sent them (1.12 clicks name the clicked item).
    windows: std::collections::HashMap<i32, Vec<Vec<u8>>>,
    state_id: u32,
    action: i16,
    world: world::World,
    /// 1.12 blocks whose look comes from their block entity (skulls, banners, beds, pots).
    specials: std::collections::HashMap<(i32, i32, i32), u32>,
    /// Treasure Client: TuffX+ ViaBlocks (servers that send the real modern blocks).
    tuff: via_tuff::Tuff,
    /// 1.12 packets for the server that came up while reading server packets.
    to_server: Vec<Vec<u8>>,
    /// The hotbar slot in hand, and the modern item in hotbar slots 0-8 and the off hand.
    carried: u8,
    hands: [azalea_registry::builtin::ItemKind; 10],
    /// A hidden 1.16-style smithing menu (2 inputs), shown as the modern one (slots + 1).
    smith_shift: Option<i32>,
    /// Block predictions (placing, breaking) waiting for the server's answer: the
    /// sequence number, the spot the server will send back, and when it was sent.
    pending_acks: Vec<(u32, (i32, i32, i32), crate::platform::time::Instant)>,
}

impl Legacy {
    pub(crate) fn new() -> Self {
        Self {
            playing: false,
            held: Vec::new(),
            sent: registries::Sent::default(),
            settings: None,
            brand: None,
            dimension: 0,
            game_mode: 0,
            plains: 0,
            biomes: Vec::new(),
            entity: 0,
            sneaking: false,
            untranslated: Default::default(),
            items: items::Ctx::default(),
            windows: Default::default(),
            state_id: 0,
            action: 0,
            world: world::World::default(),
            specials: Default::default(),
            tuff: via_tuff::Tuff::default(),
            to_server: Vec::new(),
            carried: 0,
            hands: [azalea_registry::builtin::ItemKind::Air; 10],
            smith_shift: None,
            pending_acks: Vec::new(),
        }
    }

    /// The configuration phase, right after azalea acknowledged the (faked) login.
    pub(crate) fn configuration(&mut self) -> Vec<Vec<u8>> {
        let (packets, sent) = registries::configuration();
        self.plains = sent.biome("plains").unwrap_or(0);
        self.biomes = (0..=255u8)
            .map(|id| sent.biome(chunk::biome_name(id)).unwrap_or(self.plains))
            .collect();
        self.items.enchantments = sent
            .enchantments
            .iter()
            .enumerate()
            .map(|(i, name)| (name.clone(), i as u32))
            .collect();
        self.sent = sent;
        packets
    }

    /// A 1.12 packet from the server.
    pub(crate) fn from_server(&mut self, body: Vec<u8>) -> Vec<Vec<u8>> {
        if !self.playing {
            self.held.push(body);
            return Vec::new();
        }
        let mut out = Vec::new();
        self.translate_server(&body, &mut out);
        out
    }

    /// The server sent these spots back: confirm the predictions waiting for them. The game
    /// then shows what the server sent. Confirming earlier made a placed block vanish
    /// until the server's update came (falling through while bridging).
    fn settle(&mut self, spots: &[(i32, i32, i32)], out: &mut Vec<Vec<u8>>) {
        if self.pending_acks.is_empty() {
            return;
        }
        let upto = self
            .pending_acks
            .iter()
            .filter(|(_, pos, _)| spots.contains(pos))
            .map(|(seq, _, _)| *seq)
            .max();
        if let Some(upto) = upto {
            self.pending_acks.retain(|(seq, _, _)| *seq > upto);
            game(out, azalea_protocol::packets::game::ClientboundBlockChangedAck { seq: upto });
        }
    }

    /// Predictions the server never answered (1.5 s): confirm them anyway.
    fn settle_old(&mut self, out: &mut Vec<Vec<u8>>) {
        let Some(&(_, _, first)) = self.pending_acks.first() else { return };
        if first.elapsed().as_millis() < 1500 {
            return;
        }
        let upto = self
            .pending_acks
            .iter()
            .filter(|(_, _, at)| at.elapsed().as_millis() >= 1500)
            .map(|(seq, _, _)| *seq)
            .max();
        if let Some(upto) = upto {
            self.pending_acks.retain(|(seq, _, _)| *seq > upto);
            game(out, azalea_protocol::packets::game::ClientboundBlockChangedAck { seq: upto });
        }
    }

    /// 1.12 packets for the server made while reading server packets (TuffX+ handshake).
    pub(crate) fn take_to_server(&mut self) -> Vec<Vec<u8>> {
        std::mem::take(&mut self.to_server)
    }

    /// A modern packet from the game.
    pub(crate) fn from_game(&mut self, body: &[u8]) -> Moved {
        let mut moved = Moved::default();
        if self.playing {
            self.translate_game(body, &mut moved);
            return moved;
        }
        let mut cursor = Cursor::new(body);
        let Ok(packet) =
            azalea_protocol::read::deserialize_packet::<ServerboundConfigPacket>(&mut cursor)
        else {
            return moved;
        };
        match packet {
            ServerboundConfigPacket::ClientInformation(p) => self.settings = Some(p.information),
            ServerboundConfigPacket::CustomPayload(p) => {
                if p.identifier.to_string() == "minecraft:brand" {
                    self.brand = Some(brand_text(&p.data));
                }
            }
            ServerboundConfigPacket::FinishConfiguration(_) => {
                self.playing = true;
                crate::log_info!(
                    "net",
                    "1.12: configured; releasing {} held server packets",
                    self.held.len()
                );
                if let Some(info) = self.settings.clone() {
                    moved.to_server.push(settings(&info));
                }
                let brand = self.brand.clone().unwrap_or_else(|| "Treasure Client".to_owned());
                moved.to_server.push(
                    Out::new(sb::PLUGIN_MESSAGE)
                        .string("MC|Brand")
                        .string(&brand)
                        .done(),
                );
                for body in std::mem::take(&mut self.held) {
                    self.translate_server(&body, &mut moved.to_game);
                }
            }
            _ => {}
        }
        moved
    }

    fn translate_server(&mut self, body: &[u8], out: &mut Vec<Vec<u8>>) {
        self.settle_old(out);
        let mut r = In::new(body);
        let Some(id) = r.varint() else {
            return;
        };
        match world::translate(&mut self.world, id, &mut r, out, &self.items) {
            Some(true) => return,
            None => {
                crate::log_warn!("net", "1.12: server packet 0x{id:02x} was cut short");
                return;
            }
            Some(false) => {}
        }
        match entities::translate(id, &mut r, out, &self.items) {
            Some(true) => return,
            Some(false) => {}
            None => {
                crate::log_warn!("net", "1.12: could not read entity packet 0x{id:02x} ({} bytes)", body.len());
                return;
            }
        }
        let done = match id {
            cb::JOIN_GAME => self.join_game(&mut r, out),
            cb::RESPAWN => self.respawn(&mut r, out),
            cb::PLUGIN_MESSAGE => (|| {
                let channel = r.string()?;
                let data = r.rest();
                match channel.as_str() {
                    "REGISTER" => {
                        let names: Vec<&[u8]> = data.split(|b| *b == 0).collect();
                        let has = |c: &str| names.iter().any(|n| *n == c.as_bytes());
                        if has(via_tuff::HANDSHAKE) || has(via_tuff::DATA) {
                            // TuffX+: opt in, and stop guessing (the server sends the real blocks)
                            via_guess::set_plugin();
                            self.to_server.extend(via_tuff::handshake());
                        } else if has("TC|ViaBlocks") {
                            via_guess::set_plugin();
                        }
                        if has("entities:handshake") || has("viaentities:data") {
                            // TuffX+ ViaEntities: opt in
                            self.to_server.extend(via_tuff::handshake_on("viaentities:data", "entities:handshake"));
                        }
                    }
                    "viaentities:data" => entities::tuff_entities(data, out),
                    via_tuff::DATA => {
                        via_guess::set_plugin();
                        for (pos, real) in self.tuff.read(data, None) {
                            game(out, chunk::block_update(pos, real));
                        }
                    }
                    _ => {}
                }
                Some(())
            })(),
            cb::KEEP_ALIVE => r.i64().map(|id| game(out, ClientboundKeepAlive { id: id as u64 })),
            cb::POSITION => position(&mut r, out),
            cb::CHAT => (|| {
                let json = r.string()?;
                let kind = r.u8()?;
                game(out, ClientboundSystemChat { content: text(&json), overlay: kind == 2 });
                Some(())
            })(),
            cb::DISCONNECT => r.string().map(|json| game(out, ClientboundDisconnect { reason: text(&json) })),
            cb::TIME => self.time(&mut r, out),
            cb::SPAWN_POSITION => r.position().map(|(x, y, z)| {
                game(out, ClientboundSetDefaultSpawnPosition {
                    global_pos: GlobalPos {
                        dimension: dimension_name(self.dimension),
                        pos: BlockPos::new(x, y, z),
                    },
                    yaw: 0.0,
                    pitch: 0.0,
                })
            }),
            cb::DIFFICULTY => r.u8().map(|d| {
                game(out, ClientboundChangeDifficulty { difficulty: difficulty(d), locked: false })
            }),
            cb::ABILITIES => (|| {
                let flags = r.u8()?;
                let flying_speed = r.f32()?;
                let walking_speed = r.f32()?;
                game(out, ClientboundPlayerAbilities {
                    flags: PlayerAbilitiesFlags {
                        invulnerable: flags & 0x01 != 0,
                        flying: flags & 0x02 != 0,
                        can_fly: flags & 0x04 != 0,
                        instant_break: flags & 0x08 != 0,
                    },
                    flying_speed,
                    walking_speed,
                });
                Some(())
            })(),
            cb::HELD_SLOT => r.u8().map(|slot| {
                self.carried = slot.min(8);
                game(out, ClientboundSetHeldSlot { slot: u32::from(slot.min(8)) });
            }),
            cb::HEALTH => (|| {
                let health = r.f32()?;
                let food = r.varint()?;
                let saturation = r.f32()?;
                game(out, ClientboundSetHealth { health, food: food.max(0) as u32, saturation });
                Some(())
            })(),
            cb::EXPERIENCE => (|| {
                let experience_progress = r.f32()?;
                let level = r.varint()?;
                let total = r.varint()?;
                game(out, ClientboundSetExperience {
                    experience_progress,
                    experience_level: level.max(0) as u32,
                    total_experience: total.max(0) as u32,
                });
                Some(())
            })(),
            cb::GAME_STATE => self.game_state(&mut r, out),
            cb::TITLE => title(&mut r, out),
            cb::CHUNK_DATA => self.chunk(&mut r, out),
            // Anticheats (Grim, Vulcan and others) send unaccepted window transactions as
            // pings and kick clients that do not answer. Modern clients answer Ping with Pong.
            cb::CONFIRM_TRANSACTION => (|| {
                let window = r.i8()?;
                let action = r.i16()?;
                if !r.bool()? {
                    let id = u32::from(window as u8) << 16 | u32::from(action as u16);
                    game(out, azalea_protocol::packets::game::ClientboundPing { id });
                }
                Some(())
            })(),
            cb::OPEN_WINDOW => (|| {
                let window = i32::from(r.u8()?);
                let kind = r.string()?;
                let title = r.string()?;
                let slots = usize::from(r.u8()?);
                if let Some(pos) = via_guess::opened(kind == "minecraft:crafting_table") {
                    game(out, chunk::block_update(pos, chunk::state(58 << 4)));
                }
                let Some(menu_type) = menu(&kind, slots) else {
                    crate::log_info!("net", "1.12: window type {kind} is not supported yet");
                    return Some(());
                };
                self.windows.insert(window, Vec::new());
                game(out, azalea_protocol::packets::game::ClientboundOpenScreen {
                    container_id: window,
                    menu_type,
                    title: text(&title),
                });
                Some(())
            })(),
            cb::CLOSE_WINDOW => r.u8().map(|window| {
                self.windows.remove(&i32::from(window));
                if self.smith_shift == Some(i32::from(window)) {
                    self.smith_shift = None;
                }
                game(out, azalea_protocol::packets::game::ClientboundContainerClose {
                    container_id: i32::from(window),
                });
            }),
            cb::WINDOW_ITEMS => (|| {
                let window = i32::from(r.u8()?);
                let count = usize::from(r.i16()? as u16);
                let mut raw = Vec::with_capacity(count);
                let mut stacks = Vec::with_capacity(count);
                for _ in 0..count {
                    let slot = items::read(&mut r, &self.items)?;
                    raw.push(slot.raw);
                    stacks.push(slot.item);
                }
                if window == 0 {
                    for (i, stack) in stacks.iter().enumerate().skip(36).take(10) {
                        self.hands[i - 36] = stack.kind();
                    }
                }
                // Treasure Client: the smithing menu of modern servers never "opens" in
                // 1.12; its contents just arrive after you click a smithing table
                if window > 0
                    && !self.windows.contains_key(&window)
                    && let Some(pos) = via_guess::hidden_menu(count.saturating_sub(37))
                {
                    if let Some(table) = via_guess::smithing_state() {
                        game(out, chunk::block_update(pos, table));
                    }
                    game(out, azalea_protocol::packets::game::ClientboundOpenScreen {
                        container_id: window,
                        menu_type: azalea_registry::builtin::MenuKind::Smithing,
                        title: FormattedText::from("Upgrade Gear"),
                    });
                    if count == 39 {
                        // 1.16 layout (base, addition, result): the modern one has a template first
                        self.smith_shift = Some(window);
                    }
                }
                if self.smith_shift == Some(window) {
                    stacks.insert(0, azalea_inventory::ItemStack::Empty);
                }
                self.windows.insert(window, raw);
                self.state_id = self.state_id.wrapping_add(1);
                game(out, azalea_protocol::packets::game::ClientboundContainerSetContent {
                    container_id: window,
                    state_id: self.state_id,
                    items: stacks,
                    carried_item: azalea_inventory::ItemStack::Empty,
                });
                Some(())
            })(),
            cb::SET_SLOT => (|| {
                let window = i32::from(r.i8()?);
                let slot = r.i16()?;
                let item = items::read(&mut r, &self.items)?;
                if window == -1 && slot == -1 {
                    game(out, azalea_protocol::packets::game::ClientboundSetCursorItem {
                        contents: item.item,
                    });
                    return Some(());
                }
                if window == 0 && (36..=45).contains(&slot) {
                    self.hands[(slot - 36) as usize] = item.item.kind();
                }
                let slot = if self.smith_shift == Some(window) && slot >= 0 { slot + 1 } else { slot };
                let raw_slot = if self.smith_shift == Some(window) && slot > 0 { slot - 1 } else { slot };
                if let (Some(slots), Ok(at)) = (self.windows.get_mut(&window), usize::try_from(raw_slot)) {
                    if slots.len() <= at {
                        slots.resize(at + 1, items::EMPTY.to_vec());
                    }
                    slots[at] = item.raw;
                }
                self.state_id = self.state_id.wrapping_add(1);
                game(out, azalea_protocol::packets::game::ClientboundContainerSetSlot {
                    container_id: window,
                    state_id: self.state_id,
                    slot: slot as u16,
                    item_stack: item.item,
                });
                Some(())
            })(),
            cb::WINDOW_PROPERTY => (|| {
                let window = i32::from(r.u8()?);
                let id = r.i16()? as u16;
                let value = r.i16()? as u16;
                game(out, azalea_protocol::packets::game::ClientboundContainerSetData {
                    container_id: window,
                    id,
                    value,
                });
                Some(())
            })(),
            cb::BLOCK_ENTITY => (|| {
                let pos = r.position()?;
                let _action = r.u8()?;
                let Some(tag) = items::read_root(&mut r)? else { return Some(()) };
                let specials = &self.specials;
                let entity = blockents::convert(&tag, |p| specials.get(&p).copied())?;
                if let Some(state) = entity.state {
                    game(out, azalea_protocol::packets::game::ClientboundBlockUpdate {
                        pos: BlockPos::new(pos.0, pos.1, pos.2),
                        block_state: state,
                    });
                }
                if let Some(kind) = entity.kind {
                    game(out, azalea_protocol::packets::game::ClientboundBlockEntityData {
                        pos: BlockPos::new(pos.0, pos.1, pos.2),
                        block_entity_type: Ok(kind),
                        tag: entity.data,
                    });
                }
                Some(())
            })(),
            cb::BLOCK_CHANGE => (|| {
                let pos = r.position()?;
                let global = r.varint()? as u32;
                if blockents::is_special(global) {
                    self.specials.insert(pos, global);
                } else {
                    self.specials.remove(&pos);
                }
                // Treasure Client: Modern Block Guessing, blocks you placed, smithing tables
                let look = chunk::state(global);
                let shown = via_guess::block(pos, global, self.dimension).map_or(look, |t| chunk::restyle(t, look));
                game(out, chunk::block_update(pos, shown));
                // TuffX+ appends the real block to the packet
                for (pos, real) in self.tuff.read(r.rest(), None) {
                    game(out, chunk::block_update(pos, real));
                }
                self.settle(&[pos], out);
                Some(())
            })(),
            cb::MULTI_BLOCK_CHANGE => (|| {
                let cx = r.i32()?;
                let cz = r.i32()?;
                let count = usize::try_from(r.varint()?).ok()?;
                let mut records = Vec::with_capacity(count.min(4096));
                let mut spots = Vec::new();
                for _ in 0..count {
                    let xz = r.u8()?;
                    let y = r.u8()?;
                    let global = r.varint()? as u32;
                    let pos = (cx * 16 + i32::from(xz >> 4), i32::from(y), cz * 16 + i32::from(xz & 15));
                    if blockents::is_special(global) {
                        self.specials.insert(pos, global);
                    } else {
                        self.specials.remove(&pos);
                    }
                    let look = chunk::state(global);
                    let shown = via_guess::block(pos, global, self.dimension).map_or(look, |t| chunk::restyle(t, look));
                    records.push((xz, y, shown));
                    spots.push(pos);
                }
                for update in chunk::section_update(cx, cz, records) {
                    game(out, update);
                }
                for (pos, real) in self.tuff.read(r.rest(), None) {
                    game(out, chunk::block_update(pos, real));
                }
                self.settle(&spots, out);
                Some(())
            })(),
            cb::UNLOAD_CHUNK => (|| {
                let x = r.i32()?;
                let z = r.i32()?;
                self.specials.retain(|p, _| p.0 >> 4 != x || p.2 >> 4 != z);
                via_guess::unload(x, z);
                self.tuff.unload(x, z);
                game(out, ClientboundForgetLevelChunk { pos: azalea_core::position::ChunkPos::new(x, z) });
                Some(())
            })(),
            _ => {
                if self.untranslated.insert(id) {
                    crate::log_info!("net", "1.12: server packet 0x{id:02x} is not translated yet");
                }
                Some(())
            }
        };
        if done.is_none() {
            crate::log_warn!("net", "1.12: could not read server packet 0x{id:02x} ({} bytes)", body.len());
        }
    }

    fn spawn_info(&self, game_mode: u8, dimension: i32, level_type: &str) -> CommonPlayerSpawnInfo {
        CommonPlayerSpawnInfo {
            dimension_type: DimensionKind::new_raw(dimension_index(dimension)),
            dimension: dimension_name(dimension),
            seed: 0,
            game_type: game_mode_of(game_mode),
            previous_game_type: OptionalGameType(None),
            is_debug: false,
            is_flat: level_type.eq_ignore_ascii_case("flat"),
            last_death_location: None,
            portal_cooldown: 0,
            sea_level: 63,
        }
    }

    fn join_game(&mut self, r: &mut In, out: &mut Vec<Vec<u8>>) -> Option<()> {
        via_mobs::clear();
        let entity = r.i32()?;
        let mode = r.u8()?;
        let dimension = r.i32()?;
        let difficulty_byte = r.u8()?;
        let max_players = r.u8()?;
        let level_type = r.string()?;
        let reduced_debug_info = r.bool()?;
        self.dimension = dimension;
        self.game_mode = mode & 0x7;
        self.entity = entity;
        via_guess::new_world(dimension, true);
        self.tuff.reset();
        self.smith_shift = None;
        let view = self.settings.as_ref().map_or(8, |s| u32::from(s.view_distance)).clamp(2, 32);
        game(out, ClientboundLogin {
            player_id: azalea_core::entity_id::MinecraftEntityId(entity),
            hardcore: mode & 0x8 != 0,
            levels: [0, -1, 1].into_iter().map(dimension_name).collect(),
            max_players: i32::from(max_players),
            chunk_radius: view,
            simulation_distance: view,
            reduced_debug_info,
            show_death_screen: true,
            do_limited_crafting: false,
            common: self.spawn_info(self.game_mode, dimension, &level_type),
            online_mode: false,
            enforces_secure_chat: false,
        });
        game(out, ClientboundChangeDifficulty { difficulty: difficulty(difficulty_byte), locked: false });
        game(out, ClientboundGameEvent { event: EventType::WaitForLevelChunks, param: 0.0 });
        Some(())
    }

    fn respawn(&mut self, r: &mut In, out: &mut Vec<Vec<u8>>) -> Option<()> {
        via_mobs::clear();
        let dimension = r.i32()?;
        let difficulty_byte = r.u8()?;
        let mode = r.u8()?;
        let level_type = r.string()?;
        self.dimension = dimension;
        self.game_mode = mode & 0x7;
        via_guess::new_world(dimension, false);
        self.tuff.respawn(&mut self.to_server);
        game(out, ClientboundRespawn {
            common: self.spawn_info(self.game_mode, dimension, &level_type),
            data_to_keep: 0,
        });
        game(out, ClientboundChangeDifficulty { difficulty: difficulty(difficulty_byte), locked: false });
        game(out, ClientboundGameEvent { event: EventType::WaitForLevelChunks, param: 0.0 });
        Some(())
    }

    fn time(&mut self, r: &mut In, out: &mut Vec<Vec<u8>>) -> Option<()> {
        let world_age = r.i64()?;
        let time_of_day = r.i64()?;
        let mut packet = ClientboundSetTime {
            game_time: world_age.max(0) as u64,
            clock_updates: Default::default(),
        };
        let clock = if self.dimension == 1 { "the_end" } else { "overworld" };
        if let Some(index) = self.sent.clock(clock) {
            packet.clock_updates.insert(
                WorldClock::new_raw(index),
                ClockState {
                    total_ticks: time_of_day.unsigned_abs(),
                    partial_tick: 0.0,
                    // 1.12 sends a negative time when the daylight cycle is off.
                    rate: if time_of_day < 0 { 0.0 } else { 1.0 },
                },
            );
        }
        game(out, packet);
        Some(())
    }

    fn game_state(&mut self, r: &mut In, out: &mut Vec<Vec<u8>>) -> Option<()> {
        let reason = r.u8()?;
        let value = r.f32()?;
        let event = match reason {
            0 => EventType::NoRespawnBlockAvailable,
            1 => EventType::StartRaining,
            2 => EventType::StopRaining,
            3 => {
                self.game_mode = value as u8;
                EventType::ChangeGameMode
            }
            4 => EventType::WinGame,
            5 => EventType::DemoEvent,
            6 => EventType::ArrowHitPlayer,
            7 => EventType::RainLevelChange,
            8 => EventType::ThunderLevelChange,
            9 => EventType::PufferFishSting,
            10 => EventType::GuardianElderEffect,
            _ => return Some(()),
        };
        game(out, ClientboundGameEvent { event, param: value });
        Some(())
    }

    fn chunk(&mut self, r: &mut In, out: &mut Vec<Vec<u8>>) -> Option<()> {
        let x = r.i32()?;
        let z = r.i32()?;
        let full = r.bool()?;
        let biomes = &self.biomes;
        let plains = self.plains;
        // The overworld keeps its modern height (from y = -64): 4 empty sections first.
        let below = if self.dimension == 0 { 4 } else { 0 };
        let column = chunk::chunk(
            r,
            x,
            z,
            full,
            self.dimension == 0,
            below,
            |id| biomes.get(usize::from(id)).copied().unwrap_or(plains),
            &mut self.specials,
            self.dimension,
            &mut self.tuff,
        )?;
        for packet in column.packets {
            game(out, packet);
        }
        for update in column.updates {
            game(out, update);
        }
        for data in column.entity_data {
            game(out, data);
        }
        Some(())
    }

    fn translate_game(&mut self, body: &[u8], moved: &mut Moved) {
        let mut cursor = Cursor::new(body);
        let Ok(packet) = azalea_protocol::read::deserialize_packet::<ServerboundGamePacket>(&mut cursor) else {
            return;
        };
        self.settle_old(&mut moved.to_game);
        let to = &mut moved.to_server;
        let back = &mut moved.to_game;
        // Modern clients wait for the server to confirm block predictions; 1.12 servers
        // do not, so confirm them here (the server's block updates still win).
        let mut ack = |seq: u32| {
            game(back, azalea_protocol::packets::game::ClientboundBlockChangedAck { seq });
        };
        match packet {
            ServerboundGamePacket::ContainerClick(p) => {
                let window = p.container_id;
                let mut p = p;
                if self.smith_shift == Some(window) && p.slot_num >= 0 {
                    if p.slot_num == 0 {
                        return; // the template slot: the old smithing menu has none
                    }
                    p.slot_num -= 1;
                }
                let clicked = usize::try_from(p.slot_num)
                    .ok()
                    .and_then(|at| self.windows.get(&window)?.get(at).cloned())
                    .unwrap_or_else(|| items::EMPTY.to_vec());
                self.action = self.action.wrapping_add(1).max(1);
                to.push(
                    Out::new(sb::CLICK_WINDOW)
                        .u8(window as u8)
                        .i16(p.slot_num)
                        .u8(p.button_num)
                        .i16(self.action)
                        .varint(p.click_type as i32)
                        .bytes(&clicked)
                        .done(),
                );
            }
            ServerboundGamePacket::SignUpdate(p) => {
                let mut packet = Out::new(sb::UPDATE_SIGN).position(p.pos.x, p.pos.y, p.pos.z);
                for line in &p.lines {
                    packet = packet.string(line);
                }
                to.push(packet.done());
            }
            ServerboundGamePacket::ContainerClose(p) => {
                if p.container_id != 0 {
                    self.windows.remove(&p.container_id);
                }
                if self.smith_shift == Some(p.container_id) {
                    self.smith_shift = None;
                }
                to.push(Out::new(sb::CLOSE_WINDOW).u8(p.container_id as u8).done());
            }
            ServerboundGamePacket::Pong(p) => to.push(
                Out::new(sb::CONFIRM_TRANSACTION)
                    .u8((p.id >> 16) as u8)
                    .i16(p.id as u16 as i16)
                    .bool(true)
                    .done(),
            ),
            ServerboundGamePacket::PlayerCommand(p) => {
                use azalea_protocol::packets::game::s_player_command::Action;
                let (action, data) = match p.action {
                    Action::StopSleeping => (2, 0),
                    Action::StartSprinting => (3, 0),
                    Action::StopSprinting => (4, 0),
                    Action::StartRidingJump => (5, p.data as i32),
                    Action::StopRidingJump => (6, 0),
                    Action::OpenInventory => (7, 0),
                    Action::StartFallFlying => (8, 0),
                };
                to.push(Out::new(sb::ENTITY_ACTION).varint(self.entity).varint(action).varint(data).done());
            }
            ServerboundGamePacket::PlayerInput(p) => {
                if p.shift != self.sneaking {
                    self.sneaking = p.shift;
                    let action = if p.shift { 0 } else { 1 };
                    to.push(Out::new(sb::ENTITY_ACTION).varint(self.entity).varint(action).varint(0).done());
                }
            }
            ServerboundGamePacket::Interact(p) => {
                let at = azalea_core::position::Vec3::from(p.location);
                let hand = p.hand as i32;
                // A 1.12 right click on an entity sends "interact at" and then "interact".
                to.push(
                    Out::new(sb::USE_ENTITY)
                        .varint(p.entity_id.0)
                        .varint(2)
                        .f32(at.x as f32)
                        .f32(at.y as f32)
                        .f32(at.z as f32)
                        .varint(hand)
                        .done(),
                );
                to.push(Out::new(sb::USE_ENTITY).varint(p.entity_id.0).varint(0).varint(hand).done());
            }
            ServerboundGamePacket::Attack(p) => {
                to.push(Out::new(sb::USE_ENTITY).varint(p.entity_id.0).varint(1).done());
            }
            ServerboundGamePacket::PlayerAction(p) => {
                let status = p.action as i32;
                if status <= 6 {
                    to.push(
                        Out::new(sb::DIGGING)
                            .varint(status)
                            .position(p.pos.x, p.pos.y, p.pos.z)
                            .u8(p.direction as u8)
                            .done(),
                    );
                }
                // breaking: wait for the server to send the block back (it does when a
                // block breaks or the break is refused)
                if matches!(status, 0 | 2) {
                    self.pending_acks.push((p.seq, (p.pos.x, p.pos.y, p.pos.z), crate::platform::time::Instant::now()));
                } else {
                    ack(p.seq);
                }
            }
            ServerboundGamePacket::UseItemOn(p) => {
                let b = &p.block_hit;
                let pos = b.block_pos;
                // Treasure Client: what you place is remembered (Modern Block Guessing),
                // and a block you use may be a smithing table
                if !self.sneaking {
                    via_guess::clicked((pos.x, pos.y, pos.z));
                }
                let (dx, dy, dz) = match b.direction as i32 {
                    0 => (0, -1, 0),
                    1 => (0, 1, 0),
                    2 => (0, 0, -1),
                    3 => (0, 0, 1),
                    4 => (-1, 0, 0),
                    _ => (1, 0, 0),
                };
                let hand = if p.hand as i32 == 0 { usize::from(self.carried) } else { 9 };
                via_guess::placed((pos.x + dx, pos.y + dy, pos.z + dz), self.hands[hand]);
                to.push(
                    Out::new(sb::PLACE_BLOCK)
                        .position(pos.x, pos.y, pos.z)
                        .varint(b.direction as i32)
                        .varint(p.hand as i32)
                        .f32((b.location.x - f64::from(pos.x)) as f32)
                        .f32((b.location.y - f64::from(pos.y)) as f32)
                        .f32((b.location.z - f64::from(pos.z)) as f32)
                        .done(),
                );
                // placing: 1.12 (and modern) servers always send back the clicked block and
                // the one next to it; confirm once that second one arrives
                self.pending_acks.push((p.seq, (pos.x + dx, pos.y + dy, pos.z + dz), crate::platform::time::Instant::now()));
            }
            ServerboundGamePacket::UseItem(p) => {
                to.push(Out::new(sb::USE_ITEM).varint(p.hand as i32).done());
                ack(p.seq);
            }
            ServerboundGamePacket::KeepAlive(p) => to.push(Out::new(sb::KEEP_ALIVE).i64(p.id as i64).done()),
            ServerboundGamePacket::AcceptTeleportation(p) => {
                to.push(Out::new(sb::TELEPORT_CONFIRM).varint(p.id as i32).done());
            }
            ServerboundGamePacket::MovePlayerPos(p) => to.push(
                Out::new(sb::POSITION)
                    .f64(p.pos.x)
                    .f64(p.pos.y)
                    .f64(p.pos.z)
                    .bool(p.flags.on_ground)
                    .done(),
            ),
            ServerboundGamePacket::MovePlayerPosRot(p) => to.push(
                Out::new(sb::POSITION_LOOK)
                    .f64(p.pos.x)
                    .f64(p.pos.y)
                    .f64(p.pos.z)
                    .f32(p.look_direction.y_rot())
                    .f32(p.look_direction.x_rot())
                    .bool(p.flags.on_ground)
                    .done(),
            ),
            ServerboundGamePacket::MovePlayerRot(p) => to.push(
                Out::new(sb::LOOK)
                    .f32(p.look_direction.y_rot())
                    .f32(p.look_direction.x_rot())
                    .bool(p.flags.on_ground)
                    .done(),
            ),
            ServerboundGamePacket::MovePlayerStatusOnly(p) => {
                to.push(Out::new(sb::PLAYER).bool(p.flags.on_ground).done());
            }
            ServerboundGamePacket::Chat(p) => to.push(chat(&p.message)),
            ServerboundGamePacket::ChatCommand(p) => to.push(chat(&format!("/{}", p.command))),
            ServerboundGamePacket::ChatCommandSigned(p) => to.push(chat(&format!("/{}", p.command))),
            ServerboundGamePacket::ClientCommand(p) => {
                use azalea_protocol::packets::game::s_client_command::Action;
                let action = match p.action {
                    Action::PerformRespawn => 0,
                    Action::RequestStats => 1,
                    Action::RequestGameruleValues => return,
                };
                to.push(Out::new(sb::CLIENT_STATUS).varint(action).done());
            }
            ServerboundGamePacket::ClientInformation(p) => {
                to.push(settings(&p.client_information));
                self.settings = Some(p.client_information);
            }
            ServerboundGamePacket::CustomPayload(p) => {
                if p.identifier.to_string() == "minecraft:brand" {
                    to.push(
                        Out::new(sb::PLUGIN_MESSAGE)
                            .string("MC|Brand")
                            .string(&brand_text(&p.data))
                            .done(),
                    );
                }
            }
            ServerboundGamePacket::SetCarriedItem(p) => {
                self.carried = (p.slot as u8).min(8);
                to.push(Out::new(sb::HELD_SLOT).i16(p.slot as i16).done());
            }
            ServerboundGamePacket::Swing(p) => {
                to.push(Out::new(sb::ANIMATION).varint(p.hand as i32).done());
            }
            _ => {}
        }
    }
}

/// Encodes one modern game packet into `out`.
pub(super) fn game(out: &mut Vec<Vec<u8>>, packet: impl Packet<ClientboundGamePacket>) {
    let packet = packet.into_variant();
    match azalea_protocol::write::serialize_packet(&packet) {
        Ok(body) => out.push(body.into_vec()),
        Err(e) => crate::log_warn!("net", "1.12: could not encode {}: {e}", packet.name()),
    }
}

/// 1.12 sends every title change as one packet with an action number.
fn title(r: &mut In, out: &mut Vec<Vec<u8>>) -> Option<()> {
    use azalea_protocol::packets::game::{
        ClientboundClearTitles, ClientboundSetActionBarText, ClientboundSetSubtitleText,
        ClientboundSetTitleText, ClientboundSetTitlesAnimation,
    };
    match r.varint()? {
        0 => game(out, ClientboundSetTitleText { text: text(&r.string()?) }),
        1 => game(out, ClientboundSetSubtitleText { text: text(&r.string()?) }),
        2 => game(out, ClientboundSetActionBarText { text: text(&r.string()?) }),
        3 => {
            let fade_in = r.i32()?.max(0) as u32;
            let stay = r.i32()?.max(0) as u32;
            let fade_out = r.i32()?.max(0) as u32;
            game(out, ClientboundSetTitlesAnimation { fade_in, stay, fade_out });
        }
        4 => game(out, ClientboundClearTitles { reset_times: false }),
        5 => game(out, ClientboundClearTitles { reset_times: true }),
        _ => {}
    }
    Some(())
}

fn position(r: &mut In, out: &mut Vec<Vec<u8>>) -> Option<()> {
    let x = r.f64()?;
    let y = r.f64()?;
    let z = r.f64()?;
    let yaw = r.f32()?;
    let pitch = r.f32()?;
    let flags = r.i8()? as u8;
    let id = r.varint()?;
    // The game loads chunks around this center; follow the player when the server
    // places them at an absolute spot.
    if flags & 0x05 == 0 {
        game(out, ClientboundSetChunkCacheCenter {
            x: (x.floor() as i32).div_euclid(16),
            z: (z.floor() as i32).div_euclid(16),
        });
    }
    game(out, ClientboundPlayerPosition {
        id: id as u32,
        change: PositionMoveRotation {
            pos: Vec3::new(x, y, z),
            delta: Vec3::ZERO,
            look_direction: LookDirection::new(yaw, pitch),
        },
        relative: RelativeMovements {
            x: flags & 0x01 != 0,
            y: flags & 0x02 != 0,
            z: flags & 0x04 != 0,
            y_rot: flags & 0x08 != 0,
            x_rot: flags & 0x10 != 0,
            // 1.12 kept the player's motion through a teleport.
            delta_x: true,
            delta_y: true,
            delta_z: true,
            rotate_delta: false,
        },
    });
    Some(())
}

fn chat(message: &str) -> Vec<u8> {
    // 1.12 servers kick messages longer than 256 characters.
    let message: String = message.chars().take(256).collect();
    Out::new(sb::CHAT).string(&message).done()
}

fn settings(info: &ClientInformation) -> Vec<u8> {
    let m = &info.model_customization;
    let parts = u8::from(m.cape)
        | u8::from(m.jacket) << 1
        | u8::from(m.left_sleeve) << 2
        | u8::from(m.right_sleeve) << 3
        | u8::from(m.left_pants) << 4
        | u8::from(m.right_pants) << 5
        | u8::from(m.hat) << 6;
    Out::new(sb::SETTINGS)
        .string(&info.language)
        .u8(info.view_distance)
        .varint(info.chat_visibility as i32)
        .bool(info.chat_colors)
        .u8(parts)
        .varint(info.main_hand as i32)
        .done()
}

/// The brand payload is a VarInt-length string in both versions.
fn brand_text(data: &[u8]) -> String {
    In::new(data).string().unwrap_or_else(|| "Treasure Client".to_owned())
}

/// 1.12 chat is JSON text; older plugins sometimes send a bare string.
pub(super) fn text(json: &str) -> FormattedText {
    serde_json::from_str::<FormattedText>(json).unwrap_or_else(|_| FormattedText::from(json))
}

fn dimension_name(dimension: i32) -> Identifier {
    Identifier::new(match dimension {
        -1 => "minecraft:the_nether",
        1 => "minecraft:the_end",
        _ => "minecraft:overworld",
    })
}

/// The index of a 1.12 dimension's type in the dimension type registry we sent.
fn dimension_index(dimension: i32) -> u32 {
    let name = match dimension {
        -1 => "the_nether",
        1 => "the_end",
        _ => "overworld",
    };
    registries::DIMENSION_TYPES.iter().position(|d| *d == name).unwrap_or(0) as u32
}

/// A 1.12 window type (and slot count) as a modern menu.
fn menu(kind: &str, slots: usize) -> Option<azalea_registry::builtin::MenuKind> {
    use azalea_registry::builtin::MenuKind as M;
    Some(match kind {
        "minecraft:chest" | "minecraft:container" => match slots.div_ceil(9) {
            0 | 1 => M::Generic9x1,
            2 => M::Generic9x2,
            3 => M::Generic9x3,
            4 => M::Generic9x4,
            5 => M::Generic9x5,
            _ => M::Generic9x6,
        },
        "minecraft:crafting_table" => M::Crafting,
        "minecraft:furnace" => M::Furnace,
        "minecraft:dispenser" | "minecraft:dropper" => M::Generic3x3,
        "minecraft:enchanting_table" => M::Enchantment,
        "minecraft:brewing_stand" => M::BrewingStand,
        "minecraft:villager" => M::Merchant,
        "minecraft:beacon" => M::Beacon,
        "minecraft:anvil" => M::Anvil,
        "minecraft:hopper" => M::Hopper,
        "minecraft:shulker_box" => M::ShulkerBox,
        _ => return None,
    })
}

pub(super) fn game_mode_of(mode: u8) -> GameMode {
    match mode & 0x7 {
        1 => GameMode::Creative,
        2 => GameMode::Adventure,
        3 => GameMode::Spectator,
        _ => GameMode::Survival,
    }
}

fn difficulty(d: u8) -> Difficulty {
    match d {
        0 => Difficulty::Peaceful,
        1 => Difficulty::Easy,
        3 => Difficulty::Hard,
        _ => Difficulty::Normal,
    }
}
