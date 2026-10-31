package net.minecraft.client.gui;

import net.lax1dude.eaglercraft.EagRuntime;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.Entity;
import net.minecraft.entity.monster.IMob;
import net.minecraft.entity.passive.EntityAnimal;
import net.minecraft.entity.player.EntityPlayer;

/**
 * Treasure Client — native mod module.
 *
 * The in-browser overlay (ClickGUI) writes a tiny settings blob to
 * localStorage["_eaglercraft_1.12.tcmods"]; this class reads it and applies the
 * gameplay mods that require real game state every client tick. Doing sprint /
 * sneak here (instead of dispatching fake keyboard events from JS) is what makes
 * them reliable and keeps them from ever tripping the overlay's own hotkey.
 *
 * Storage format is a plain comma-separated key=value string, e.g.
 *   "sprint=1,sprintMode=0,sneak=1"
 * so the JS side can write it trivially and we can parse it without JSON.
 */
public class TreasureMods {

	private static boolean sprint = false;
	private static int sprintMode = 0; // 0 = always (auto-sprint), 1 = double-tap W
	private static boolean sneak = false;

	// Hitboxes: visual-only entity bounding boxes, same renderer vanilla uses
	// for F3+B. No reach/targeting/aim-assist is touched anywhere here.
	private static boolean hitboxes = false;
	private static boolean hitboxPlayers = true;
	private static boolean hitboxMobs = true;
	private static boolean hitboxAnimals = true;
	private static boolean hitboxOther = false;
	private static boolean pauseUnfocused = false;

	// Zoom: held-key FOV multiplier (visual only). zoomActive is polled from the
	// overlay (tczoom storage), zoomLevel is the configured strength.
	private static boolean zoomActive = false;
	private static long zoomRead = 0L;
	private static float zoomLevelCache = 0.30f; // resulting FOV multiplier when zoomed
	// Smooth Zoom: animate the FOV multiplier toward the target instead of snapping.
	private static boolean smoothZoom = false;
	private static float zoomSpeed = 0.15f;      // per-frame easing (higher = snappier)
	private static float zoomCurrent = 1.0f;     // the eased value actually returned
	private static long zoomFrameTime = 0L;

	private static long lastLoad = 0L;
	private static long lastInWorldWrite = 0L;

	// ---- Deep Sea performance mode ----
	// Deep Sea is a real performance mode: on entering it we SAVE the player's
	// current video settings, then apply leaner "fast" values; on returning to
	// Normal we RESTORE exactly what they had. Nothing here is placebo — every
	// field below is a genuine GameSettings lever the engine honors.
	// ---- FPS Booster (replaces the old Deep Sea performance mode) ----
	// A single toggle the user flips in the Mod Menu (overlay writes tcfps=1/0).
	// ---- 1.1 optimization / render mods (read from the tcmods flag string) ----
	private static boolean noParticles = false;  // Optimizations: disable all particles
	private static boolean noHurtCam = false;    // Render: no damage camera tilt
	private static boolean tntTimer = false;     // Render: fuse countdown over TNT
	public static boolean noHurtCam() { return noHurtCam; }
	public static boolean tntTimer() { return tntTimer; }
	public static boolean noParticles() { return noParticles; }

	// ViaVersion: master toggle for the Option A modern-content data path
	// (handshake + applying modern blocks/items/entities streamed by servers).
	// Default ON; the overlay writes via=0 when the user turns it off.
	private static boolean via = true;
	public static boolean viaEnabled() { return via; }

	// Performance modpack: one switch for all ported performance mods. Default ON; overlay
	// writes perfpack=0 to turn it off, which runs the game like vanilla. The single mods'
	// own settings are kept (Video Settings shows and changes them) and come back when it's on.
	private static boolean perfPack = true;
	public static boolean performanceModpack() { return perfPack; }

	// Celeritas: the faster chunk renderer (WASM only). Default ON; overlay writes celeritas=0 to turn it off.
	private static boolean celeritas = true;
	/** The Celeritas setting saved by the overlay (the Mods screen shows and changes it). */
	public static boolean celeritasSetting() { return celeritas; }
	/** Whether Celeritas should actually run right now (its setting and the modpack switch). */
	public static boolean celeritasActive() { return perfPack && celeritas; }

	// Entity Culling: skip mobs/block entities hidden behind walls. Default ON; overlay writes entcull=0 to turn it off.
	private static boolean entityCulling = true;
	public static boolean entityCulling() { return perfPack && entityCulling; }

	// Dynamic FOV: zoom the view when sprinting, flying, with Speed or drawing a bow. Default OFF
	// (overlay writes dynfov=1 to turn it on), like OptiFine's Dynamic FOV option.
	private static boolean dynFov = false;
	public static boolean dynamicFov() { return dynFov; }

	// VanillaFix: crash recovery and Minecraft bug fixes. Default ON; overlay writes vanillafix=0 to turn it off.
	private static boolean vanillaFix = true;
	public static boolean vanillaFix() { return perfPack && vanillaFix; }

