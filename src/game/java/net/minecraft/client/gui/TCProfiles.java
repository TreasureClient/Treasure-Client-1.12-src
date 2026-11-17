package net.minecraft.client.gui;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import net.lax1dude.eaglercraft.EagRuntime;
import net.lax1dude.eaglercraft.EaglerInputStream;
import net.lax1dude.eaglercraft.EaglerOutputStream;
import net.lax1dude.eaglercraft.opengl.GlStateManager;
import net.lax1dude.eaglercraft.profile.DefaultSkins;
import net.lax1dude.eaglercraft.profile.EaglerProfile;
import net.lax1dude.eaglercraft.profile.EaglerSkinTexture;
import net.lax1dude.eaglercraft.profile.SkinModel;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.ResourceLocation;

/**
 * Treasure Client: Profiles (an alt manager). Each profile is one account with its
 * own name, skin, cape and mod setup.
 *
 * Name, skin and cape: Eaglercraft keeps ONE profile in storage "p" (EaglerProfile).
 * Every Treasure Client profile keeps its own copy of that data here (storage
 * "tcprofiles"), and switching loads that copy into EaglerProfile. Whatever the
 * Edit Profile screen saves goes to the active profile (EaglerProfile.save calls
 * {@link #onSaved}).
 *
 * Mod setup: the mod menu (page overlay) owns it. Java tells the overlay which
 * profile is active through storage "tcprofile" ("activeId\nid,id,id"); the overlay
 * stores each profile's mod settings and swaps them when the active one changes.
 */
public final class TCProfiles {

	private TCProfiles() {
	}

	public static final int MAX = 20;
	private static final String STORE = "tcprofiles";

	public static final class Profile {
		public final int id;
		byte[] data;
		public String name = "?";
		int presetSkin = 0;
		byte[] customSkin;
		int customModel = 0;
		public String skinLabel = "";
		ResourceLocation faceTex;

		Profile(int id, byte[] data) {
			this.id = id;
			setData(data);
		}

		void setData(byte[] b) {
			this.data = b;
			releaseFace();
			try {
				NBTTagCompound c = CompressedStreamTools.readCompressed(new EaglerInputStream(b));
				String n = c.getString("username").trim();
				this.name = n.isEmpty() ? "?" : n;
				int ps = c.getInteger("presetSkin");
				int cs = c.getInteger("customSkin");
				this.customSkin = null;
				if (ps == -1) {
					NBTTagList skins = c.getTagList("skins", 10);
					if (cs >= 0 && cs < skins.tagCount()) {
						NBTTagCompound sk = skins.getCompoundTagAt(cs);
						byte[] d = sk.getByteArray("data");
						if (d.length == 16384) {
							this.customSkin = d;
							this.customModel = sk.getByte("model");
							this.skinLabel = sk.getString("name");
						}
					}
					this.presetSkin = 0;
				} else {
					this.presetSkin = ps;
				}
				if (this.customSkin == null) {
					DefaultSkins ds = DefaultSkins.getSkinFromId(this.presetSkin);
					this.skinLabel = ds != null ? ds.name : "";
				}
			} catch (Throwable t) {
			}
		}

		void releaseFace() {
			if (faceTex != null) {
				try {
					Minecraft.getMinecraft().getTextureManager().deleteTexture(faceTex);
				} catch (Throwable t) {
				}
				faceTex = null;
			}
		}
	}

	private static final List<Profile> list = new ArrayList<Profile>();
	private static int active = -1, nextId = 1;
	private static boolean ready = false, switching = false;
	private static int faceCounter = 0;

	public static List<Profile> list() {
		return list;
	}

	public static int activeId() {
		return active;
	}

	public static Profile get(int id) {
		for (int i = 0; i < list.size(); i++) {
			if (list.get(i).id == id) {
				return list.get(i);
			}
		}
		return null;
	}

	/** At startup, right after EaglerProfile.read(). The current account becomes profile 1. */
	public static void init() {
		try {
			load();
			byte[] cur = EaglerProfile.write();
			Profile a = get(active);
			if (a == null && !list.isEmpty()) {
				a = list.get(0);
			}
			if (a == null) {
				a = new Profile(nextId++, cur);
				list.add(a);
			} else if (cur != null) {
				a.setData(cur); // storage "p" is the truth for the active profile
			}
			active = a.id;
			ready = true;
			persist();
			publish();
		} catch (Throwable t) {
		}
	}

	/** EaglerProfile.save() (Edit Profile screen): keep the active profile's copy up to date. */
	public static void onSaved(byte[] b) {
		if (!ready || switching || b == null) {
			return;
		}
		Profile a = get(active);
		if (a != null) {
			a.setData(b);
			persist();
		}
	}

	public static void switchTo(int id) {
		if (!ready || id == active) {
			return;
		}
		Profile t = get(id);
		if (t == null) {
			return;
		}
		Profile a = get(active);
		if (a != null) {
			byte[] cur = EaglerProfile.write();
			if (cur != null) {
				a.setData(cur);
			}
		}
		switching = true;
		try {
			EaglerProfile.read(t.data);
			EaglerProfile.setName(EaglerProfile.getName()); // refresh the session's name
			EaglerProfile.save();
		} finally {
			switching = false;
		}
		active = id;
		persist();
		publish();
	}

	/** A new profile with a fresh name and skin; its mods start as a copy of the current ones. */
	public static Profile addNew() {
		if (!ready || list.size() >= MAX) {
			return null;
		}
		Profile p = new Profile(nextId++, blank());
		list.add(p);
		persist();
		switchTo(p.id);
		return p;
	}

