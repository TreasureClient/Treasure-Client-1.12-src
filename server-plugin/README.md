# TreasureViaServer — Option A data source (server side)

The server half of the ViaBlocks "data source" (see `../docs/VIA_DATASOURCE_PLAN.md`).
Drops into a **Paper/Spigot 1.12.2 backend** — e.g. the `server/` backend of an
EaglerXBungee stack like QuizzityMC/EaglerServer-1.12. The EaglerXBungee proxy
forwards game packets transparently, so the plugin on the backend does the work.

## Status
- **Phase 1: handshake.** Detects Treasure Clients via the `TC|ViaBlocks`
  channel and replies so the client's `serverSupports` flag flips true.
- **Phase 2: modern block streaming (this version).** The server sends modern
  blocks to Treasure Clients over the same channel; the **client applies them
  from its own ViaBlocks registry** (`TreasureViaHandshake.applyBlock`). No
  ProtocolLib / NMS packet rewriting — the client already knows how to render
  our state ids, so the server just names positions + state ids.
  - Blocks are **client-side/visual** (the 1.12 server world doesn't hold them),
    so a later server chunk/block update for that position can overwrite them.
    That's fine for the proof; a persistent store is phase 3.

### Try it (ops, as a Treasure Client)
- `/viablocks` — list the modern block names.
- `/viablock deepslate` — turn the block you're looking at into deepslate
  (for your client only).
- `/viatest` — drop a sampler row of modern blocks ~2 blocks in front of you.

### Wire protocol (TC|ViaBlocks)
Client → server (join): `[proto][varint ViaBlocks.FIRST_ID][varint ViaItems.FIRST_ID]`.
Server → client:
- `[1]` — ACK (OP_ACK) → client `serverSupports = true`.
- `[16][int x][int y][int z][int stateId]` — OP_SET_BLOCK.
- `[17][int count]{[int x][int y][int z][int stateId]}×count` — OP_SET_BLOCKS.
State id = `blockId << 4 | meta` (see `viablocks_mapping.json`, `baseStateId`).

## Build
```sh
cd server-plugin
mvn -q package      # -> target/TreasureViaServer.jar
```
If the Spigot repo can't resolve `spigot-api:1.12.2`, install it locally first
with BuildTools (`java -jar BuildTools.jar --rev 1.12.2`), which puts the API in
your local Maven repo; then `mvn package` again.

## Install & test (phase 1)
1. Put `TreasureViaServer.jar` in the backend's `plugins/` folder, restart it.
2. Join from Treasure Client through your EaglerXBungee.
3. Server console should log: `Treasure Client detected: <you> (proto 1)`.
4. The client now has `TreasureViaHandshake.serverSupports == true`.

Once that works, we build phase 2 (block injection) on top.

## How it connects to the client
- Channel: `TC|ViaBlocks` (must match `TreasureViaHandshake.CHANNEL`).
- Client → server on join: `[proto byte][varint ViaBlocks.FIRST_ID][varint ViaItems.FIRST_ID]`.
- Server → client reply: `[proto byte]` → client sets `serverSupports = true`.
- `viablocks_mapping.json` (copied from `docs/`) is the modern-identifier → our
  id / state-id table that phase 2 will use to rewrite packets.
