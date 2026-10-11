# Modern-content rendering ("ViaBlocks") — plan & progress

Goal: render modern (1.13+) blocks/items/entities with their real textures in the
1.12 WASM client, like Tuff/ShadowNet/Voltan do. Those clients are closed, so we
build it ourselves. This doc tracks the plan grounded in *this* codebase.

## How the 1.12 engine makes this possible (verified in-repo)
- `net.minecraft.block.Block.REGISTRY` — blocks by name + numeric id.
- `Block.BLOCK_STATE_IDS` (`ObjectIntIdentityMap<IBlockState>`) — the global block-state
  id table. Assigned at the end of `Block.registerBlocks()` as `regId << 4 | meta`.
- `net.minecraft.world.chunk.BlockStatePaletteLinear` reads/writes chunk block ids
  **through `Block.BLOCK_STATE_IDS`** — so any block we register flows through the
  chunk palette like a vanilla block. This is the key that makes modern blocks
  renderable at all.
- Models/textures are standard 1.12 JSON assets packed into `assets.epk` from
  `desktopRuntime/resources/assets/minecraft/`:
  `blockstates/<n>.json`, `models/block/<n>.json`, `models/item/<n>.json`,
  `textures/blocks/<n>.png`.
- Item models are mapped in `RenderItem.registerItems()`; item forms in
  `Item.registerItems()`; `ItemBlock.getCreativeTab()` inherits the block's tab.

## Phases
1. **Connectivity** — join newer servers. Done via the VIAaaS proxy bundle
   (separate deliverable). Protocol only; no modern textures by itself.
2. **Below-Y0** — extend world height below y=0. Self-contained; not started.
3. **Modern blocks — proof of concept** — ✅ *this branch*. A handful of 1.13+
   full-cube blocks registered + rendered with real textures, placeable in
   creative (Building Blocks). Proves registry → state-id → palette → model →
   atlas → render end to end.
4. **Expand** — more blocks (non-cube models: slabs, walls, dripstone…), then
   items, then entities; then wire to a data source (client-side Via decode, or a
   server plugin) so they show up from real servers, not just creative. Long tail.

## Phase 3 — what's implemented here
- `net.minecraft.block.ViaBlocks` — registry of modern block names + instances.
- `Block.registerViaBlocks()` (called from `registerBlocks()` before the
  block-state-id loop) registers each as a full-cube rock block, ids from 1000.
- `Item.registerItems()` + `RenderItem.registerItems()` register item form + model.
- Assets for: deepslate, calcite, tuff, amethyst_block, copper_block, smooth_basalt.
  Textures are **placeholder** solid-colour 16×16 PNGs — swap in real Mojang
  textures later (same filenames, no code change).

### How to test (this branch)
Build it (your normal flow), then: Singleplayer → Creative → **Building Blocks**
tab → the new blocks appear near the end. Place them → they should render with
their (placeholder) textures. If they render, the pipeline works and Phase 4 is
just "more of the same + a data source".

> ⚠️ Not build-tested in this environment (the WASM toolchain can't run here).
> First build may surface small API fixes — report the compiler output and we
> iterate. This lives on branch `claude/via-modern-blocks`, **not** `WASM`, so it
> can't affect your working themed client.

## Next steps
- Get Phase 3 compiling + rendering (iterate from your build log).
- Swap placeholder textures for real modern textures.
- Add a few non-cube models to prove complex shapes.
- Decide the data source for Phase 4 (client-side Via decode vs. server plugin).
