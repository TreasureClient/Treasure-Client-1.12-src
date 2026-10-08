# Treasure Client — Mod System build notes

This build adds the full Mod Menu + HUD mod system. **100% non-cheat** in all
modes: everything here is visual / QoL / performance only. No Kill Aura, Reach,
Fly, Speed, Anti-KB, Aimbot, Auto-Clicker, X-Ray, ESP, hitbox enlargement, etc.

## How to build (on Crostini, same as before)

```
./CompileEPK.sh
./CompileJS.sh
./MakeOfflineDownload.sh
```

Output: `javascript/Eaglercraft_1.12_Offline_International.html` — that single
file is the client.

**Important:** the overlay (ClickGUI + HUD) lives in
`javascript/OfflineDownloadTemplate.txt`, so it appears as soon as you run
`MakeOfflineDownload.sh`. But the mods that read real game state
(Coordinates, Direction, Ping, Armor, Potions, Held Item, and native
Hitboxes) need the **Java** changes, which only take effect after the full
`CompileJS.sh` step rebuilds `classes.js`. Run all three scripts.

## What was added

### New Java files
- `src/game/java/net/minecraft/client/gui/TreasureHud.java`
  Pushes REAL live game state each tick to
  `localStorage["_eaglercraft_1.12.tchud"]` (a small plain-text blob):
  position, yaw, ping, armor durability, potion effects, held item.
  Only reports values genuinely available this tick — never fabricated.

### Changed Java files
- `TreasureMods.java` — added native **Hitboxes** (visual-only). Drives the
  same bounding-box renderer vanilla uses for F3+B, gated by four
  sub-toggles (players / mobs / animals / other). Reads its flags from the
  same `tcmods` storage key the overlay already writes.
- `renderer/entity/RenderManager.java` — the debug-box draw is now also
  gated by `TreasureMods.shouldShowHitbox(entity)` so the sub-toggles work.
  (One-line condition added; nothing else touched.)
- `client/Minecraft.java` — added `TreasureHud.tick(this)` next to the
  existing `TreasureMods.tick(this)` at the top of `runTick()`.

### Overlay (`javascript/OfflineDownloadTemplate.txt`)
- Reorganized Mod Menu into categories: **HUD / Visual / Movement /
  Performance / Interface / Profile**.
- New HUD mods: FPS, CPS, Keystrokes(+settings), Coordinates(+precision),
  Direction(+cardinal/detailed), Ping, Armor Status, Potion Effects,
  Held Item, FPS Graph, Performance Monitor, Custom HUD Editor.
- New Visual mods: Fullbright, Custom Crosshair (style/size/thickness/
  opacity), Hitboxes (+sub-toggles).
- Movement: Toggle Sprint (+ Always / Double-tap-W + hotkey), Toggle Sneak
  (+ hotkey), and a **Keybind Manager** (change / clear / reset, with
  conflict detection).
