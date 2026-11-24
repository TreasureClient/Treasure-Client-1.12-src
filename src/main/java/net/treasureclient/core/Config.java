package net.treasureclient.core;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Everything Treasure Client remembers, saved to config/treasureclient.json:
 * the theme, which mods are on, each mod's settings and where HUD mods sit on screen.
 */
public final class Config {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static Config instance;

    public String theme = "normal";
    public Map<String, Boolean> enabled = new LinkedHashMap<>();
    public Map<String, String> settings = new LinkedHashMap<>();
    /** HUD positions as fractions of the screen (0..1), keyed by mod. */
    public Map<String, float[]> hud = new LinkedHashMap<>();
    public String lastServerName = "";
    public String lastServerAddress = "";
    /** Treasure Client cosmetics: what you wear (one line, the same as the browser editions'). */
    public String cosmetics = "";
    /** The cosmetics server's key for each name you've shared under (so nobody else can change it). */
    public Map<String, String> cosmeticKeys = new LinkedHashMap<>();

    private transient boolean dirty;
    private transient long dirtyAt;

    public static Config get() {
        if (instance == null) instance = load();
        return instance;
    }

    private static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve("treasureclient.json");
    }

    private static Config load() {
        Path f = file();
        if (Files.exists(f)) {
            try {
                JsonObject o = JsonParser.parseString(Files.readString(f, StandardCharsets.UTF_8)).getAsJsonObject();
                Config c = GSON.fromJson(o, Config.class);
                if (c != null) {
                    if (c.enabled == null) c.enabled = new LinkedHashMap<>();
                    if (c.settings == null) c.settings = new LinkedHashMap<>();
                    if (c.hud == null) c.hud = new LinkedHashMap<>();
                    if (c.theme == null) c.theme = "normal";
                    if (c.cosmetics == null) c.cosmetics = "";
                    if (c.cosmeticKeys == null) c.cosmeticKeys = new LinkedHashMap<>();
                    return c;
                }
            } catch (Exception e) {
                System.err.println("[Treasure Client] settings file unreadable, using defaults: " + e);
            }
        }
        return new Config();
    }

    /** Saves soon (changes made while dragging or typing are batched). */
    public void markDirty() {
        dirty = true;
        dirtyAt = System.currentTimeMillis();
    }

    /** Called every client tick: writes the file half a second after the last change. */
    public void tick() {
        if (dirty && System.currentTimeMillis() - dirtyAt > 500) saveNow();
    }

    public void saveNow() {
        dirty = false;
        try {
            Path f = file();
            Files.createDirectories(f.getParent());
            Path tmp = f.resolveSibling("treasureclient.json.tmp");
            Files.writeString(tmp, GSON.toJson(this), StandardCharsets.UTF_8);
            Files.move(tmp, f, java.nio.file.StandardCopyOption.REPLACE_EXISTING, java.nio.file.StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            System.err.println("[Treasure Client] could not save settings: " + e);
        }
    }

    // ---- mods ----
    public boolean on(String key) {
        Boolean b = enabled.get(key);
        if (b != null) return b;
        ModDef d = Mods.byKey(key);
        return d != null && d.defaultOn();
    }

    public void set(String key, boolean value) {
        enabled.put(key, value);
        markDirty();
    }

    // ---- settings ----
    public String str(String key, String def) {
        String v = settings.get(key);
        return v == null ? def : v;
    }

    public int num(String key, int def) {
        try {
            String v = settings.get(key);
            return v == null ? def : Integer.parseInt(v);
        } catch (NumberFormatException e) {
            return def;
        }
    }

    public boolean flag(String key, boolean def) {
        String v = settings.get(key);
        return v == null ? def : v.equals("1") || v.equals("true");
    }

    public void put(String key, Object value) {
        settings.put(key, value instanceof Boolean b ? (b ? "1" : "0") : String.valueOf(value));
        markDirty();
    }
}
