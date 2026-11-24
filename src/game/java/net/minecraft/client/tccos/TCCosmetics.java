package net.minecraft.client.tccos;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.lax1dude.eaglercraft.EagRuntime;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.TreasureButtons;
import net.minecraft.entity.player.EntityPlayer;

/**
 * Treasure Client cosmetics: what you're wearing (cape, aura, hat, boots, and what you made in
 * the Maker), and what other Treasure Client players are wearing.
 *
 * A look is one short line, the same in your storage ("tccos") and on the sharing server:
 * {@code c=frost;a=halo;ac=gold;h=crown;hc=natural;b=rocket;bc=natural;bt=1;mk=stars.gold.blue.star.shine;as=normal;sp=normal;acc=ffd76b}
 *
 * Sharing works like Tier Tagger: the game can't make web requests, so it asks the page overlay.
 *   game -> overlay: tccospub = "name\nlook" (your look, when it changes); tccosreq = names to look up (comma separated)
 *   overlay -> game: tccosdata = lines of "name=look" ("name=" = not a Treasure Client player)
 * Only Treasure Client draws these, so only Treasure Client players see them.
 */
public final class TCCosmetics {

	private TCCosmetics() {
	}

	/** Cape ids in menu order ("none" = your normal Eaglercraft cape, "custom" = the one you made in the Maker). */
	public static final String[] CAPES = { "none", "treasure", "frost", "tropical", "ember", "aurora", "galaxy", "circuit", "theme", "custom" };
	public static final String[] AURAS = { "none", "halo", "orbit", "rising", "ripple", "helix", "custom" };
	/** Aura colours. */
	public static final String[] COLORS = { "theme", "gold", "ice", "aqua", "ember", "violet", "rose", "lime", "white", "rainbow" };
	private static final int[] COLOR_RGB = { 0, 0xFFD76B, 0x7FE9FF, 0x45E3C6, 0xFF7A3C, 0xB38CFF, 0xFF6FA8, 0x9BEA5B, 0xF4F4F4, 0 };
	public static final String[] HATS = { "none", "crown", "tophat", "wizard", "beanie", "catears", "horns", "propeller", "angel", "custom" };
	public static final String[] BOOTS = { "none", "sneakers", "rocket", "winged", "cloud", "frost", "magma", "custom" };
	/** Hat and boots colours ("natural" = the colours they were made with). */
	public static final String[] WEAR_COLORS = { "natural", "theme", "gold", "ice", "aqua", "ember", "violet", "rose", "lime", "white", "black", "rainbow" };
	private static final int[] WEAR_RGB = { 0, 0, 0xFFD76B, 0x7FE9FF, 0x45E3C6, 0xFF7A3C, 0xB38CFF, 0xFF6FA8, 0x9BEA5B, 0xF4F4F4, 0x26262B, 0 };
	public static final String[] SIZES = { "small", "normal", "big" };
	public static final String[] SPEEDS = { "calm", "normal", "lively" };
	/** The Maker's default cape. */
	public static final String DEFAULT_MK = "fade.theme.black.star.shine";

	// the Maker's aura: base.particles.colour1.colour2.height (colours from COLORS)
	public static final String[] AK_BASES = { "none", "ring", "glow", "ripple", "double" };
	public static final String[] AK_PARTS = { "none", "orbit", "rising", "helix", "sparkle", "snow" };
	public static final String[] AK_HEIGHTS = { "low", "waist", "head" };
	public static final String DEFAULT_AK = "ring.sparkle.theme.white.waist";
	// the Maker's hat: base.top.trim.colour1.colour2 (colours from TCCapeArt.PALETTE)
	public static final String[] HK_BASES = { "none", "cap", "beanie", "brim", "crown", "band" };
	public static final String[] HK_TOPS = { "none", "pompom", "cone", "propeller", "ears", "horns", "antenna", "halo" };
	public static final String[] HK_TRIMS = { "none", "stripe", "gems", "stars" };
	public static final String DEFAULT_HK = "cap.pompom.stripe.theme.white";
	// the Maker's boots: shape.extra.trail.colour1.colour2 (colours from TCCapeArt.PALETTE)
	public static final String[] BK_SHAPES = { "sneaker", "boot", "chunky", "cloud" };
	public static final String[] BK_EXTRAS = { "none", "wings", "crystals", "cracks", "nozzle" };
	public static final String[] BK_TRAILS = { "none", "prints", "puffs", "flames", "sparks" };
	public static final String DEFAULT_BK = "sneaker.none.sparks.white.theme";

