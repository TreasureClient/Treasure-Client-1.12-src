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
				String rn = (it != null && it.getRegistryName() != null) ? it.getRegistryName().toString() : "?";
				String nbt = (st.getTagCompound() != null) ? st.getTagCompound().toString() : "(no NBT)";
				msg = "[ViaItems] " + rn + " x" + st.func_190916_E()
						+ " name='" + st.getDisplayName() + "' nbt=" + nbt;
			}
			System.out.println(msg);
			try {
				mc.ingameGUI.getChatGUI().printChatMessage(
						new net.minecraft.util.text.TextComponentString(msg));
			} catch (Throwable t) {}
		} catch (Throwable t) {}
	}

	private static void reload() {
		try {
			byte[] data = EagRuntime.getStorage("tcmods");
			sprint = false; sprintMode = 0; sneak = false;
			hitboxes = false; hitboxPlayers = true; hitboxMobs = true; hitboxAnimals = true; hitboxOther = false;
			pauseUnfocused = false;
			via = true; // default ON unless the overlay explicitly says via=0
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
				}
			}
		} catch (Throwable t) {
			// leave defaults
		}
	}

	public static void tick(Minecraft mc) {
		try {
			if (mc == null || mc.gameSettings == null) return;

			// re-read settings ~4x/second so overlay changes apply live
			long now = EagRuntime.steadyTimeMillis();
			if (now - lastLoad > 250L) { reload(); lastLoad = now; }

			// Treasure Client — apply any pending TuffX+ ViaBlocks overrides (modern
			// blocks streamed by real servers) + the F6 item-NBT dump diagnostic.
			try { net.minecraft.client.network.TuffViaBlocks.tick(); } catch (Throwable t) {}
			dumpHeldItemOnKey(mc);

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
