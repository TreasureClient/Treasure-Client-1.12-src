package net.treasureclient.compat;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** A Treasure Client screen: draws everything itself in draw() (no Minecraft blur or dirt behind it). */
public abstract class TCScreen extends Screen {
    private GuiGraphicsExtractor graphics;

    protected TCScreen(Component title) {
        super(title);
    }

    protected abstract void draw(int mouseX, int mouseY, float delta);

    /** Draws Minecraft widgets added with addRenderableWidget (like the 3D player). */
    protected void drawWidgets(int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        graphics = g;
        Gfx.set(g);
        draw(mouseX, mouseY, delta);
    }
}