	public static boolean validAk(String s) {
		return validSpec(s, AK_BASES, AK_PARTS, COLORS, COLORS, AK_HEIGHTS);
	}

	public static boolean validHk(String s) {
		return validSpec(s, HK_BASES, HK_TOPS, HK_TRIMS, TCCapeArt.PALETTE, TCCapeArt.PALETTE);
	}

	public static boolean validBk(String s) {
		return validSpec(s, BK_SHAPES, BK_EXTRAS, BK_TRAILS, TCCapeArt.PALETTE, TCCapeArt.PALETTE);
	}

	/** A Maker design is parts joined with dots, each part from its own list. */
	static boolean validSpec(String s, String[]... lists) {
		if (s == null || s.length() > 80) return false;
		String[] p = s.split("\\.", -1);
		if (p.length != lists.length) return false;
		for (int i = 0; i < p.length; ++i) {
			if (indexOf(lists[i], p[i]) < 0) return false;
		}
		return true;
	}

	/** One player's look. */
	public static final class Look {
		public String cape = "none";
		public String aura = "none";
		public String color = "theme";
		public String hat = "none";
		public String hatColor = "natural";
		public String boots = "none";
		public String bootColor = "natural";
		public boolean trail = true;
		/** The Maker cape: pattern.colour1.colour2.emblem.motion */
		public String mk = DEFAULT_MK;
		public String auraSize = "normal";
		public String auraSpeed = "normal";
		/** The Maker's aura, hat and boots (see DEFAULT_AK etc.). */
		public String ak = DEFAULT_AK, hk = DEFAULT_HK, bk = DEFAULT_BK;
		private String akFrom, hkFrom, bkFrom;
		private String[] akParts, hkParts, bkParts;
		/** Their theme colour (0xRRGGBB), or -1 = yours, live. */
		public int acc = -1;

		public boolean hasCape() {
			return !"none".equals(cape) && TCCapeArt.known(capeArtId());
		}

		public boolean hasAura() {
			return !"none".equals(aura);
		}

		public boolean hasHat() {
			return !"none".equals(hat);
		}

		public boolean hasBoots() {
			return !"none".equals(boots);
		}

		public boolean hasTrail() {
			return this.trail && trailKind() != null;
		}

		/** What the boots leave behind: frost, magma, prints, puffs, flames, sparks, or null. */
		public String trailKind() {
			switch (boots) {
			case "frost": return "frost";
			case "magma": return "magma";
			case "cloud": return "puffs";
			case "rocket": return "flames";
			case "winged": return "sparks";
			case "custom": {
				String t = bkParts()[2];
				return "none".equals(t) ? null : t;
			}
			default: return null;
			}
		}

		/** The trail's colour (0xRRGGBB), or -1 for the boots' own. */
		public int trailTint(long ms) {
			if ("custom".equals(boots)) return TCCapeArt.paletteRgb(bkParts()[4], accent());
			return wearRgb(bootColor, -1, ms);
		}

		/** The Maker designs split into their parts (worked out again only when they change). */
		public String[] akParts() {
			if (ak != akFrom) { akFrom = ak; akParts = ak.split("\\."); }
			return akParts;
		}

		public String[] hkParts() {
			if (hk != hkFrom) { hkFrom = hk; hkParts = hk.split("\\."); }
			return hkParts;
		}

