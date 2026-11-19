//! Treasure Client: the TuffX+ "ViaBlocks" protocol (github.com/TuffNetwork/TuffXPlus), the
//! same as the JS and WASM editions (TuffViaBlocks).
//!
//! A server with the TuffX+ plugin tells us where its modern blocks really are. On join the
//! server REGISTERs `viablocks:*`; we opt in on `viablocks:handshake`. Messages (Java
//! DataOutput framing: 2-byte length UTF, big-endian int/long):
//! `INIT_PALETTE` (int n, n x UTF state), `NEW_PALETTE_ENTRY` (UTF state),
//! `ADD_CHUNK` (int entries, each [int id][int n][long x n pos]), `ADD_SINGLE` (int id, long
//! pos). They arrive on `viablocks:data`, and also appended to the end of chunk, block
//! change and multi block change packets (optionally followed by a Below-Y0 trailer:
//! int 0x59304348, int length, bytes). This engine has every modern block, so each one
//! is shown as itself.

use std::collections::{HashMap, HashSet};

use azalea::block::BlockState;

use super::wire::Out;

pub(super) const HANDSHAKE: &str = "viablocks:handshake";
pub(super) const DATA: &str = "viablocks:data";
const Y0_MAGIC: u32 = 0x5930_4348;
const MAX_PENDING_CHUNKS: usize = 4096;

#[derive(Default)]
pub(super) struct Tuff {
    palette: Vec<Option<BlockState>>,
    /// Blocks for chunks that haven't arrived yet.
    pending: HashMap<(i32, i32), Vec<((i32, i32, i32), BlockState)>>,
    loaded: HashSet<(i32, i32)>,
    /// The server has the plugin and we opted in.
    pub(super) on: bool,
}

/// "minecraft:polished_basalt[axis=x]" -> the modern state.
fn resolve(s: &str) -> Option<BlockState> {
    super::chunk::state_named(s.strip_prefix("minecraft:").unwrap_or(s))
}

struct Buf<'a> {
    b: &'a [u8],
    at: usize,
}

impl Buf<'_> {
    fn left(&self) -> usize {
        self.b.len() - self.at
    }
    fn take(&mut self, n: usize) -> Option<&[u8]> {
        let s = self.b.get(self.at..self.at + n)?;
        self.at += n;
        Some(s)
    }
    fn u16(&mut self) -> Option<u16> {
        self.take(2).map(|s| u16::from_be_bytes([s[0], s[1]]))
    }
    fn i32(&mut self) -> Option<i32> {
        self.take(4).map(|s| i32::from_be_bytes(s.try_into().unwrap()))
    }
    fn i64(&mut self) -> Option<i64> {
        self.take(8).map(|s| i64::from_be_bytes(s.try_into().unwrap()))
    }
    fn utf(&mut self) -> Option<String> {
        let n = usize::from(self.u16()?);
        Some(String::from_utf8_lossy(self.take(n)?).into_owned())
    }
}

/// TuffX+ position packing (CustomBlockListener.packLocation).
fn unpack(p: i64) -> (i32, i32, i32) {
    ((p >> 38) as i32, ((p << 52) >> 52) as i32, ((p << 26) >> 38) as i32)
}

impl Tuff {
    /// A new connection: forget the old server.
    pub(super) fn reset(&mut self) {
        *self = Self::default();
    }

    /// A respawn (a proxy server switch only sends a respawn): opt in again.
    pub(super) fn respawn(&mut self, to_server: &mut Vec<Vec<u8>>) {
        self.pending.clear();
        self.loaded.clear();
        if self.on {
            to_server.extend(handshake());
        }
    }

    pub(super) fn chunk_loaded(&mut self, cx: i32, cz: i32) -> Vec<((i32, i32, i32), BlockState)> {
        self.loaded.insert((cx, cz));
        self.pending.remove(&(cx, cz)).unwrap_or_default()
    }

    pub(super) fn unload(&mut self, cx: i32, cz: i32) {
        self.loaded.remove(&(cx, cz));
    }

