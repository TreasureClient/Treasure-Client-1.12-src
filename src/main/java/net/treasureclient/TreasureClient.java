package net.treasureclient;

import net.treasureclient.compat.V;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.treasureclient.cos.TCShare;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.debug.DebugScreenEntries;
import net.minecraft.client.gui.components.debug.DebugScreenEntryStatus;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.phys.Vec3;
import net.treasureclient.core.Config;
import net.treasureclient.hud.Hud;
import net.treasureclient.hud.Toggles;
import net.treasureclient.hud.Trackers;
import net.treasureclient.ui.ModMenuScreen;
import com.mojang.blaze3d.platform.InputConstants;

/** Treasure Client for real Minecraft: the 1.5 Mod Menu, main menu, HUD mods and themes. */
public final class TreasureClient implements ClientModInitializer {
    public static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(Identifier.fromNamespaceAndPath("treasureclient", "main"));
    public static final KeyMapping MENU_KEY = new KeyMapping("key.treasureclient.menu", V.KEYBOARD, InputConstants.KEY_RSHIFT, CATEGORY);
    public static final KeyMapping ZOOM_KEY = new KeyMapping("key.treasureclient.zoom", V.KEYBOARD, InputConstants.KEY_C, CATEGORY);

    private static Boolean hitboxesApplied;

    @Override
    public void onInitializeClient() {
        net.treasureclient.boost.SmoothPackets.init();
        V.registerKey(MENU_KEY);
        V.registerKey(ZOOM_KEY);
        V.registerHud(Identifier.fromNamespaceAndPath("treasureclient", "hud"), Hud::render);

        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            Config.get().tick();
            net.treasureclient.core.KeyFixer.tick(mc);
            TCShare.tick();
            Trackers.tick();
            Toggles.tick(mc);
            while (MENU_KEY.consumeClick()) {
                if (V.screen() == null) V.open(new ModMenuScreen(null));
            }
            syncHitboxes(mc);
            if (net.treasureclient.ui.TitleScreenTC.replaces(V.screen())) V.open(new net.treasureclient.ui.TitleScreenTC());
        });

        // remember the last server for "Rejoin" on the main menu
        ClientPlayConnectionEvents.JOIN.register((handler, sender, mc) -> {
            ServerData s = mc.getCurrentServer();
            if (s != null && !mc.isLocalServer()) {
                Config c = Config.get();
                c.lastServerName = s.name == null ? "" : s.name;
                c.lastServerAddress = s.ip == null ? "" : s.ip;
                c.markDirty();
            }
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, mc) -> {
            Trackers.reset();
            Toggles.reset();
        });

        // Reach Display: measure from your eyes to the nearest point of what you hit
        AttackEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
            if (world.isClientSide()) {
                Vec3 eye = player.getEyePosition();
                double d = Math.sqrt(entity.getBoundingBox().distanceToSqr(eye));
                Trackers.attacked(entity.getId(), d);
            }
            return InteractionResult.PASS;
        });
    }

    /** A Mod Menu switch changed. */
    public static void onModToggled(String key) {
        if (key.equals("hitboxes")) hitboxesApplied = null;
    }

    /** Hitboxes: Minecraft's own F3+B boxes (visual only), kept on while the mod is on. */
    private static void syncHitboxes(Minecraft mc) {
        boolean want = Config.get().on("hitboxes");
        if (hitboxesApplied != null && hitboxesApplied == want) return;
        if (hitboxesApplied == null && !want && !Config.get().enabled.containsKey("hitboxes")) {
            hitboxesApplied = false; // never touched: leave F3+B as the player set it
            return;
        }
        mc.debugEntries.setStatus(DebugScreenEntries.ENTITY_HITBOXES, want ? DebugScreenEntryStatus.ALWAYS_ON : DebugScreenEntryStatus.NEVER);
        hitboxesApplied = want;
    }
}