		public String[] bkParts() {
			if (bk != bkFrom) { bkFrom = bk; bkParts = bk.split("\\."); }
			return bkParts;
		}

		/** The id to draw the cape from ("custom:<maker cape>" for the Maker's). */
		public String capeArtId() {
			return "custom".equals(cape) ? "custom:" + mk : cape;
		}

		/** Their theme colour as 0xRRGGBB. */
		public int accent() {
			return acc >= 0 ? acc : TreasureButtons.accent() & 0xFFFFFF;
		}

		/** The aura colour as 0xRRGGBB right now (rainbow changes over time). */
		public int auraRgb(long timeMs, float offset) {
			return colorRgb(color, timeMs, offset);
		}

		/** An aura colour id (from COLORS) as 0xRRGGBB right now. */
		public int colorRgb(String id, long timeMs, float offset) {
			int i = indexOf(COLORS, id);
			if (i <= 0) return accent();
			if ("rainbow".equals(id)) return rainbow(timeMs, offset);
			return COLOR_RGB[i];
		}

		/** A hat or boots colour as 0xRRGGBB, or the given natural colour. */
		public int wearRgb(String id, int natural, long timeMs) {
			int i = indexOf(WEAR_COLORS, id);
			if (i <= 0) return natural;
			if (i == 1) return accent();
			if ("rainbow".equals(id)) return rainbow(timeMs, 0f);
			return WEAR_RGB[i];
		}

		public float auraScale() {
			int i = indexOf(SIZES, auraSize);
			return i == 0 ? 0.78f : (i == 2 ? 1.28f : 1f);
		}

		public float auraSpeedMul() {
			int i = indexOf(SPEEDS, auraSpeed);
			return i == 0 ? 0.55f : (i == 2 ? 1.7f : 1f);
		}

		/** The one-line form (stored and shared). */
		public String encode() {
			return "c=" + cape + ";a=" + aura + ";ac=" + color + ";h=" + hat + ";hc=" + hatColor + ";b=" + boots + ";bc="
					+ bootColor + ";bt=" + (trail ? "1" : "0") + ";mk=" + mk + ";as=" + auraSize + ";sp=" + auraSpeed + ";acc="
					+ hex6(accent()) + ";ak=" + ak + ";hk=" + hk + ";bk=" + bk;
		}

		public boolean isEmpty() {
			return !hasCape() && !hasAura() && !hasHat() && !hasBoots();
		}
	}

	static int rainbow(long timeMs, float offset) {
		float[] c = TCCapeArt.hsv((timeMs % 7000L) / 7000f + offset, 0.6f, 1f);
		return (TCCapeArt.c8(c[0]) << 16) | (TCCapeArt.c8(c[1]) << 8) | TCCapeArt.c8(c[2]);
	}

	/** Reads a look; unknown names and values are skipped (so older or newer clients never break it). */
	public static Look parse(String s, boolean withAccent) {
		Look l = new Look();
		if (s == null) return l;
		for (String part : s.split("[;\n]")) {
			int eq = part.indexOf('=');
			if (eq <= 0) continue;
			String k = part.substring(0, eq).trim(), v = part.substring(eq + 1).trim();
			setField(l, k, v, withAccent);
		}
		return l;
	}

