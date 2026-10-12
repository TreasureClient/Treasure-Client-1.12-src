# Option A — server-side data source for ViaBlocks/ViaItems/ViaEntities

Goal: make the modern content we registered client-side (≈117 blocks, 69 items,
2 entities) show up **from real servers**, not just creative. This is the
server-side "data source" half; the client half (registration + rendering) is
already done.

## The key insight
The 1.12 client already renders **any** block-state id that is registered in
`Block.BLOCK_STATE_IDS` and arrives in a chunk packet — that is exactly how our
ViaBlocks render in creative. So the data source does **not** need a brand-new
rendering path: it just needs the **server to emit our state ids** in the data
it sends to the Eaglercraft client (instead of the vanilla 1.12 fallback that
ViaBackwards would otherwise produce, e.g. deepslate → stone).

So Option A = a server/proxy component that rewrites modern block/item/entity
ids → **our** ids, using the mapping table in `docs/viablocks_mapping.json`.

## The mapping table — `docs/viablocks_mapping.json`
Generated from the live registries. For every ViaBlock/ViaItem:
- `modern`: the vanilla identifier (we deliberately named our blocks after the
  real ones, so `minecraft:deepslate` → our `deepslate`).
- `blockId` / `itemId`: our numeric registry id.
- `baseStateId`: `blockId << 4` (meta 0 / default state).
- `states`: per-state ids where it matters (pillar `axis`, slab `half`).

Block-state id = `regId << 4 | meta`, same as vanilla. Stateful kinds:
- **pillar**: `axis=y|x|z` → meta `0|4|8`.
- **slab**: half `bottom|top` → meta `0|8`; plus a separate double-slab block id.
- **wall**: connection flags are computed client-side (`getActualState`); map the
  modern wall to our `baseStateId`.

## ⚠️ Protocol constraint — use indirect palettes
Our block ids start at 1000, so state ids run **16000–21040**. The 1.12 chunk
"direct"/global palette is **13 bits (max 8191)** — our ids don't fit there.
They DO fit in a chunk section's **indirect (local) palette**, whose entries are
varints of any size, which the client decodes through
`BlockStatePaletteLinear` → `BLOCK_STATE_IDS`. So:

> The server must send any section containing our blocks with an **indirect
> palette** (≤256 distinct states per section ⇒ local palette). This is already
> what servers do for ordinary sections, so in practice it "just works"; only a
> section with >256 distinct block types would be forced to the 13-bit direct
> palette and overflow — essentially never in normal worlds.

**Optional hardening:** renumber ViaBlocks to pack contiguously from regId 256
(state ids 4096–~6100, all < 8192) so they also fit the direct palette. ~125
block ids fit in the 256–511 range. One-line-per-constant change to
`ViaBlocks` + regenerate assets (ids aren't persisted anywhere yet). Offered —
not required for the indirect-palette path.

## Two deployment paths (pick per your server stack)

### A1 — ViaBackwards mapping override  *(lightweight; needs a modern server + Via)*
If the stack is **modern Paper/Spigot (1.13+) → ViaVersion + ViaBackwards →
Eaglercraft bungee → client**, ViaBackwards already down-maps modern block
states to 1.12. We override that mapping for the blocks we support so it outputs
our state ids instead of the vanilla fallback.
- Pros: least code; reuses the existing Via pipeline; per-block data-driven.
- Cons: tied to the Via mapping internals; block-focused (items/entities need
  the item/entity rewriters too).

### A2 — custom proxy plugin  *(TuffX+ model; most control)*
A plugin on the Eaglercraft bungee/proxy intercepts packets to Eaglercraft
clients and rewrites chunk/entity/window data using the mapping table, gated by
a handshake plugin-channel so it only sends our ids to *our* client.
- Channels (TuffX+ precedent): `viablocks:handshake` (client announces support +
  registry version), `viablocks:data` (block mapping/overrides),
  `entities:handshake`. The client registers these and applies incoming data.
- Pros: full control over blocks + items + entities; clean capability handshake;
  independent of Via mapping internals.
- Cons: more code; a standalone plugin project + a small client channel handler.

## Scope by content type
- **Blocks:** map modern state → our `baseStateId`/`states` (above).
- **Items:** map modern item id → our `itemId` (3000–3068) in the item rewriter
  (window items, slot updates, creative give).
- **Entities:** map modern entity type → our entity ids (copper_golem=201,
  bee=202) in the spawn-entity rewriter.

## Build order (once the stack is known)
1. ✅ **Client capability handshake** — done. `TreasureViaHandshake`
   (`net.minecraft.client.network`) sends a plugin-channel handshake
   (`TC|ViaBlocks`) on join advertising the id scheme, and handles the server's
   reply (`serverSupports`). An Option-A server/proxy uses it to detect our
   client and start sending our state ids. Fully defensive — a normal server
   ignores the channel and nothing breaks.
2. ✅ **Server plugin + modern block streaming** — done (`server-plugin/`,
   `TreasureViaServer`, a Paper/Spigot 1.12.2 plugin). It completes the
   handshake and streams modern blocks to Treasure Clients over `TC|ViaBlocks`
   (`OP_SET_BLOCK` / `OP_SET_BLOCKS`); the **client** applies them from its
   ViaBlocks registry (`TreasureViaHandshake.applyBlock`). This sidesteps raw
   NMS/ProtocolLib packet rewriting (the server can't natively hold our
   non-vanilla state ids). Commands: `/viablock`, `/viablocks`, `/viatest`.
   Blocks are client-side/visual (not stored in the 1.12 server world) — good
   for the end-to-end proof. *Build + test on a live stack to iterate.*
3. **Phase 3 (persistence + scale):** store modern blocks server-side (per
   chunk) and re-send on chunk load / block update so they stick; add item +
   entity streaming the same way; optional `A1` ViaBackwards path for a modern
   backend.

## What I need from you to build the server half
The server component depends entirely on your stack. Which is it?
- **(1)** Modern Paper/Spigot server **+ ViaVersion/ViaBackwards** in front of an
  Eaglercraft bungee  → path **A1** (Via mapping override).
- **(2)** An Eaglercraft bungee/proxy (EaglerXBungee / EaglercraftXBungee), with
  or without a modern backend  → path **A2** (custom proxy plugin + client
  channel handler).
- **(3)** A standalone 1.12 Eaglercraft server (no modern content upstream) →
  then there's nothing modern to map; we'd instead inject modern blocks via a
  world-gen/worldedit style plugin on that server.
