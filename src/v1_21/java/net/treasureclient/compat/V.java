package net.treasureclient.compat;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.client.gui.components.PlayerSkinWidget;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.resources.Identifier;
import net.treasureclient.mixin.PlayerSkinWidgetAccessor;

/** The bits of Minecraft / Fabric that are named differently in 1.21.11 and 26.x. */
public final class V {
    private V() {}

    public static final InputConstants.Type KEYBOARD = InputConstants.Type.KEYSYM;

    /** The camera the world is seen through. */
    public static net.minecraft.client.Camera camera() {
        return Minecraft.getInstance().gameRenderer.getMainCamera();
    }

    public static Screen screen() {
        return Minecraft.getInstance().screen;
    }

    public static void open(Screen s) {
        Minecraft.getInstance().setScreen(s);
    }

    /** F1 (hide the HUD) is on. */
    public static boolean hudHidden() {
        return Minecraft.getInstance().options.hideGui;
    }

    public static Screen optionsScreen(Screen parent) {
        return new OptionsScreen(parent, Minecraft.getInstance().options);
    }

    /** The main menu player's two head parts (wide and slim arms models), for turning toward the mouse. */
    public static ModelPart[] skinHeads(PlayerSkinWidget w) {
        PlayerSkinWidgetAccessor a = (PlayerSkinWidgetAccessor) w;
        return new ModelPart[]{a.tc$wide().head, a.tc$slim().head};
    }

    /** How far the main menu player has been dragged round (degrees). */
    public static float skinRotationY(PlayerSkinWidget w) {
        return ((PlayerSkinWidgetAccessor) w).tc$rotationY();
    }

    /** Turns the menu player (degrees; Minecraft starts it at 30). */
    public static void setSkinRotationY(PlayerSkinWidget w, float degrees) {
        ((net.treasureclient.mixin.PlayerSkinWidgetAccessor) (Object) w).tc$setRotationY(degrees);
    }

    public static void openLink(Screen parent, String url) {
        ConfirmLinkScreen.confirmLinkNow(parent, url);
    }

    public static void registerKey(KeyMapping k) {
        KeyBindingHelper.registerKeyBinding(k);
    }

    public static void registerHud(Identifier id, Runnable draw) {
        HudElementRegistry.addLast(id, (g, delta) -> {
            Gfx.set(g);
            draw.run();
        });
    }
}