	public static boolean delete(int id) {
		if (!ready || list.size() <= 1) {
			return false;
		}
		Profile p = get(id);
		if (p == null) {
			return false;
		}
		if (id == active) {
			int i = list.indexOf(p);
			switchTo(list.get(i > 0 ? i - 1 : 1).id);
		}
		p.releaseFace();
		list.remove(p);
		persist();
		publish();
		return true;
	}

	/** Releases the face pictures made for the Profiles screen. */
	public static void releaseFaces() {
		for (int i = 0; i < list.size(); i++) {
			list.get(i).releaseFace();
		}
	}

	/**
	 * Draws a profile's face (with its hat layer) as a size x size square. The active
	 * profile uses the live skin, so the title screen icon always matches.
	 */
	public static void drawFace(Profile p, int x, int y, int size) {
		Minecraft mc = Minecraft.getMinecraft();
		ResourceLocation tex;
		int texH = 64;
		try {
			if (p == null || p.id == active) {
				tex = EaglerProfile.getActiveSkinResourceLocation();
				SkinModel m = EaglerProfile.getActiveSkinModel();
				if (m != null && m.highPoly != null) {
					tex = DefaultSkins.getSkinFromId(0).location;
				} else if (m != null) {
					texH = m.height;
				}
			} else if (p.customSkin != null) {
				if (p.faceTex == null) {
					SkinModel m = SkinModel.getModelFromId(p.customModel);
					p.faceTex = new ResourceLocation("tc:profiles/face_" + (faceCounter++));
					mc.getTextureManager().loadTexture(p.faceTex, new EaglerSkinTexture(p.customSkin, m.width, m.height));
				}
				tex = p.faceTex;
			} else {
				DefaultSkins ds = DefaultSkins.getSkinFromId(p.presetSkin);
				if (ds == null || ds.model.highPoly != null) {
					ds = DefaultSkins.getSkinFromId(0);
				}
				tex = ds.location;
				texH = ds.model.height;
			}
			mc.getTextureManager().bindTexture(tex);
			GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
			GlStateManager.enableBlend();
			Gui.drawScaledCustomSizeModalRect(x, y, 8.0F, 8.0F, 8, 8, size, size, 64.0F, (float) texH);
			Gui.drawScaledCustomSizeModalRect(x, y, 40.0F, 8.0F, 8, 8, size, size, 64.0F, (float) texH);
		} catch (Throwable t) {
		}
	}

	private static byte[] blank() {
		try {
			NBTTagCompound c = new NBTTagCompound();
			int skin;
			do {
				skin = EaglerProfile.rand.nextInt(DefaultSkins.defaultSkinsMap.length);
			} while (DefaultSkins.defaultSkinsMap[skin].model.highPoly != null);
			c.setInteger("presetSkin", skin);
			c.setInteger("customSkin", -1);
			c.setInteger("presetCape", 0);
			c.setInteger("customCape", -1);
			String[] names = { "Captain", "Corsair", "Buccaneer", "Seadog", "Pirate", "Sailor", "Cutlass",
					"Privateer", "Mariner", "Deckhand", "Shipmate", "Bosun", "Helmsman" };
			c.setString("username", names[EaglerProfile.rand.nextInt(names.length)]
					+ (100 + EaglerProfile.rand.nextInt(900)));
			c.setTag("skins", new NBTTagList());
			c.setTag("capes", new NBTTagList());
			EaglerOutputStream bao = new EaglerOutputStream();
			CompressedStreamTools.writeCompressed(c, bao);
			return bao.toByteArray();
		} catch (Throwable t) {
			return EaglerProfile.write();
		}
	}

	private static void load() {
		list.clear();
		try {
			byte[] b = EagRuntime.getStorage(STORE);
			if (b == null) {
				return;
			}
			NBTTagCompound c = CompressedStreamTools.readCompressed(new EaglerInputStream(b));
			active = c.getInteger("active");
			nextId = Math.max(1, c.getInteger("next"));
			NBTTagList l = c.getTagList("list", 10);
			for (int i = 0; i < l.tagCount(); i++) {
				NBTTagCompound e = l.getCompoundTagAt(i);
				byte[] d = e.getByteArray("data");
				if (d.length > 0) {
					int id = e.getInteger("id");
					list.add(new Profile(id, d));
					nextId = Math.max(nextId, id + 1);
				}
			}
		} catch (Throwable t) {
			list.clear();
		}
	}

	private static void persist() {
		try {
			NBTTagCompound c = new NBTTagCompound();
			c.setInteger("active", active);
			c.setInteger("next", nextId);
			NBTTagList l = new NBTTagList();
			for (int i = 0; i < list.size(); i++) {
				NBTTagCompound e = new NBTTagCompound();
				e.setInteger("id", list.get(i).id);
				e.setByteArray("data", list.get(i).data);
				l.appendTag(e);
			}
			c.setTag("list", l);
			EaglerOutputStream bao = new EaglerOutputStream();
			CompressedStreamTools.writeCompressed(c, bao);
			EagRuntime.setStorage(STORE, bao.toByteArray());
		} catch (Throwable t) {
		}
	}

	/** Tells the mod menu which profile's mod setup to use. */
	private static void publish() {
		StringBuilder sb = new StringBuilder();
		sb.append(active).append('\n');
		for (int i = 0; i < list.size(); i++) {
			if (i > 0) {
				sb.append(',');
			}
			sb.append(list.get(i).id);
		}
		try {
			EagRuntime.setStorage("tcprofile", sb.toString().getBytes(StandardCharsets.UTF_8));
		} catch (Throwable t) {
		}
	}
}