	// Enhanced Block Entities: chests and beds baked into the chunk mesh. Default ON; overlay writes ebe=0 to turn it off.
	private static boolean ebe = true;
	private static int appliedEbe = 7;
	// BetterFps fast math (OptiFine-compatible sine table). Default ON; overlay writes bfmath=0 to turn it off.
	private static boolean bfMath = true;
	public static boolean betterFpsMath() { return perfPack && bfMath; }
	// Dynamic Resolution (run by the overlay). Default OFF; read here for the Mods screen.
	private static boolean dynRes = false;
	public static boolean dynamicResolution() { return dynRes; }
	// Celeritas Leaf Culling mode (TCLeafCulling.OFF/HOLLOW/SOLID_AGGRESSIVE/SOLID). Default SOLID, like the original mod.
	private static int leafCull = net.minecraft.block.TCLeafCulling.SOLID;
	public static int leafCulling() { return perfPack ? leafCull : net.minecraft.block.TCLeafCulling.OFF; }
	public static boolean enhancedBlockEntities() { return perfPack && ebe; }

	// Dynamic FPS: lower the frame rate while the game window isn't focused. Default ON; overlay writes dynfps=0.
	private static boolean dynFps = true;
	public static boolean dynamicFps() { return perfPack && dynFps; }

	// ImmediatelyFast: cheaper name labels and sign text. Default ON; overlay writes ifast=0 to turn it off.
	private static boolean iFast = true;
	public static boolean immediatelyFast() { return perfPack && iFast; }

	// Lithium: collision, chunk lookup and block entity list speedups. Default ON; overlay writes lithium=0.
	private static boolean lithium = true;
	public static boolean lithium() { return perfPack && lithium; }

	// Memory Saver: stop the idle singleplayer worker on servers. Default ON; overlay writes memsaver=0.
	private static boolean memSaver = true;
	public static boolean memorySaver() { return perfPack && memSaver; }

	// the last settings text we parsed, so unchanged settings aren't parsed again 4x a second
	private static byte[] lastModsData;
	private static boolean modsParsedOnce;

	// Particle Culling: skip particles outside the view. Default ON; overlay writes pcull=0 to turn it off.
	private static boolean particleCulling = true;
	public static boolean particleCulling() { return perfPack && particleCulling; }
	// Particle Core: cheaper particle movement and a lower particle cap. Default ON (overlay writes pcore=0).
	private static boolean pCore = true;
	public static boolean particleCore() { return perfPack && pCore; }
	// C2ME smooth chunk loading: spread big server packet bursts over a few ticks. Default ON (c2me=0 turns off).
	private static boolean c2me = true;
	public static boolean c2me() { return perfPack && c2me; }

	// Entity render distance in blocks (mobs, players, items, chests, signs...). 0 = off (vanilla).
	private static int entityDistance = 0;
	public static int entityDistance() { return perfPack ? entityDistance : 0; }

	// 1.7 Animations (Render): master switch off by default, parts on by default
	private static boolean oldAnim = false, oaBlock = true, oaNoDip = true, oaUseSwing = true, oaArmor = true, oaHitSwing = true;
	public static boolean oaHitSwing() { return oaHitSwing; }
	public static boolean oldAnim() { return oldAnim; }
	public static boolean oaBlock() { return oaBlock; }
	public static boolean oaNoDip() { return oaNoDip; }
	public static boolean oaUseSwing() { return oaUseSwing; }
	public static boolean oaArmor() { return oaArmor; }

	// Fullbright (gamma is applied by the overlay) + its Clear Water option (less fog underwater)
	private static boolean fullbright = false, clearWater = true;
	public static boolean clearWater() { return fullbright && clearWater; }

	// Saturation Display (HUD): gold outlines on the food bar
	private static boolean satDisplay = false;
	public static boolean saturationDisplay() { return satDisplay; }

	// Hit Color (Render): custom hurt tint. Color is 0xRRGGBB, strength 0..1
	private static boolean hitColorOn = false;
	private static int hitColor = 0xFF0000;
	private static float hitAlpha = 0.30f;
	public static boolean hitColorOn() { return hitColorOn; }
	public static int hitColor() { return hitColor; }
	public static float hitAlpha() { return hitAlpha; }

	// Tier Tagger (Render): MCTiers PvP tiers next to player names. Mode: "highest" or a gamemode id
	private static boolean tierTagger = false;
	private static String ttMode = "highest";
	public static boolean tierTagger() { return tierTagger; }
	public static String tierTaggerMode() { return ttMode; }

	private static float clamp01(float v, float lo, float hi) {
		return v < lo ? lo : (v > hi ? hi : v);
	}

	// ---- new 1.2 mods ----
	// Password hider: mask the password when typing /login, /register, etc.
	private static boolean passwordHider = false;
	public static boolean passwordHider() { return passwordHider; }
	// Low fire: shorter first-person fire overlay. Height 0.05..1.0 (default 0.40).
	private static boolean lowFire = false;
	private static float lowFireH = 0.40f;
	public static boolean lowFire() { return lowFire; }
	public static float lowFireHeight() { return lowFireH; }
	// Low shield: push the blocking shield down so it covers less view. 0..0.6 (default 0.30).
	private static boolean lowShield = false;
	private static float lowShieldAmt = 0.30f;
	public static boolean lowShield() { return lowShield; }
	public static float lowShieldAmount() { return lowShieldAmt; }
	// Small totem: scale the totem particle pop. 0.1..1.0 (default 0.40).
	private static boolean smallTotem = false;
	private static float totemScaleV = 0.40f;
	public static boolean smallTotem() { return smallTotem; }
	public static float totemScale() { return totemScaleV; }