- Interface: Mod Menu keybind (clear / reset to Left-Ctrl-style default —
  currently `\`), menu animations toggle.
- **Search bar** across mod names, settings, categories, descriptions.
- **Custom HUD Editor**: click "Edit Layout", drag any HUD element, positions
  persist. "Reset Positions" restores defaults.
- **Mode sync**: unchanged single source of truth
  (`localStorage["_eaglercraft_1.12.tcmode"]`). Changing mode in the Mod Menu
  or the main menu updates both, and the Mod Menu auto-themes (gold vs cyan).

### Bug fixed
- The Performance panel used to show hardcoded FPS numbers ("312" / "186").
  It now shows the **real measured framerate**, per the no-fake-data rule.

## Storage keys used (all under the `_eaglercraft_1.12.` namespace)
- `tcmode`  — 1 byte, 1 = Deep Sea (shared with Java main menu)
- `tcmods`  — plain "k=v,..." string: sprint/sprintMode/sneak + hitbox flags
              (written by overlay, read by TreasureMods.java)
- `tchud`   — plain "k=v\n..." blob of live game state
              (written by TreasureHud.java, read by overlay)
- `g`       — engine's own graphics options (real perf control)
- `p`       — engine's own profile store (skin picker)
- `treasureClient.settings` — the overlay's own JSON settings

## Non-cheat guarantee
Hitboxes are visual only (vanilla's own renderer) and never affect targeting,
reach, or combat. Ping shows the real `NetworkPlayerInfo.getResponseTime()` and
displays "ping n/a" when no reliable value exists. Nothing in the HUD bridge
estimates or invents data.

## Update — session 2 (mode sync, edit-layout, client-wide button theming)

### Mode sync is now live in BOTH directions
- `GuiMainMenu.updateScreen()` now polls the shared `tcmode` key every tick, so
  changing mode in the overlay Mod Menu re-themes the Java main menu live (and
  updates the "Mode: …" button label). The overlay likewise polls the key ~3x/sec
  (`pollSharedMode()`), so changing mode from the Java menu re-themes the overlay
  HUD + ClickGUI live. One shared source of truth, always in agreement.

### Edit Layout only shows enabled HUD mods
- Clicking "Edit Layout" now reveals only the HUD elements you actually have
  turned on. Disabled mods (e.g. Keystrokes when off) stay hidden, so you only
  reposition what you'll really see.

### Mod Menu keybind changer
- Already available as a mod: Interface ▸ Mod Menu Keybind (change / clear /
  reset), and Movement ▸ Keybind Manager. Default is now Left Ctrl.

### Client-wide button restyle (NEW)
- `TreasureButtons.java` (new) + a rewritten `GuiButton.func_191745_a` give
  EVERY text button in the client a Treasure Client look: rounded corners, a
  hollow outline in the active mode's accent color, and a smooth hover
  animation that fills the button with that color (gold in Normal, cyan in Deep
  Sea) with dark high-contrast label text when filled. This covers Options,
  Edit Profile, Singleplayer/Multiplayer, Done/Cancel, and all the vanilla
  settings screens, because they all build on GuiButton.
- Icon/image buttons (language flag, toggle switches, recipe-book, beacon,
  lock-icon) keep their own textures on purpose — theming them would break
  their icons.
- Button color follows the same shared mode key, so it recolors instantly when
  you switch modes anywhere.

**This session changed Java (`GuiMainMenu`, `GuiButton`, new `TreasureButtons`),
so you must run the full `CompileJS.sh` build — not just `MakeOfflineDownload.sh`.**

## Update — session 3 (bug fixes)

### Button theming fixes
- **Doubled/ghosted button text** fixed: `Gui.drawRect` leaves the GL color and
  texture state set to the last rectangle, which tinted the font and split the
  shadow into a ghost copy. `GuiButton` now resets color to white + re-enables
  texture before drawing the label.
- **Only some buttons filled on hover** fixed: the hover animation used one
  shared static timer, so only the first-drawn button eased smoothly. Each
  button now keeps its own `tcLastDraw` timestamp and animates independently —
  every button fills on hover now.

### Mod fixes
- **Unbinding the Mod Menu key can no longer lock you out.** Added an always-
  present "Mod Menu" button to the Java main menu (id 101) that opens the
  overlay via a shared `tcopen` key the overlay polls.
- **Changing the Mod Menu keybind now works for any key** (including letters).
  The old code silently refused letters/numbers and the warning only showed on
  one panel, so it looked broken. The hotkey is swallowed before the game sees
  it, so letters are safe; only Escape is disallowed. Rebind feedback now shows
  as a floating toast from any panel.
- **Removed the fake "FPS optimizations."** They just rewrote the game's own
  video options (render distance, particles, clouds, FOV, framerate cap, view
  bob) behind your back. The Performance panel now shows your real live FPS,
  points you to the game's Video Settings, and surfaces the real FPS
  Counter / FPS Graph / Performance Monitor HUD mods. The only engine graphics
  option Treasure Client still writes is Fullbright (gamma), which has no
  in-game equivalent.
- **Keystrokes now show Left & Right click properly.** Rewrote mouse handling to
  track real press/hold/release for both buttons (capture phase, so it works
  during pointer-locked gameplay) instead of a 90ms blink on left-only.

## Update — session 4 (Mod Menu remake + fuzzy-text fix)

### Fuzzy hover text — fixed
Dark hover-fill text was drawn WITH Minecraft's drop shadow, and the dark
shadow bleeding into dark text on a bright fill read as "fuzzy". The label now
drops its shadow once the button is filled (light idle text keeps its shadow).

### Mod Menu — reorganized & polished
Every mod re-sorted into the correct category (no more keybinds in Visual, etc.):
- **HUD**: Coordinates, Direction, Armor Status, Potion Effects, Held Item,
  Keystrokes, CPS Counter, HUD Side, Custom HUD Editor
- **Visual**: FPS Counter, Ping Display, FPS Graph, Performance Monitor,
  Fullbright, Custom Crosshair, Hitboxes  (FPS/ping readouts live here now)
- **Movement**: Toggle Sprint, Toggle Sneak
- **Performance**: live FPS readout, "Where are Video Settings?", and ONE real
  toggle — see below. No placebo boosters.
- **Interface**: Mod Menu Keybind (change/clear/reset), Keybind Manager (all
  hotkeys + conflict detection), Menu Animations
- **Profile**: preset skins
Added section dividers, section labels, consistent compact buttons, and a fixed
a stray `</div>` that had been mis-closing the Performance panel.

### Performance — one legitimate optimization (not a placebo)
Added **Pause When Unfocused**: when enabled and the browser window isn't
focused, `Minecraft.runGameLoop()` skips the world render and yields ~60ms
(ticks still run, so nothing desyncs). This genuinely cuts GPU/battery use when
you tab away — it is NOT a duplicate of any Video Setting. Driven by the same
`tcmods` storage the other native mods use; `TreasureMods.pauseWhenUnfocused()`
reads it (throttled) and `Display.isActive()` reports real window focus.
No other "optimization" toggles were added, because in this WebGL/browser engine
the real levers (render distance, particles, graphics, FOV, framerate cap) are
already in the game's Video Settings — inventing more would be placebo.

This session changed Java (`Minecraft`, `TreasureMods`, `GuiButton`,
`TreasureButtons`), so run the full `CompileJS.sh` build.

## Update — session 5 (audit + fixes + Custom Skin removal)

### Custom Skin — fully removed
Per request, the entire skin picker is gone from the Mod Menu: the Profile
category and panel, all preset data, the paperdoll/grid rendering, model
buttons, the NBT profile writer, and all related CSS. Nothing replaced it.
(The unrelated xque-default-skin removal in DefaultSkins/GuiScreenEditProfile
from an earlier session is untouched.)

### Custom Crosshair — gap + color added
The spec listed gap and color as wanted settings and they were missing.
Both are now implemented and wired: Gap (0–8, shrinks the arms away from
center) and Color (Theme / White / Red / Green / Cyan / Black). Style, size,
thickness, opacity already worked. Every crosshair setting now does something.

### Audited every mod — verified working
Coordinates, Direction, Ping, Armor, Potions, Held Item, Keystrokes (incl.
LMB/RMB press+hold+release), CPS, FPS, FPS Graph, Performance Monitor,
Fullbright, Hitboxes (category filters), HUD Editor (drag+persist, only shows
enabled elements), Keybind Manager (change/clear/reset + conflict detection),
mode sync (both directions), and full persistence across reload — all tested
headless with zero console errors.

### Honest limitations stated (not faked)
- Hitbox line thickness / color / opacity were NOT added: WebGL clamps GL line
  width to 1px in most browsers and color/opacity would require rewriting the
  entity debug-box renderer. The working category toggles (players/mobs/animals/
  other) are kept rather than shipping settings that do nothing.
- Performance still has exactly one real toggle (Pause When Unfocused). No other
  optimization is added because everything else duplicates Video Settings.

## Update — session 6 (8 bug fixes + zoom + resolution)

1. CPS "2 per click" — fixed: mousedown was registered on BOTH window and
   document (capture), double-counting every click. Now window-only.
2. Search — verified working (was likely a stale build).
3. "Interface" tab renamed to "Other Mods" (internal data-cat kept as
   "interface" so panel/search mapping stays intact).
4. Zoom mod (Other Mods): hold a key to zoom. EntityRenderer.getFOVModifier
   multiplies FOV by TreasureMods.zoomFactor(); overlay writes tczoom=1 while
   the bound key is held (gameplay only) and zoomLevel into tcmods. Visual only
   — no sensitivity/aim change. Default key C, strength adjustable 10–80.
5. Render Resolution mod (Performance): a REAL optimization. PlatformInput.update
   multiplies the device pixel ratio by a scale (30–100%) read from tcres, so the
   WebGL framebuffer is genuinely smaller — fewer pixels shaded = higher FPS.
   Not a duplicate of any Video Setting.
6. Game now pauses when the Mod Menu opens: overlay openGui() writes tcpause=1 +
   exits pointer lock; TreasureMods.tick reads it and calls displayInGameMenu()
   (same as Escape) so SP pauses and the mouse frees. closeGui() writes tcpause=2
   to close that menu and return to the world. No more pressing Escape after the
   hotkey.
7. Double-tap-W sprint firing on a single tap — fixed: the latch now requires two
   genuine presses and, crucially, clears lastForwardTapTime on release so a stale
   timestamp can't re-latch on the next single press. Gap tightened to 350ms.
8. Default Mod Menu keybind changed Left Ctrl -> Right Shift (more familiar).
   labelFor() now maps letters and Shift keys to friendly names.

This session changed Java (Minecraft, TreasureMods, EntityRenderer, PlatformInput),
so run the full CompileJS.sh build.

## Update — session: WORLD SHARING / LAN MULTIPLAYER PORT (1.8 -> 1.12)

This is the big one: ported Eaglercraft's shared-world / LAN multiplayer from the
1.8.8 source into this 1.12 client. Treasure Client is aiming to be the first
1.12 Eaglercraft client with working world sharing.

WHAT WAS PORTED (44 files):
- Relay protocol: all 14 packet classes (sp/relay/pkt/) — pure data, ported clean.
- WebRTC layer: PlatformWebRTC.java (teavm backend, 1052 lines). Self-contained —
  all the WebRTC JS is inline @JSBody, no external .js needed.
- Client LAN package: LANClientNetworkManager (rewritten to extend 1.12's
  net.minecraft.network.NetworkManager), LANClientPeer, LANPeerEvent, plus the
  server-side LANServerController + LANServerList.
- Relay client package: RelayManager, RelayServer(Socket) + impls, RelayQuery,
  RelayWorldsQuery, rate-limit trackers (14 files).
- Relay/LAN GUIs: GuiScreenRelay, GuiScreenAddRelay, GuiScreenNoRelays,
  GuiSlotRelay, GuiShareToLan, GuiScreenLANInfo, GuiScreenChangeRelayTimeout,
  GuiScreenLANConnect, GuiScreenLANConnecting.
- Small support: EnumInputEvent enum, UpdateService stub.

WIRING:
- Un-commented the 7+ dormant LAN call sites in SingleplayerServerController.
- GuiIngameMenu "Share to LAN" button (case 7) no longer throws
  "LAN is not supported yet" — it opens GuiShareToLan.
- Default relays already present in TeaVMClientConfigAdapter:
  relay.deev.is, relay.lax1dude.net, relay.shhnowisnottheti.me.

KEY COMPATIBILITY WIN: this 1.12 fork kept many 1.8 names (fontRendererObj,
GuiButton ctor, EnumConnectionState/PacketBuffer APIs), and 1.12's own
GuiScreenSingleplayerConnecting gave an exact template for the login handshake —
so the LAN join uses 1.12's proven login path over the WebRTC transport.

HONEST STATUS: validated by symbol audit (every cross-file method call resolves),
NOT by real compilation (no javac/gradle in the porting sandbox). "Should compile"
is not "confirmed working." The real test is: build on Crostini, start a world,
Esc -> Share to LAN -> get a join code -> a friend joins with it. Networking bugs
(connects-but-breaks) can only be found by live testing. If the build throws a
compile error, send me the first error and the file/line — those are quick to fix.

## Update — mod-menu polish batch + multiplayer button wiring

1. Mod Menu keybind can be CHANGED but never cleared/removed (removed the clear
   handler + guarded setBind). Removed the duplicate keybind rows.
2. In-game HUD elements (keystrokes, CPS, FPS, coords, etc.) are now HIDDEN on the
   main menu and only appear once you're in a world (gated on the hud.active
   bridge via a .tc-nogame class).
3. Fixed "can't type in Mod Menu search": the engine's keydown/keyup handlers now
   bail out when an overlay text input is focused (tcOverlayInputFocused() in
   PlatformInput), and the search box claims its own keys. Two-sided fix.
4. Share to LAN button is now ENABLED and opens GuiShareToLan (it was force-
   disabled and then hijacked by the Discord button; Discord now gets its own
   separate button).
5. Pause-menu buttons (GuiButtonWithStupidIcons) now render with the Treasure
   Client themed style via TreasureButtons.drawButton, keeping their icons.
6. Toggle Sprint/Sneak keybinds moved out of "Other Mods" (they live with their
   toggles in Movement); Zoom settings are now always-visible, not behind a gear.
7. New "Reset Other Client Data" button (Other Mods > Troubleshooting): clears
   localStorage keys that aren't Treasure's (leftover mods from another client
   like Tuff), preserving all _eaglercraft_1.12.* + treasureClient.settings and
   all worlds (worlds are in IndexedDB).
8. Zoom trigger now keys off the in-game bridge (hud.active) instead of a fragile
   pointer-lock check.

Java touched: GuiIngameMenu, GuiButtonWithStupidIcons, PlatformInput -> full build.

## Update — Deep Sea = REAL performance mode + mode loading screen

Deep Sea mode is now a genuine performance mode, not just a theme:
- On switching to Deep Sea, TreasureMods SAVES the player's current video
  settings, then applies leaner values: render distance -3 chunks, particles
  minimal, clouds off, fast graphics, entity shadows off, smooth lighting (AO)
  off, view bobbing off, fog off. It rebuilds chunks (renderGlobal.loadRenderers)
  so it takes effect immediately.
- On switching back to Normal, it RESTORES the exact saved values, so the user's
  own preferences are never lost.
- Every field is a real GameSettings lever the engine honors — no placebo.
- A themed loading screen shows during the switch with the REAL steps
  ("Lowering render distance", "Disabling entity shadows", "Rebuilding world
  chunks", etc.). Gold for Normal, cyan for Deep Sea.

NOT done (yet): Bare Bones texture toggle. The client has a full resource-pack
system (GuiScreenResourcePacks + refreshResources), and 161+ of the pack's block
textures map by name to 1.12 (core duel blocks: stone/dirt/cobblestone/planks/
ores/glass). A converter + built-in-pack wiring is a sizable follow-up.

Note on u3: the "u3 source" downloads are compiled JS/WASM blobs with no real
source; u3's FPS comes from being a WASM-GC build, which can't be extracted or
scoped to one mode. Deep Sea's real optimizations are the honest alternative.

## Update — Bare Bones texture toggle (Visual)

A "Bare Bones Textures" toggle in the Visual panel swaps in flat, modern-style
block & item textures — useful on modern duel servers.

How it works:
- The 1.21 Bare Bones pack was converted to 1.12 names at build time (folder
  block/->blocks/, item/->items/, plus flip-name mapping like oak_planks ->
  planks_oak). 203 block + 257 item textures mapped, covering the core duel
  blocks/items (stone, cobblestone, planks, ores, glass, obsidian, diamond
  sword/pickaxe, apple, ender pearl, etc.). Rarer/renamed ones stay vanilla.
- Converted textures live at desktopRuntime/resources/assets/treasure/barebones/
  and get packed into the EPK automatically.
- BareBonesResourcePack (a built-in IResourcePack) sits on top of the default
  pack. When enabled it serves the converted textures for textures/blocks/* and
  textures/items/*; when disabled it reports every resource absent so the default
  shows through.
- The overlay toggle writes tcbb=1/0; TreasureMods.updateBareBones flips
  BareBonesResourcePack.enabled and calls mc.refreshResources() to reload. A
  single loading screen ("Enabling Bare Bones textures", "Refreshing resources",
  "Rebuilding texture atlas") plays during the swap.

Requires a FULL rebuild (new Java class + new textures -> EPK must repack).

## Update — Deep Sea maxed + join-code moved + server packs auto-accept

1. Deep Sea is now MAX performance: render distance capped at 4 chunks, particles
   minimal, clouds/fog/shadows/AO/viewbob off, mipmaps 0, vsync off, AND render
   resolution dropped to 60% (the biggest browser FPS lever, via tcres). All saved
   and restored when returning to Normal. HONEST: this can't match u3's WASM-GC
   engine — "60fps on the weakest computer" isn't guaranteed on the TeaVM engine,
   but this is everything real that can be toggled.
2. "Join Shared World (code)" button moved OUT of the multiplayer list and INTO
   the Direct Connect screen (GuiScreenServerList), just below the server-address
   box.
3. Modern-server textures (mace, wind charge, etc.): server resource packs now
   default to ENABLED (was PROMPT), so a duel server's pack downloads
   automatically. CustomItems/CIT is already on by default. This is the necessary
   fix, but whether a specific server's mace renders depends on their setup and
   can't be verified without connecting.

## Update — Rodrigo's Pack + hidden mod loader + Discord button fix

1. Replaced Bare Bones with Rodrigo's Pack (88x, native 1.12 pack_format 3, so
   460/478 blocks + 340/343 items match directly — near-full coverage). TEXTURES
   ONLY: copied just blocks/ + items/ into assets/treasure/rodrigo/, NO gui/font/
   models, so it changes zero UI. Toggle in Visual renamed "Rodrigo's Pack".
   RodrigoResourcePack replaces BareBonesResourcePack (same on/off + refresh logic,
   tcbb flag).
2. Hidden EaglercraftML mod loader: injected ModAPI.js + ModLoader.js (NOT ModGUI,
   so no visible UI) into the offline template before classes.js. Inits with an
   empty mod list; mods can be added via eaglercraftXOpts.Mods or ?Mod= but with
   none it sits idle and invisible.
3. Discord button no longer overlaps the Leave/Disconnect button: moved from
   height/4+120 (same row as Return-to-Menu) down to height/4+144.
4. The "FPS mod for Deep Sea" is the existing native applyDeepSeaPerf (auto-applies
   each tick on entering Deep Sea: render distance cap 4, particles/clouds/shadows/
   AO/fog/viewbob off, mipmaps 0, vsync off, render resolution 60%). No JS mod
   needed — native Java is more reliable.

Full rebuild (new textures + Java class + template change -> EPK repack).

## Update — Deep Sea removed, Rodrigo removed, FPS Booster added, GUI polish

Client simplified to reduce weight and fix the "too heavy" feel:
1. DEEP SEA MODE fully removed: TreasureButtons.isDeepSea() always false (single
   gold theme); removed the main-menu Mode toggle button (id 100) + its handler +
   updateScreen poll; removed the overlay Normal/Deep Sea header toggle, applyMode
   theming, showModeLoader, syncModeToEngine, pollSharedMode mode-read.
2. RODRIGO'S PACK removed: deleted RodrigoResourcePack.java + the rodrigo textures,
   removed the registration in Minecraft.java, removed the Visual toggle. (It was
   8x = lower-res than vanilla, wasn't helping FPS anyway.)
3. FPS BOOSTER added (Performance tab): one toggle writes tcfps=1/0; TreasureMods
   .updateFpsBoost saves the user's video settings then applies render distance 4,
   no particles, fast graphics, no clouds/fog/shadows, smooth lighting off, no
   mipmaps, no vsync; restores exactly on off. Native Java, reliable. Brief loading
   screen on toggle.
4. GUI polish: fixed in-game HUD overlap — keystrokes/cps/fps re-spaced (ks bottom
   66px, cps 34px, fps 6px, no collisions), held-item moved to bottom-center so it
   no longer overlaps the WASD keystrokes block. Header simplified (mode toggle
   gone). Footer hint "Interface" -> "Other Mods".

Full rebuild (Java + textures removed -> EPK repack).
