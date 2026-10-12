# TreasureViaServer — Option A data source (server side)

The server half of the ViaBlocks "data source" (see `../docs/VIA_DATASOURCE_PLAN.md`).
Drops into a **Paper/Spigot 1.12.2 backend** — e.g. the `server/` backend of an
EaglerXBungee stack like QuizzityMC/EaglerServer-1.12. The EaglerXBungee proxy
forwards game packets transparently, so the plugin on the backend does the work.

## Status
- **Phase 1 (this code): handshake.** Detects Treasure Clients via the
  `TC|ViaBlocks` plugin channel (the client sends it on join) and replies so the
  client's `serverSupports` flag flips true. No block injection yet — this just
  proves the client↔server capability handshake end to end.
- **Phase 2 (next, needs ProtocolLib): block/item/entity injection.** Intercept
  outgoing chunk / block-change packets for Treasure Clients and rewrite block
  ids → our modern state ids from `viablocks_mapping.json`. This is the part that
  can't be tested without a live server, so we iterate it against your stack.

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