	// toggle-via-sprint-key state + status display
	private static boolean sprintToggledOn = false;
	private static boolean sprintKeyWasDown = false;
	/** For the HUD: true = Toggled mode active, false = Vanilla. */
	public static boolean sprintToggled() { return sprintToggledOn; }
	/** For the HUD: is the Toggle Sprint feature even enabled? */
	public static boolean sprintFeatureOn() { return sprint; }
	// Toggle Sneak latch (mirrors sprint)
	private static boolean sneakToggledOn = false;
	private static boolean sneakKeyWasDown = false;
	public static boolean sneakToggled() { return sneakToggledOn; }
	public static boolean sneakFeatureOn() { return sneak; }
	// double-tap tracking for sprintMode == 1
	private static boolean forwardWasDown = false;
	private static long lastForwardTapTime = 0L;
	private static boolean doubleTapLatched = false;

	// ---- F6 item-NBT dump (diagnostic for building ViaItems) ----
	// Press F6 to print the item you're hovering (in a container), holding on the
	// cursor, or in your main hand — registry name + display name + full NBT — to
	// chat and the console. This reveals whether a server's down-mapped modern
	// item (e.g. a mace shown as an axe) still carries its modern identity in NBT.
	private static boolean dumpKeyWasDown = false;

	/** Key-triggered dump (backslash "\"). Chat command ".viadump" is the reliable path. */
	private static void dumpHeldItemOnKey(Minecraft mc) {
		try {
			boolean down = net.lax1dude.eaglercraft.Keyboard.isKeyDown(net.lax1dude.eaglercraft.Keyboard.KEY_BACKSLASH);
			if (down && !dumpKeyWasDown) {
				dumpHeldItem(mc);
			}
			dumpKeyWasDown = down;
		} catch (Throwable t) {}
	}

	/**
	 * Print the item you're hovering (in a container), holding on the cursor, or
	 * in your main hand — registry name + display name + full NBT — to chat and
	 * the console. Triggered by the chat command ".viadump" or the "\" key.
	 */
	public static void dumpHeldItem(Minecraft mc) {
		try {
			if (mc == null) {
				return;
			}
			net.minecraft.item.ItemStack st = null;
			if (mc.currentScreen instanceof net.minecraft.client.gui.inventory.GuiContainer) {
				net.minecraft.inventory.Slot slot =
						((net.minecraft.client.gui.inventory.GuiContainer) mc.currentScreen).tcHoveredSlot();
				if (slot != null && slot.getHasStack()) {
					st = slot.getStack();
				}
				if (st == null && mc.player != null) {
					net.minecraft.item.ItemStack cur = mc.player.inventory.getItemStack();
					if (cur != null && !cur.func_190926_b()) {
						st = cur;
					}
				}
			}
			if (st == null && mc.player != null) {
				st = mc.player.getHeldItemMainhand();
			}

			String msg;
			if (st == null || st.func_190926_b()) {
				msg = "[ViaItems] item: (empty) — hold the item in your main hand, then type .viadump";
			} else {
				net.minecraft.item.Item it = st.getItem();
				net.minecraft.util.ResourceLocation rl = (it != null)
						? net.minecraft.item.Item.REGISTRY.getNameForObject(it) : null;
				String rn = (rl != null) ? rl.toString() : "?";
				String nbt = (st.getTagCompound() != null) ? st.getTagCompound().toString() : "(no NBT)";
				msg = "[ViaItems] " + rn + " x" + st.func_190916_E()
						+ " name='" + st.getDisplayName() + "' nbt=" + nbt;
			}
			System.out.println(msg);
			try {
				mc.ingameGUI.getChatGUI().printChatMessage(
						new net.minecraft.util.text.TextComponentString(msg));
			} catch (Throwable t) {}
			// Short, prominent verdict — printed LAST so it stays at the bottom of
			// chat (the long NBT line above wraps and scrolls its start out of view).
			try {
				if (st != null && !st.func_190926_b()) {
					net.minecraft.item.Item vit = st.getItem();
					int vid = (vit != null) ? net.minecraft.item.Item.getIdFromItem(vit) : -1;
					boolean ours = vid >= net.minecraft.item.ViaItems.FIRST_ID;
					// Is our mace item actually registered in this runtime?
					net.minecraft.item.Item maceReg = net.minecraft.item.Item.getByNameOrId("mace");
					String maceStr = (maceReg != null)
							? ("YES id " + net.minecraft.item.Item.getIdFromItem(maceReg)) : "NO(null)";
					// Would the remap change THIS stack right now (name is known-good here)?
					boolean changes = false;
					try {
						net.minecraft.item.ItemStack rr = net.minecraft.item.ViaItemRemap.remap(st);
						changes = (rr != null && rr.getItem() != st.getItem());
					} catch (Throwable t2) {}
					String verdict = ">> VERDICT: held=" + (ours ? ("OURS id " + vid) : ("vanilla id " + vid))
							+ " | mace reg=" + maceStr + " | remap-now=" + (changes ? "SWAPS" : "no-op")
							+ " | Via " + (viaEnabled() ? "ON" : "OFF");
					mc.ingameGUI.getChatGUI().printChatMessage(
							new net.minecraft.util.text.TextComponentString(verdict));
				}
			} catch (Throwable t) {}
		} catch (Throwable t) {}
	}

