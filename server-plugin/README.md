# TreasureViaServer — Option A data source (server side)

The server half of the ViaBlocks "data source" (see `../docs/VIA_DATASOURCE_PLAN.md`).
Drops into a **Paper/Spigot 1.12.2 backend** — e.g. the `server/` backend of an
EaglerXBungee stack like QuizzityMC/EaglerServer-1.12. The EaglerXBungee proxy
forwards game packets transparently, so the plugin on the backend does the work.

## Status
- **Phase 1: handshake.** Detects Treasure Clients via the `TC|ViaBlocks`
  channel and replies so the client's `serverSupports` flag flips true.
- **Phase 2: modern block streaming.** The server sends modern blocks to
  Treasure Clients over the same channel; the **client applies them from its own
  ViaBlocks registry** (`TreasureViaHandshake.applyBlock`). No ProtocolLib / NMS
  packet rewriting — the client already knows how to render our state ids, so
  the server just names positions + state ids.
- **Phase 3: persistence + items + entities (this version).**
  - **Persistence.** Block overrides are stored on disk
    (`plugins/TreasureViaServer/blocks.yml`) and **re-asserted every 3 s** to
    each nearby Treasure Client, so they survive chunk reloads and relogs. (A
    plain 1.12 server re-sends the real chunk, which would otherwise revert the
    override; the periodic resend keeps it stuck. Expect a brief flicker when a
    chunk first loads, until the next resend tick.) Breaking the real block at an
    override position drops the override. `/viaclear` removes them.
  - **Items.** `/viagive <name> [count]` streams a modern item into the
    client's inventory (`OP_GIVE_ITEM`). Client-side/visual — a server
    inventory update can clear it.
  - **Entities.** `/viaentity <name>` spawns a modern entity client-side where
    you're looking (`OP_SPAWN_ENTITY`). Client-side/visual, no server AI.

  Blocks/items/entities are **client-side/visual** (the 1.12 server world
  doesn't hold them). Persistence here means the *server remembers and
  re-sends* the overrides — not that the 1.12 world stores modern blocks.

### Try it (ops, as a Treasure Client)
- `/viablocks` — list the modern block / item / entity names.
- `/viablock deepslate` — turn the block you're looking at into deepslate
  (persisted; re-sent so it sticks).
- `/viatest` — drop a sampler row of modern blocks ~2 blocks in front of you.
- `/viagive trident` — put a modern item in your inventory.
- `/viaentity bee` — spawn a bee (or `copper_golem`) where you're looking.
- `/viaclear` — clear nearby overrides (`/viaclear all` clears every world).

### Wire protocol (TC|ViaBlocks)
Client → server (join): `[proto][varint ViaBlocks.FIRST_ID][varint ViaItems.FIRST_ID]`.
Server → client:
- `[1]` — ACK (OP_ACK) → client `serverSupports = true`.
- `[16][int x][int y][int z][int stateId]` — OP_SET_BLOCK.
- `[17][int count]{[int x][int y][int z][int stateId]}×count` — OP_SET_BLOCKS.
- `[32][varint itemId][int count]` — OP_GIVE_ITEM.
- `[48][int entityId][int typeId][double x][double y][double z][float yaw][float pitch]`
  — OP_SPAWN_ENTITY.
State id = `blockId << 4 | meta` (see `viablocks_mapping.json`, `baseStateId`);
`itemId` / `entityTypeId` come from the `items` / `entities` sections.

## Build
```sh
cd server-plugin
mvn -q package      # -> target/TreasureViaServer.jar
```
If the Spigot repo can't resolve `spigot-api:1.12.2`, install it locally first
with BuildTools (`java -jar BuildTools.jar --rev 1.12.2`), which puts the API in
your local Maven repo; then `mvn package` again.

## Install & test
1. Put `TreasureViaServer.jar` in the backend's `plugins/` folder, restart it.
2. Join from Treasure Client through your EaglerXBungee.
3. Server console should log:
   `Treasure Client detected: <you> (proto 1)` and, on enable,
   `… N blocks, M items, K entities; P stored overrides …`.
4. Run `/viatest`, then relog or walk away and back — the sampler row should
   still be there (persistence + resend). Try `/viagive trident` and
   `/viaentity bee`.

## How it connects to the client
- Channel: `TC|ViaBlocks` (must match `TreasureViaHandshake.CHANNEL`).
- Client → server on join: `[proto byte][varint ViaBlocks.FIRST_ID][varint ViaItems.FIRST_ID]`.
- Server → client reply: `[1]` (OP_ACK) → client sets `serverSupports = true`.
- `viablocks_mapping.json` (copied from `docs/`) is the modern-identifier → our
  id / state-id table: `blocks[*].baseStateId`, `items[*].itemId`,
  `entities[*].entityTypeId`.
- Overrides persist in `plugins/TreasureViaServer/blocks.yml`
  (`world|x|y|z: stateId`).