	/** Sets one field from a key ("c"/"cape", "hc", ...) and a value; false if either isn't known. */
	static boolean setField(Look l, String k, String v, boolean withAccent) {
		switch (k) {
		case "c": case "cape": if (indexOf(CAPES, v) < 0) return false; l.cape = v; return true;
		case "a": case "aura": if (indexOf(AURAS, v) < 0) return false; l.aura = v; return true;
		case "ac": case "color": if (indexOf(COLORS, v) < 0) return false; l.color = v; return true;
		case "h": case "hat": if (indexOf(HATS, v) < 0) return false; l.hat = v; return true;
		case "hc": case "hatc": if (indexOf(WEAR_COLORS, v) < 0) return false; l.hatColor = v; return true;
		case "b": case "boots": if (indexOf(BOOTS, v) < 0) return false; l.boots = v; return true;
		case "bc": case "bootc": if (indexOf(WEAR_COLORS, v) < 0) return false; l.bootColor = v; return true;
		case "bt": case "trail": if (!"1".equals(v) && !"0".equals(v)) return false; l.trail = "1".equals(v); return true;
		case "mk": if (!TCCapeArt.validMaker(v)) return false; l.mk = v; return true;
		case "ak": if (!validAk(v)) return false; l.ak = v; return true;
		case "hk": if (!validHk(v)) return false; l.hk = v; return true;
		case "bk": if (!validBk(v)) return false; l.bk = v; return true;
		case "as": case "asize": if (indexOf(SIZES, v) < 0) return false; l.auraSize = v; return true;
		case "sp": case "aspeed": if (indexOf(SPEEDS, v) < 0) return false; l.auraSpeed = v; return true;
		case "acc":
			if (!withAccent || v.length() != 6) return false;
			try {
				l.acc = Integer.parseInt(v, 16) & 0xFFFFFF;
				return true;
			} catch (NumberFormatException e) {
				return false;
			}
		default: return false;
		}
	}

	static String hex6(int rgb) {
		String h = Integer.toHexString(rgb & 0xFFFFFF);
		while (h.length() < 6) h = "0" + h;
		return h;
	}

	// ------------------------------------------------------------------ yours

	private static Look mine = null;
	/** True once you've picked anything (until then nothing is shared, so your name isn't claimed for nothing). */
	private static boolean everSet = false;

	/** Your own cosmetics. */
	public static Look mine() {
		if (mine == null) {
			String s = null;
			try {
				byte[] b = EagRuntime.getStorage("tccos");
				if (b != null) s = new String(b, StandardCharsets.UTF_8);
			} catch (Throwable t) {
			}
			everSet = s != null && !s.isEmpty();
			mine = parse(s, false);
			mine.acc = -1;
		}
		return mine;
	}

	/** Changes one of your cosmetics ("cape", "aura", "color", "hat", "hatc", "boots", "bootc", "trail", "mk", "ak", "hk", "bk", "asize", "aspeed") and saves it. */
	public static boolean set(String what, String id) {
		Look m = mine();
		if ("acc".equals(what) || !setField(m, what, id, false)) return false;
		// editing a Maker design puts it on
		if ("mk".equals(what)) m.cape = "custom";
		else if ("ak".equals(what)) m.aura = "custom";
		else if ("hk".equals(what)) m.hat = "custom";
		else if ("bk".equals(what)) m.boots = "custom";
		everSet = true;
		try {
			EagRuntime.setStorage("tccos", m.encode().getBytes(StandardCharsets.UTF_8));
		} catch (Throwable t) {
		}
		return true;
	}

	// ------------------------------------------------------------------ other players

	private static final class Other {
		Look look;
		long at = -1L, askedAt;
		boolean pending;
		/** The shared text this look came from, so an unchanged one isn't read again. */
		String raw;
	}

	private static final Map<String, Other> OTHERS = new HashMap<>();
	private static final List<String> PENDING = new ArrayList<>();
	/** How long another player's look is kept before asking again (they may have changed it). */
	private static final long KEEP_MS = 180_000L;

	/** What a player in the world is wearing, or null for none. */
	public static Look of(EntityPlayer p) {
		if (p == null) return null;
		Minecraft mc = Minecraft.getMinecraft();
		if (p == mc.player) return mine();
		String key = p.tcCosKey;
		if (key == null) {
			String name = p.getName();
			if (!validName(name)) return null;
			key = p.tcCosKey = name.toLowerCase();
		}
		Other o = OTHERS.get(key);
		if (o == null) {
			if (OTHERS.size() > 600) OTHERS.clear();
			o = new Other();
			OTHERS.put(key, o);
		}
		long now = EagRuntime.steadyTimeMillis();
		if (o.pending && now - o.askedAt > 20_000L) {
			o.pending = false; // no answer (offline?): try again later
			o.at = now;
		}
		if (!o.pending && (o.at < 0L || now - o.at > KEEP_MS)) {
			o.pending = true;
			o.askedAt = now;
			PENDING.add(p.getName());
		}
		return o.look;
	}