	/**
	 * The value saved for a setting, as the user chose it (the Performance modpack switch
	 * doesn't change it). Used by Video Settings to show the mods' own settings.
	 */
	// ---- mod settings from Video Settings (keys starting with "vs") ----
	private static final java.util.HashMap<String, String> vs = new java.util.HashMap<String, String>();

	/** A whole-number mod setting, kept between min and max. */
	public static int vsInt(String key, int def, int min, int max) {
		String v = vs.get(key);
		if (v == null) return def;
		try {
			return Math.max(min, Math.min(max, Integer.parseInt(v)));
		} catch (Throwable t) {
			return def;
		}
	}

	/** An on/off mod setting (part of a mod, not the mod itself). */
	public static boolean vsOn(String key, boolean def) {
		String v = vs.get(key);
		return v == null ? def : v.equals("1");
	}

	private static boolean hudBatch = true, hudCache = true, modelBatch = true;
	private static int bgFps = 10, workerIdleSec = 5, crashRecoveries = 3;

	/** ImmediatelyFast's HUD cache (draw the HUD only when it changes). */
	public static boolean hudCache() { return immediatelyFast() && hudCache; }
	/** Dynamic FPS: frame rate while another window is focused. */
	public static int backgroundFps() { return bgFps; }
	/** Fast Ping: answer the server's keep-alive pings the moment they arrive. */
	public static boolean fastPing() { return vsOn("vsFastPing", true); }
	/** Elytra Fix: open the elytra the moment jump is pressed in the air, like newer versions. */
	public static boolean elytraFix() { return vsOn("vsElytraFix", true); }
	/** VanillaFix: crashes it recovers from within 15 seconds before giving up. */
	public static int crashRecoveries() { return crashRecoveries; }

	/** Pushes the Video Settings values into the mods. */
	private static void applyModSettings() {
		try {
			org.embeddedt.embeddium.impl.render.chunk.compile.executor.ChunkBuilder.MAIN_THREAD_BUDGET_NANOS =
					vsInt("vsBuildMs", 4, 2, 12) * 1000000.0;
			org.embeddedt.embeddium.impl.gui.SodiumGameOptions.PerformanceSettings po =
					org.taumc.celeritas.CeleritasVintage.options().performance;
			po.alwaysDeferChunkUpdates = vsOn("vsDefer", true);
			po.useFogOcclusion = vsOn("vsFogOcc", true);
			po.useBlockFaceCulling = vsOn("vsFaceCull", true);
			po.useEntityCulling = vsOn("vsChunkEnt", true);
		} catch (Throwable t) {}
		net.minecraft.client.renderer.TCEntityCulling.FRAME_BUDGET_NS = vsInt("vsCullMs", 15, 5, 40) * 100000L;
		int near = vsInt("vsCullNear", 4, 0, 16);
		net.minecraft.client.renderer.TCEntityCulling.ALWAYS_VISIBLE_DIST_SQ = near * near;
		net.minecraft.client.renderer.TCBlockEntities.bakeChests = vsOn("vsChests", true);
		net.minecraft.client.renderer.TCBlockEntities.bakeBeds = vsOn("vsBeds", true);
		hudBatch = vsOn("vsHudBatch", true);
		hudCache = vsOn("vsHudCache", true);
		modelBatch = vsOn("vsModelBatch", true);
		int sign = vsInt("vsSignDist", 32, 8, 64);
		net.minecraft.client.renderer.TCFastText.SIGN_TEXT_DISTANCE_SQ = sign * sign;
		net.minecraft.client.particle.TCParticleCore.maxParticles = vsInt("vsPMax", 2048, 512, 8192);
		int coll = vsInt("vsPColl", 24, 8, 64);
		net.minecraft.client.particle.TCParticleCore.FAR_SQ = coll * coll;
		net.minecraft.client.particle.TCParticleCore.cullMargin = vsInt("vsPPad", 2, 0, 8);
		net.lax1dude.eaglercraft.socket.WebSocketNetworkManager.tcBaseBudgetNs = vsInt("vsPktMs", 4, 2, 16) * 1000000L;
		bgFps = vsInt("vsBgFps", 10, 1, 60);
		workerIdleSec = vsInt("vsWorkerSec", 5, 5, 300);
		crashRecoveries = vsInt("vsCrashMax", 3, 1, 10);
	}

	public static String userSetting(String key, String def) {
		byte[] data = lastModsData;
		if (data == null || data.length == 0) {
			return def;
		}
		String s = new String(data, java.nio.charset.StandardCharsets.UTF_8);
		String prefix = key + "=";
		int at = s.startsWith(prefix) ? 0 : s.indexOf("," + prefix);
		if (at < 0) {
			return def;
		}
		if (at > 0) {
			at++;
		}
		int end = s.indexOf(',', at);
		return s.substring(at + prefix.length(), end < 0 ? s.length() : end);
	}

