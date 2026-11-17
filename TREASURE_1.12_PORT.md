# Treasure Client Rust (Beta): porting Torch to 1.12

This branch (`Rust`) is a fork of [Torch Client](https://github.com/torch-client/torch-client)
(AGPL-3.0), a Minecraft client written in Rust. The goal is a third Treasure Client
edition, beside JS and WASM, that joins **1.12.2 servers (protocol 340)**, mainly
Eaglercraft 1.12 servers over `wss://` and Java 1.12 servers.

Licensing: this branch stays AGPL-3.0 and keeps Torch's history and credits. None of its
code goes into the Java (JS/WASM) client. Block and item ID tables may come from
[ViaVersion/Mappings](https://github.com/ViaVersion/Mappings) (GPL-3.0, which may be combined
with AGPL-3.0), credited. No Mojang assets go into the source tree.

## How the port works

Torch's game code speaks one modern protocol (26.2, protocol 776). It already
translates neighbouring versions (775, 774) with `azalea::connection::PacketTranslator`
(`src/protocol/hop.rs`). The 1.12 port adds one more translator, `src/protocol/legacy/`,
that sits between the 1.12 server and the game:

- **server -> game:** 1.12 packets are rewritten into the modern packets the game
  already understands. Some packets have no 1.12 equivalent and are made up. For example,
  the whole configuration phase (registries, tags, known packs) is synthesized after the
  1.12 login.
- **game -> server:** modern packets are rewritten into 1.12 ones, or dropped when 1.12 has
  nothing like them (for example chat signing and the configuration acks).

This is the same idea as ViaFabricPlus or ViaRewind, done in Rust inside the client.

**Where it plugs in.** azalea's `PacketTranslator` maps one frame to exactly one frame, which
is not enough for 1.12. One 1.12 chunk becomes a chunk packet plus a light packet, and the
configuration phase has to be invented. So the 1.12 adapter sits one layer lower, in
`src/eagler/`. After the Eagler handshake, every game packet is one WebSocket message, so
the adapter takes the server's 1.12 messages and hands azalea any number of modern frames,
in the phase azalea expects (login -> configuration -> game). It turns azalea's
outgoing frames back into 1.12 messages the same way. Java 1.12 servers can later use the
same adapter over a Wisp/TCP stream.

## Stages

Each stage ends with a build that the owner tests in Chrome.

0. **Setup.** Branch, Rust toolchain, build stock Torch for the web (legit, Eagler on), and
   check that it starts and runs on the Chromebook. Replace the third-party asset relay
   (`getstuff.xpncvr.workers.dev`) with assets packed by `tools/pack_assets.py` and hosted by us.
1. **Clean and rebrand.** Remove the cheat modules: aim assist, auto mace/mine/sell/totem/walk,
   basefinder, crystal aura/macro, ESP/player ESP/sound ESP, flight, freecam, no fall,
   ore miner, pathfinder, safe anchor, scaffold, seed cracker/xray, spam, speed,
   triggerbot, vclip and velocity. Keep the legit ones: zoom, fullbright, HUD panels,
   extra HUD, nametags, tooltips, chat math and friends. Change the name and title to
   "Treasure Client Rust (Beta)", with a "based on Torch" credit.
2. **1.12 login and spawning.** The translator skeleton: Eagler handshake offering protocol
   340, 1.12 login, the synthesized configuration phase, join game, respawn, keep-alive,
   position and look, time, chat in and out, and disconnect messages. Goal: spawn on a 1.12
   server and chat, in an empty world.
3. **Chunks and blocks.** The 1.12 chunk format (sections, palette, `id << 4 | meta`, light
   and biomes inside the chunk) becomes modern chunk and light packets. Also block change,
   multi block change and unload chunk. Block table: 1.12 -> 1.13 (the flattening) -> modern.
   Goal: see the world.
4. **Moving and building.** Movement, teleport confirm, digging, placing, using items,
   swinging, sneaking and sprinting, held slot and abilities. Goal: walk, mine and build
   without the server rubber-banding you.
5. **Entities.** Spawning objects, mobs and players, metadata (1.12 indexes -> modern),
   movement, velocity, equipment, effects, attack and interact. Goal: PvP works.
6. **Inventory and items.** Window items and set slot, click window with 1.12's action
   numbers and confirm transaction, and item NBT -> data components (names, lore,
   enchants, damage). Goal: kits, menus and server GUIs work.
6b. **Singleplayer (1.12).** Reuse the Treasure Client JS/WASM integrated 1.12 server, the
   TeaVM-compiled worker that already runs singleplayer, the practice world and its bots.
   It runs as a separate Web Worker (a separate program, so no license mixing), and the
   Rust client talks to it through the same 1.12 adapter as multiplayer, over a
   message channel instead of a WebSocket. If Torch ships its own singleplayer (26.x
   worlds), we can merge it later from the `torch` remote.
7. **The rest of the world.** Signs, skulls, banners and chests, sounds (1.12 sound IDs),
   particles, titles, boss bars, scoreboards and teams, the tab list, the world border,
   explosions and block break progress.
8. **Eaglercraft extras.** Eagler skins and capes over plugin messages, server icons in
   the list, and Eagler passwords and cookies.
9. **Treasure Client features.** The TC look and menus, and our mods (keystrokes, CPS, FPS
   HUD, Tier Tagger and so on) rewritten in Rust where they fit. Perf work on the
   Chromebook.
10. **Beta release.** A play-only page on the beta site.

## Known risks

- **1.12 movement physics differ from modern** (water, step height, block shapes such as
  fences, panes and stairs). Anticheats may flag differences, so stage 4 needs care.
- **Modern blocks and items that 1.12 servers never send** are fine. 1.12 things with no modern
  equivalent need stand-ins.
- **Disk:** the Rust toolchain plus a Bevy build needs several GB. Check `df -h /`.