	static boolean validName(String n) {
		if (n == null) return false;
		int len = n.length();
		if (len < 1 || len > 16) return false;
		for (int i = 0; i < len; ++i) {
			char c = n.charAt(i);
			if (!(c >= 'a' && c <= 'z' || c >= 'A' && c <= 'Z' || c >= '0' && c <= '9' || c == '_')) return false;
		}
		return true;
	}

	private static long lastPoll = 0L, lastPubCheck = 0L;
	private static String lastPub = null, lastVer = null;

	/** Called every game tick: shares your look when it changes, and swaps lookups with the overlay. */
	public static void tick() {
		long now = EagRuntime.steadyTimeMillis();
		if (now - lastPoll < 500L) return;
		lastPoll = now;
		try {
			if (!PENDING.isEmpty()) {
				StringBuilder sb = new StringBuilder();
				byte[] old = EagRuntime.getStorage("tccosreq");
				if (old != null && old.length > 0 && old.length < 4000) sb.append(new String(old, StandardCharsets.UTF_8));
				for (int i = 0, n = PENDING.size(); i < n; ++i) {
					if (sb.length() > 0) sb.append(',');
					sb.append(PENDING.get(i));
				}
				PENDING.clear();
				EagRuntime.setStorage("tccosreq", sb.toString().getBytes(StandardCharsets.UTF_8));
			}
			// the overlay bumps "tccosver" whenever its list changes: check that small number first, and
			// only read the list (up to 300 players) when it moved
			byte[] ver = EagRuntime.getStorage("tccosver");
			String vs = ver != null ? new String(ver, StandardCharsets.US_ASCII) : "";
			if (!vs.equals(lastVer)) {
				lastVer = vs;
				byte[] raw = EagRuntime.getStorage("tccosdata");
				String s = raw != null ? new String(raw, StandardCharsets.UTF_8) : "";
				{
					// (even if the text is the same: an answer for someone we asked again still counts)
					for (String line : s.split("\n")) {
						int eq = line.indexOf('=');
						if (eq <= 0) continue;
						Other o = OTHERS.get(line.substring(0, eq).toLowerCase());
						if (o == null) continue;
						String v = line.substring(eq + 1);
						if (o.pending || !v.equals(o.raw)) {
							// only a look that's new or changed is read again
							if (!v.equals(o.raw)) {
								o.raw = v;
								Look l = v.isEmpty() ? null : parse(v, true);
								o.look = l == null || l.isEmpty() ? null : l;
							}
							o.pending = false;
							o.at = now;
						}
					}
				}
			}
			if (now - lastPubCheck >= 2000L) {
				lastPubCheck = now;
				publish();
			}
		} catch (Throwable t) {
		}
	}

	/** Shares your look under the name other players see (when it changed). */
	private static void publish() {
		Look m = mine();
		if (!everSet) return;
		Minecraft mc = Minecraft.getMinecraft();
		String name = mc.player != null ? mc.player.getName() : mc.getSession().getProfile().getName();
		if (!validName(name)) return;
		String s = name + "\n" + m.encode();
		if (s.equals(lastPub)) return;
		lastPub = s;
		EagRuntime.setStorage("tccospub", s.getBytes(StandardCharsets.UTF_8));
	}

	public static int indexOf(String[] a, String v) {
		for (int i = 0; i < a.length; ++i) {
			if (a[i].equals(v)) return i;
		}
		return -1;
	}
}