	private static void reload() {
		try {
			byte[] data = EagRuntime.getStorage("tcmods");
			if (modsParsedOnce && java.util.Arrays.equals(data, lastModsData)) {
				return; // nothing changed since the last read
			}
			modsParsedOnce = true;
			lastModsData = data;
			sprint = false; sprintMode = 0; sneak = false;
			hitboxes = false; hitboxPlayers = true; hitboxMobs = true; hitboxAnimals = true; hitboxOther = false;
			pauseUnfocused = false;
			via = true; // default ON unless the overlay explicitly says via=0
			perfPack = true;
			celeritas = true;
			entityCulling = true;
			particleCulling = true;
			pCore = true;
			c2me = true;
			vanillaFix = true;
			ebe = true;
			dynFps = true;
			iFast = true;
			lithium = true;
			memSaver = true;
			bfMath = true;
			dynRes = false;
			dynFov = false;
			entityDistance = 0;
			oldAnim = false; oaBlock = true; oaNoDip = true; oaUseSwing = true; oaArmor = true; oaHitSwing = true;
			fullbright = false; clearWater = true; satDisplay = false;
			hitColorOn = false; hitColor = 0xFF0000; hitAlpha = 0.30f;
			tierTagger = false; ttMode = "highest";
			passwordHider = false;
			lowFire = false; lowFireH = 0.40f;
			lowShield = false; lowShieldAmt = 0.30f;
			smallTotem = false; totemScaleV = 0.40f;
			net.minecraft.client.renderer.TCExtra.reset();
			vs.clear();
			leafCull = net.minecraft.block.TCLeafCulling.SOLID;
			if (data != null && data.length > 0) {
				String s = new String(data, java.nio.charset.StandardCharsets.UTF_8);
				String[] parts = s.split(",");
				for (int i = 0; i < parts.length; i++) {
					String p = parts[i];
					int eq = p.indexOf('=');
					if (eq <= 0) continue;
					String k = p.substring(0, eq);
					String v = p.substring(eq + 1);
					if (k.equals("sprint")) sprint = v.equals("1");
					else if (k.equals("sprintMode")) { try { sprintMode = Integer.parseInt(v); } catch (Throwable t) {} }
					else if (k.equals("sneak")) sneak = v.equals("1");
					else if (k.equals("hitbox")) hitboxes = v.equals("1");
					else if (k.equals("hbPlayers")) hitboxPlayers = v.equals("1");
					else if (k.equals("hbMobs")) hitboxMobs = v.equals("1");
					else if (k.equals("hbAnimals")) hitboxAnimals = v.equals("1");
					else if (k.equals("hbOther")) hitboxOther = v.equals("1");
					else if (k.equals("pauseUnfocused")) pauseUnfocused = v.equals("1");
				else if (k.equals("noParticles")) noParticles = v.equals("1");
				else if (k.equals("noHurtCam")) noHurtCam = v.equals("1");
				else if (k.equals("tntTimer")) tntTimer = v.equals("1");
				else if (k.equals("via")) via = v.equals("1");
				else if (k.equals("celeritas")) celeritas = v.equals("1");
				else if (k.equals("perfpack")) perfPack = v.equals("1");
				else if (k.equals("entcull")) entityCulling = v.equals("1");
				else if (k.equals("vanillafix")) vanillaFix = v.equals("1");
				else if (k.equals("ebe")) ebe = v.equals("1");
				else if (k.equals("dynfps")) dynFps = v.equals("1");
				else if (k.equals("ifast")) iFast = v.equals("1");
				else if (k.equals("lithium")) lithium = v.equals("1");
				else if (k.equals("memsaver")) memSaver = v.equals("1");
				else if (k.equals("bfmath")) bfMath = v.equals("1");
				else if (k.equals("dynres")) dynRes = v.equals("1");
				else if (k.equals("dynfov")) dynFov = v.equals("1");
				else if (k.equals("pcull")) particleCulling = v.equals("1");
				else if (k.equals("pcore")) pCore = v.equals("1");
				else if (k.equals("c2me")) c2me = v.equals("1");
				else if (k.equals("oldAnim")) oldAnim = v.equals("1");
				else if (k.equals("oaBlock")) oaBlock = v.equals("1");
				else if (k.equals("oaNoDip")) oaNoDip = v.equals("1");
				else if (k.equals("oaUseSwing")) oaUseSwing = v.equals("1");
				else if (k.equals("oaArmor")) oaArmor = v.equals("1");
				else if (k.equals("oaHitSwing")) oaHitSwing = v.equals("1");
				else if (k.equals("fullbright")) fullbright = v.equals("1");
				else if (k.equals("clearWater")) clearWater = v.equals("1");
				else if (k.equals("satDisplay")) satDisplay = v.equals("1");
				else if (k.equals("hitColorOn")) hitColorOn = v.equals("1");
				else if (k.equals("hitColor")) { try { hitColor = Integer.parseInt(v, 16) & 0xFFFFFF; } catch (Throwable t) {} }
				else if (k.equals("hitAlpha")) { try { hitAlpha = clamp01(Integer.parseInt(v) / 100.0f, 0.05f, 0.9f); } catch (Throwable t) {} }
				else if (k.equals("tierTagger")) tierTagger = v.equals("1");
				else if (k.equals("ttMode")) ttMode = v;
				else if (k.equals("entDist")) { try { entityDistance = Math.max(0, Math.min(256, Integer.parseInt(v))); } catch (Throwable t) {} }
				else if (k.equals("pwHide")) passwordHider = v.equals("1");
				else if (k.equals("lowFire")) lowFire = v.equals("1");
				else if (k.equals("lowFireH")) { try { lowFireH = clamp01(Integer.parseInt(v) / 100.0f, 0.05f, 1.0f); } catch (Throwable t) {} }
				else if (k.equals("lowShield")) lowShield = v.equals("1");
				else if (k.equals("lowShieldA")) { try { lowShieldAmt = clamp01(Integer.parseInt(v) / 100.0f, 0.0f, 0.6f); } catch (Throwable t) {} }
				else if (k.equals("smallTotem")) smallTotem = v.equals("1");
				else if (k.equals("totemScale")) { try { totemScaleV = clamp01(Integer.parseInt(v) / 100.0f, 0.1f, 1.0f); } catch (Throwable t) {} }
				else if (k.equals("leafCull")) leafCull = net.minecraft.block.TCLeafCulling.parseMode(v);
				else if (k.startsWith("vs")) vs.put(k, v);
				else net.minecraft.client.renderer.TCExtra.parse(k, v);
				}
			}
			net.minecraft.client.renderer.TCExtra.packOn = perfPack;
			net.minecraft.client.renderer.TCExtra.apply();
			applyModSettings();
		} catch (Throwable t) {
			// leave defaults
		}
	}

