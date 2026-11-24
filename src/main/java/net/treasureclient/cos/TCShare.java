package net.treasureclient.cos;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.regex.Pattern;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.treasureclient.core.Config;

/**
 * Treasure Client cosmetics: sharing looks through the Treasure Client cosmetics server, the same
 * one the browser editions use (so players on every edition see each other's cosmetics).
 *
 * The server remembers one look per player name. The first time you share a name it gives back a
 * key, kept in the settings file, so nobody else can change that name's cosmetics. Nothing else is
 * sent: just names to look up, and your name and look when you change it.
 */
public final class TCShare {
    private TCShare() {}

    /** The Treasure Client cosmetics server ("" = sharing off). */
    private static final String API = "https://tc-cosmetics.arjuntxca00.workers.dev";
    private static final Pattern NAME = Pattern.compile("^[A-Za-z0-9_]{1,16}$");
    private static final Pattern LOOK = Pattern.compile("^[a-z0-9=;.]{1,300}$");

    private static HttpClient http;
    private static final ArrayDeque<String> QUEUE = new ArrayDeque<>();
    /** Answers from the web threads, applied on the client thread. */
    private static final ConcurrentLinkedQueue<Runnable> DONE = new ConcurrentLinkedQueue<>();
    private static volatile boolean busy, pubBusy;
    private static long lastPoll, pubRetryAt, lastPubAt;
    private static String lastPub, sentPub;
    /** on, taken, offline, off, or "" (nothing shared yet). */
    private static volatile String status = API.isEmpty() ? "off" : "";

    public static String status() {
        return status;
    }

    private static HttpClient http() {
        if (http == null) http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();
        return http;
    }

    private static void post(String path, JsonObject body, java.util.function.BiConsumer<Integer, JsonObject> then) {
        HttpRequest req = HttpRequest.newBuilder(URI.create(API + path))
            .timeout(Duration.ofSeconds(10))
            .header("Content-Type", "text/plain")
            .header("User-Agent", "TreasureClient-Mod")
            .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
            .build();
        http().sendAsync(req, HttpResponse.BodyHandlers.ofString()).whenComplete((res, err) -> {
            int code = err != null || res == null ? -1 : res.statusCode();
            JsonObject json = null;
            if (res != null) {
                try {
                    JsonElement e = JsonParser.parseString(res.body());
                    if (e.isJsonObject()) json = e.getAsJsonObject();
                } catch (Exception ignored) {
                }
            }
            final int c = code;
            final JsonObject j = json;
            DONE.add(() -> then.accept(c, j));
        });
    }

    /** Called every client tick. */
    public static void tick() {
        TCCosRender.tick();
        Runnable r;
        while ((r = DONE.poll()) != null) {
            try {
                r.run();
            } catch (Exception e) {
                System.err.println("[Treasure Client] cosmetics: " + e);
            }
        }
        if (API.isEmpty()) return;
        long now = System.currentTimeMillis();
        if (now - lastPoll < 1000L) return;
        lastPoll = now;
        for (String n : TCCosmetics.takePending()) {
            if (NAME.matcher(n).matches() && !QUEUE.contains(n) && QUEUE.size() < 400) QUEUE.add(n);
        }
        if (!busy && !QUEUE.isEmpty()) lookUp();
        publish(now);
    }

    private static void lookUp() {
        JsonArray names = new JsonArray();
        final List<String> batch = new java.util.ArrayList<>();
        while (!QUEUE.isEmpty() && batch.size() < 50) batch.add(QUEUE.poll());
        for (String n : batch) names.add(n);
        JsonObject body = new JsonObject();
        body.add("names", names);
        busy = true;
        post("/v1/looks", body, (code, json) -> {
            busy = false;
            Map<String, String> out = new HashMap<>();
            JsonObject looks = json != null && json.has("looks") && json.get("looks").isJsonObject() ? json.getAsJsonObject("looks") : null;
            for (String n : batch) {
                String k = n.toLowerCase(Locale.ROOT), v = "";
                if (looks != null && looks.has(k) && looks.get(k).isJsonPrimitive()) {
                    String s = looks.get(k).getAsString();
                    if (LOOK.matcher(s).matches()) v = s;
                }
                // no answer at all (offline): leave them to be asked again later
                if (code == 200) out.put(n, v);
            }
            TCCosmetics.deliver(out);
        });
    }

    /** Shares your look under your name when it changed. */
    private static void publish(long now) {
        if (pubBusy || now < pubRetryAt || !TCCosmetics.everSet()) return;
        String name = TCCosmetics.myName();
        if (name == null || !NAME.matcher(name).matches()) return;
        String look = TCCosmetics.mine().encode();
        if (!LOOK.matcher(look).matches()) return;
        String pub = name + "\n" + look;
        if (pub.equals(lastPub)) return;
        // the server takes at most 20 changes a minute: while you click through choices, send the latest every 4 s
        if (now - lastPubAt < 4000L) return;
        lastPubAt = now;
        String key = Config.get().cosmeticKeys.get(name.toLowerCase(Locale.ROOT));
        JsonObject body = new JsonObject();
        body.addProperty("name", name);
        body.addProperty("look", look);
        if (key != null) body.addProperty("token", key);
        else body.add("token", com.google.gson.JsonNull.INSTANCE);
        pubBusy = true;
        sentPub = pub;
        post("/v1/set", body, (code, json) -> {
            pubBusy = false;
            if (code == 200) {
                if (json != null && json.has("token") && json.get("token").isJsonPrimitive()) {
                    Config.get().cosmeticKeys.put(name.toLowerCase(Locale.ROOT), json.get("token").getAsString());
                    Config.get().markDirty();
                }
                status = "on";
                lastPub = sentPub;
            } else if (code == 409) {
                // another browser or computer shared this name first (it can take over after 30 days unused)
                status = "taken";
                lastPub = sentPub;
            } else {
                status = "offline";
                pubRetryAt = System.currentTimeMillis() + 30_000L;
            }
        });
    }
}