    /// A `viablocks:data` message, or bytes appended to a block packet. Returns the blocks
    /// to show now (in loaded chunks, or in the chunk `arriving` that is being read).
    pub(super) fn read(&mut self, bytes: &[u8], arriving: Option<(i32, i32)>) -> Vec<((i32, i32, i32), BlockState)> {
        let mut out = Vec::new();
        if !crate::tc_mods::get().via || bytes.len() < 2 {
            return out;
        }
        let mut b = Buf { b: bytes, at: 0 };
        while b.left() >= 2 {
            if b.left() >= 8 && b.b[b.at..b.at + 4] == Y0_MAGIC.to_be_bytes() {
                b.at += 4;
                let Some(len) = b.i32().and_then(|l| usize::try_from(l).ok()) else { break };
                if len > b.left() {
                    break;
                }
                b.at += len; // Below-Y0 data: not supported (1.12 has no blocks below 0)
                continue;
            }
            if self.message(&mut b, arriving, &mut out).is_none() {
                break; // not something we understand
            }
        }
        out
    }

    fn message(
        &mut self,
        b: &mut Buf,
        arriving: Option<(i32, i32)>,
        out: &mut Vec<((i32, i32, i32), BlockState)>,
    ) -> Option<()> {
        let len = usize::from(u16::from_be_bytes([b.b[b.at], b.b[b.at + 1]]));
        if !(1..=32).contains(&len) || len + 2 > b.left() {
            return None;
        }
        match b.utf()?.as_str() {
            "INIT_PALETTE" => {
                let n = b.i32()?.max(0);
                self.palette.clear();
                for _ in 0..n {
                    let s = b.utf()?;
                    self.palette.push(resolve(&s));
                }
            }
            "NEW_PALETTE_ENTRY" => {
                let s = b.utf()?;
                self.palette.push(resolve(&s));
            }
            "ADD_CHUNK" => {
                let entries = b.i32()?;
                for _ in 0..entries {
                    let id = b.i32()?;
                    let n = b.i32()?;
                    for _ in 0..n {
                        let p = b.i64()?;
                        self.place(p, id, arriving, out);
                    }
                }
            }
            "ADD_SINGLE" => {
                let id = b.i32()?;
                let p = b.i64()?;
                self.place(p, id, arriving, out);
            }
            _ => return None,
        }
        self.on = true;
        Some(())
    }

    fn place(&mut self, p: i64, id: i32, arriving: Option<(i32, i32)>, out: &mut Vec<((i32, i32, i32), BlockState)>) {
        if id == 0 {
            return; // "clear": the server's own block update already restored it
        }
        let pos = unpack(p);
        if !(0..256).contains(&pos.1) {
            return; // outside the 1.12 world
        }
        let Some(Some(state)) = usize::try_from(id).ok().and_then(|i| self.palette.get(i)).copied() else {
            return;
        };
        let chunk = (pos.0 >> 4, pos.2 >> 4);
        if arriving == Some(chunk) || self.loaded.contains(&chunk) {
            out.push((pos, state));
            return;
        }
        if !self.pending.contains_key(&chunk) && self.pending.len() >= MAX_PENDING_CHUNKS {
            return;
        }
        self.pending.entry(chunk).or_default().push((pos, state));
    }
}

/// Opting in to ViaBlocks.
pub(super) fn handshake() -> Vec<Vec<u8>> {
    handshake_on(DATA, HANDSHAKE)
}

/// Opting in: register the data channel, then a byte on the handshake channel.
pub(super) fn handshake_on(data: &str, handshake: &str) -> Vec<Vec<u8>> {
    if !crate::tc_mods::get().via {
        return Vec::new();
    }
    let reg = format!("{data}\0{handshake}");
    vec![
        Out::new(super::sb::PLUGIN_MESSAGE).string("REGISTER").bytes(reg.as_bytes()).done(),
        Out::new(super::sb::PLUGIN_MESSAGE).string(handshake).bytes(&[1]).done(),
    ]
}