	private static long lastWorkerCheck, workerIdleSince;
	private static String lastKeys;

	/** Tells the overlay the Treasure Settings and Zoom keys chosen in Options > Controls. */
	private static void publishKeys(Minecraft mc) {
		String k = "menu=" + mc.gameSettings.keyBindTreasureMenu.getKeyCode() + ",zoom="
				+ mc.gameSettings.keyBindTreasureZoom.getKeyCode();
		if (!k.equals(lastKeys)) {
			lastKeys = k;
			try {
				EagRuntime.setStorage("tckeys", k.getBytes(java.nio.charset.StandardCharsets.UTF_8));
			} catch (Throwable t) {}
		}
	}

	/**
	 * Memory Saver: once you're on a server, stop the singleplayer worker if it's
	 * sitting idle. It's a whole second copy of the game (its own memory and CPU)
	 * that only singleplayer needs; clicking Singleplayer starts it again.
	 */
	private static void applyMemorySaver(Minecraft mc) {
		boolean memSaver = memorySaver();
		// Decoded sounds stay 10 minutes either way: a sound that isn't cached is decoded while the
		// game waits (a small freeze each time), and decoded sounds use little memory.
		net.lax1dude.eaglercraft.internal.PlatformAudio.cacheExpireMillis = 600000L;
		if (!memSaver || mc.world == null) {
			return;
		}
		long now = EagRuntime.steadyTimeMillis();
		if (now - lastWorkerCheck < 1000L) {
			return;
		}
		lastWorkerCheck = now;
		try {
			if (!net.lax1dude.eaglercraft.sp.SingleplayerServerController.isWorldRunning()
					&& net.lax1dude.eaglercraft.sp.SingleplayerServerController.isIntegratedServerWorkerAlive()
					&& net.lax1dude.eaglercraft.sp.SingleplayerServerController.isReady()
					&& net.lax1dude.eaglercraft.sp.SingleplayerServerController.canKillWorker()) {
				if (workerIdleSince == 0L) workerIdleSince = now;
				if (now - workerIdleSince >= workerIdleSec * 1000L - 100L) {
					net.lax1dude.eaglercraft.sp.SingleplayerServerController.killWorker();
					workerIdleSince = 0L;
				}
			} else {
				workerIdleSince = 0L;
			}
		} catch (Throwable t) {
		}
	}

