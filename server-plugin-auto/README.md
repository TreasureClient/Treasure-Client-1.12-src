# TreasureViaAuto — Option A phase 4 (automatic)

The **automatic** half of the ViaBlocks data source. Where `../server-plugin`
(`TreasureViaServer`, phases 1–3) needs ops to run `/viablock`, this plugin
detects modern blocks **players and worldgen actually place** on a modern
backend and streams our ViaBlocks state ids to Treasure Clients with **no
commands** — reusing the exact same `TC|ViaBlocks` wire protocol, so the client
is unchanged.

## When to use which plugin
- **1.12 backend** (no modern content upstream) → use `../server-plugin`
  (`TreasureViaServer`) and place modern blocks with `/viablock` / `/viatest`.
- **1.13+ backend** fronted by ViaVersion for Eaglercraft 1.12 clients → use
  **this** plugin. Real `minecraft:deepslate` etc. placed in the world shows up
  on Treasure Clients as our deepslate automatically.

You can run both on different backends in the same network.

## How it works
- Our block short-names were named after the vanilla identifiers, so a detected
  modern block's `Material.name().toLowerCase()` (`DEEPSLATE` → `deepslate`) is
  looked up directly in `viablocks_mapping.json`. A backend only has a Material
  constant for a block it's new enough to know, so this works across 1.13–1.21
  with no hardcoded enum constants.
- `BlockPlaceEvent` / `BlockBreakEvent` track freshly placed/broken blocks.
  Optional `scan-chunks-on-load` (off by default; expensive) also catches
  pre-existing world content; `/viaauto scan` scans nearby loaded chunks on
  demand.
- Overrides persist to `plugins/TreasureViaAuto/blocks.yml` and are re-asserted
  every few seconds to nearby Treasure Clients (same persistence model as
  phase 3).

## ⚠️ Channel caveat (read this)
A 1.12 client sends the handshake on the **legacy** channel `TC|ViaBlocks`.
Modern (1.13+) servers require **namespaced** channel names, and ViaVersion
rewrites the legacy name as the packet crosses to the backend — the exact
result depends on your Via version. So the plugin registers **several candidate
channels** (`config.yml`) and replies / streams on whichever one the handshake
actually arrives on. If `/viaauto status` shows **0 detected clients** after a
Treasure Client joins, add the real channel name to `config.yml` — find it in
your Via/console logs, or enable Via's channel debug — and restart.

## Build
```sh
cd server-plugin-auto
mvn -q package      # -> target/TreasureViaAuto.jar
```
Targets the 1.20.4 API by default; any 1.13+ API works (only version-stable
calls are used). If the Spigot repo can't resolve the API, install it with
BuildTools for a 1.17+ rev first.

## Install & test
1. Put `TreasureViaAuto.jar` in the **modern backend's** `plugins/` folder,
   restart it. Console logs the mappable-block count and the channels it
   registered.
2. Join from Treasure Client through your EaglerXBungee + ViaVersion.
3. `/viaauto status` → should show ≥1 detected client (if 0, see the channel
   caveat above).
4. Place a modern block (e.g. give yourself `/minecraft:give @s deepslate` on
   the backend and place it) → it should appear on the Treasure Client. Or run
   `/viaauto scan` near existing modern content.

## Commands
- `/viaauto status` — mappable blocks, stored overrides, detected clients, channels.
- `/viaauto scan` — scan nearby loaded chunks for modern blocks now.
- `/viaauto clear` — clear all stored overrides.
- `/viaauto reload` — reload config + mapping (channel changes need a restart).

## Limitations / next
- Stateful blocks (pillars/slabs/walls) map to their **default** state id
  (orientation/half not derived from the modern block data yet).
- Items/entities aren't auto-detected (world blocks are the natural auto target;
  use the phase-3 `/viagive` / `/viaentity` for those).
- Still client-side/visual with a brief flicker on chunk load until the next
  resend (same as phase 3). The flicker-free path is a proxy-level chunk-section
  rewrite with indirect palettes — larger, NMS/Via-internal, and best built
  against a live stack (documented as optional in `../docs/VIA_DATASOURCE_PLAN.md`).