	public static void tick(Minecraft mc) {
		try {
			if (mc == null || mc.gameSettings == null) return;

			// re-read settings ~4x/second so overlay changes apply live
			long now = EagRuntime.steadyTimeMillis();
			if (now - lastLoad > 250L) { reload(); lastLoad = now; }

			// switch between the Celeritas and vanilla chunk renderers when the toggle changes
			try { org.taumc.celeritas.impl.TreasureCeleritas.setEnabled(celeritasActive()); } catch (Throwable t) {}

			// Memory Saver
			applyMemorySaver(mc);
			publishKeys(mc);
			net.minecraft.util.TCLithium.enabled = lithium();
			net.lax1dude.eaglercraft.opengl.TCHudBatch.setEnabled(immediatelyFast() && hudBatch);
			net.lax1dude.eaglercraft.opengl.TCModelBatch.setEnabled(immediatelyFast() && modelBatch);
			net.minecraft.client.particle.TCParticleCore.enabled = particleCore();
			net.lax1dude.eaglercraft.socket.WebSocketNetworkManager.tcSpreadBursts = c2me();
			net.lax1dude.eaglercraft.socket.WebSocketNetworkManager.tcFastPing = fastPing();
			net.minecraft.util.math.MathHelper.tcFastMath = betterFpsMath();

			// Enhanced Block Entities changed: rebuild the chunk meshes with / without baked chests and beds
			// Leaf Culling mode changed: rebuild chunk meshes with the new leaf faces
			if (leafCulling() != net.minecraft.block.TCLeafCulling.mode) {
				net.minecraft.block.TCLeafCulling.mode = leafCulling();
				if (mc.world != null && mc.renderGlobal != null) {
					try { mc.renderGlobal.loadRenderers(); } catch (Throwable t) {}
				}
			}

			int ebeNow = enhancedBlockEntities() ? 1 | (net.minecraft.client.renderer.TCBlockEntities.bakeChests ? 2 : 0)
					| (net.minecraft.client.renderer.TCBlockEntities.bakeBeds ? 4 : 0) : 0;
			if (ebeNow != appliedEbe) {
				appliedEbe = ebeNow;
				if (mc.world != null && mc.renderGlobal != null) {
					try { mc.renderGlobal.loadRenderers(); } catch (Throwable t) {}
				}
			}

			// Treasure Client — apply any pending TuffX+ ViaBlocks overrides (modern
			// blocks streamed by real servers) + the F6 item-NBT dump diagnostic.
			try { net.minecraft.client.network.TuffViaBlocks.tick(); } catch (Throwable t) {}
			dumpHeldItemOnKey(mc);

			// Treasure Client — tell the overlay whether we're in a world, so the
			// out-of-game Mod Menu can show the theme background (and the in-game Mod
			// Menu is left untouched, showing the paused world behind it).
			if (now - lastInWorldWrite > 250L) {
				lastInWorldWrite = now;
				try {
					boolean inWorld = (mc.world != null && mc.player != null);
					EagRuntime.setStorage("tcinworld", new byte[] { (byte) (inWorld ? 1 : 0) });
				} catch (Throwable t) {}
			}

			// No Particles: force the game's particle setting to minimal while the
			// toggle is on (keeps applying each reload since the user could change it).
			if (noParticles && mc.gameSettings.particleSetting != 2) {
				mc.gameSettings.particleSetting = 2;
			}

			// ----- Mod Menu open requested from the overlay -----
			// When the overlay opens its ClickGUI during gameplay it sets tcpause=1.
			// We open the in-game menu (same as pressing Escape) so the world pauses
			// in singleplayer and the mouse is released — the user no longer has to
			// press Escape after their Mod Menu hotkey.
			try {
				byte[] pz = EagRuntime.getStorage("tcpause");
				if (pz != null && pz.length > 0) {
					if (pz[0] == 1) {
						EagRuntime.setStorage("tcpause", new byte[] { 0 });
						if (mc.player != null && mc.currentScreen == null) {
							mc.displayInGameMenu();
						}
					} else if (pz[0] == 2) {
						EagRuntime.setStorage("tcpause", new byte[] { 0 });
						// close our in-game menu and return to the world
						if (mc.player != null && mc.currentScreen instanceof net.minecraft.client.gui.GuiIngameMenu) {
							mc.displayGuiScreen((net.minecraft.client.gui.GuiScreen) null);
						}
					}
				}
			} catch (Throwable t) {}

			// only drive the game while actually in a world with no screen open
			boolean active = (mc.player != null && mc.currentScreen == null);

			KeyBinding sprintKey = mc.gameSettings.keyBindSprint;
			KeyBinding sneakKey = mc.gameSettings.keyBindSneak;
			KeyBinding fwdKey = mc.gameSettings.keyBindForward;

			if (!active) {
				forwardWasDown = false;
				doubleTapLatched = false;
				return;
			}

			// ----- Toggle Sprint -----
			// Press the sprint key to flip the mod ON / OFF (shown on the HUD):
			//   ON  = auto-sprint: tap W once and you sprint while moving forward
			//   OFF = vanilla: double-tap W to sprint, as normal
			if (sprint) {
				boolean fwdDown = fwdKey.isKeyDown();
				// Detect the sprint-key toggle press from the RAW hardware key, NOT
				// from sprintKey.isKeyDown() — that returns the engine's `pressed`
				// flag, which we ourselves set below when forcing sprint, so reading
				// it back would flip the toggle off the instant we hold W. Using the
				// real keyboard state avoids that feedback loop.
				int sprintCode = sprintKey.getKeyCode();
				boolean sprintKeyDown = (sprintCode > 0 && sprintCode < 256)
						&& net.lax1dude.eaglercraft.Keyboard.isKeyDown(sprintCode);
				if (sprintKeyDown && !sprintKeyWasDown) {
					sprintToggledOn = !sprintToggledOn; // flip ON <-> OFF
				}
				sprintKeyWasDown = sprintKeyDown;
				if (sprintToggledOn && fwdDown) {
					// ON: hold sprint for the player while moving forward (tap-W-to-sprint)
					KeyBinding.setKeyBindState(sprintCode, true);
				}
				forwardWasDown = fwdDown;
			} else {
				sprintToggledOn = false;
			}

			// ----- Toggle Sneak (Toggle Shift) -----
			// Press the sneak key once to stay sneaking; press again to return to
			// vanilla hold-to-sneak. Disabling the mod fully restores vanilla Shift.
			if (sneak) {
				// Same fix as sprint: read the RAW key, not sneakKey.isKeyDown(),
				// so our own forced sneak state doesn't re-trigger the toggle.
				int sneakCode = sneakKey.getKeyCode();
				boolean sneakKeyDown = (sneakCode > 0 && sneakCode < 256)
						&& net.lax1dude.eaglercraft.Keyboard.isKeyDown(sneakCode);
				if (sneakKeyDown && !sneakKeyWasDown) {
					sneakToggledOn = !sneakToggledOn; // flip latch on each press
				}
				sneakKeyWasDown = sneakKeyDown;
				if (sneakToggledOn) {
					// hold sneak for the player until they press the key again
					KeyBinding.setKeyBindState(sneakCode, true);
				}
			} else {
				// mod OFF: clear any latch so vanilla Shift behaves normally
				if (sneakToggledOn) {
					sneakToggledOn = false;
					try { KeyBinding.setKeyBindState(sneakKey.getKeyCode(), false); } catch (Throwable t) {}
				}
				sneakKeyWasDown = false;
			}

			// ----- Hitboxes (visual-only; uses the same renderer as vanilla F3+B) -----
			try {
				if (mc.getRenderManager() != null) {
					mc.getRenderManager().setDebugBoundingBox(hitboxes);
				}
			} catch (Throwable t) {}
		} catch (Throwable t) {
			// never let a mod bug break the game loop
		}
	}

	/**
	 * Category filter consulted by RenderManager right before it draws a
	 * debug bounding box, so the four Hitboxes sub-toggles (players / mobs /
	 * animals / other) work without touching anything about how entities are
	 * targeted or interacted with.
	 */
	public static boolean shouldShowHitbox(Entity entityIn) {
		if (!hitboxes) return false;
		if (entityIn instanceof EntityPlayer) return hitboxPlayers;
		if (entityIn instanceof IMob) return hitboxMobs;
		if (entityIn instanceof EntityAnimal) return hitboxAnimals;
		return hitboxOther;
	}

	// Read directly from storage (throttled) so this stays correct even while the
	// game loop is skipping ticks in the paused state.
	private static boolean pauseCache = false;
	private static long pauseRead = 0L;
	public static boolean pauseWhenUnfocused() {
		try {
			long now = EagRuntime.steadyTimeMillis();
			if (now - pauseRead > 300L) {
				pauseRead = now;
				byte[] data = EagRuntime.getStorage("tcmods");
				pauseCache = false;
				if (data != null && data.length > 0) {
					String s = new String(data, java.nio.charset.StandardCharsets.UTF_8);
					String[] parts = s.split(",");
					for (int i = 0; i < parts.length; i++) {
						if (parts[i].equals("pauseUnfocused=1")) { pauseCache = true; break; }
					}
				}
			}
		} catch (Throwable t) { pauseCache = false; }
		return pauseCache;
	}

	/**
	 * Zoom FOV multiplier — 1.0 = normal, smaller = more zoomed in. Read from the
	 * overlay: tczoom = "1" while the zoom key is held, and the configured level
	 * lives in the tcmods string as zoomLevel=NN (percent of FOV when zoomed).
	 * Called every frame by EntityRenderer.getFOVModifier, so it's throttled.
	 */
	public static float zoomFactor() {
		try {
			long now = EagRuntime.steadyTimeMillis();
			if (now - zoomRead > 80L) {
				zoomRead = now;
				byte[] z = EagRuntime.getStorage("tczoom");
				zoomActive = (z != null && z.length > 0 && z[0] == 1);
				byte[] data = EagRuntime.getStorage("tcmods");
				if (data != null && data.length > 0) {
					String s = new String(data, java.nio.charset.StandardCharsets.UTF_8);
					String[] parts = s.split(",");
					for (int i = 0; i < parts.length; i++) {
						if (parts[i].startsWith("zoomLevel=")) {
							try {
								int pct = Integer.parseInt(parts[i].substring(10));
								if (pct < 10) pct = 10; if (pct > 90) pct = 90;
								zoomLevelCache = pct / 100.0f;
							} catch (Throwable t) {}
						} else if (parts[i].startsWith("smoothZoom=")) {
							smoothZoom = parts[i].substring(11).equals("1");
						} else if (parts[i].startsWith("zoomSpeed=")) {
							try {
								int sp = Integer.parseInt(parts[i].substring(10));
								if (sp < 1) sp = 1; if (sp > 20) sp = 20;
								zoomSpeed = sp / 100.0f; // fraction of the gap closed per frame-ish
							} catch (Throwable t) {}
						}
					}
				}
			}

			float target = zoomActive ? zoomLevelCache : 1.0f;
			if (!smoothZoom) {
				zoomCurrent = target; // instant (old behavior)
				return target;
			}
			// Smoothly ease the current factor toward the target. Frame-rate
			// independent: step scales by elapsed time since the last call.
			long ft = now - zoomFrameTime;
			if (zoomFrameTime == 0L || ft > 250L) ft = 16L; // first call / big gap
			zoomFrameTime = now;
			// higher zoomSpeed => closes the gap faster. Clamp per-frame alpha to 1.
			float alpha = zoomSpeed * (ft / 16.0f);
			if (alpha > 1.0f) alpha = 1.0f;
			if (alpha < 0.0f) alpha = 0.0f;
			if (zoomCurrent <= 0.0f) zoomCurrent = 1.0f; // init
			zoomCurrent += (target - zoomCurrent) * alpha;
			// snap when extremely close so it fully settles
			if (Math.abs(zoomCurrent - target) < 0.002f) zoomCurrent = target;
			return zoomCurrent;
		} catch (Throwable t) { zoomActive = false; return 1.0f; }
	}
}
