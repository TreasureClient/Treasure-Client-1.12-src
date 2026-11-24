package net.treasureclient.ui;

import net.treasureclient.compat.V;
import net.treasureclient.compat.TCScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.treasureclient.core.Config;
import net.treasureclient.core.Theme;
import net.treasureclient.hud.Hud;
import com.mojang.blaze3d.platform.InputConstants;

import java.util.Locale;
import java.util.Map;

/** The HUD Editor: drag any HUD mod that's on to move it; Reset Positions puts them all back. */
public class HudEditorScreen extends TCScreen implements Hud.HudEditorScreenMarker {
    private final Screen parent;
    private Map<String, float[]> boxes = Map.of();
    private String dragging;
    private float offX, offY;
    private float[] resetBox = new float[4], doneBox = new float[4];

    public HudEditorScreen(Screen parent) {
        super(Component.literal("HUD Editor"));
        this.parent = parent;
    }


    @Override
    protected void draw(int mouseX, int mouseY, float delta) {
        Ui.begin();
        try {
            Theme th = Theme.current();
            float mx = Ui.mx(mouseX), my = Ui.my(mouseY);
            Ui.rect(0, 0, Ui.vw, Ui.vh, 0x8C060810);
            if (minecraft.player != null) boxes = Hud.drawAll(dragging);
            for (Map.Entry<String, float[]> e : boxes.entrySet()) {
                float[] b = e.getValue();
                boolean active = e.getKey().equals(dragging) || Ui.in(mx, my, b[0], b[1], b[2], b[3]);
                dashed(b[0] - 4, b[1] - 4, b[2] + 8, b[3] + 8, active ? th.hi() : th.a1());
            }
            // the bar at the bottom
            String msg = "DRAG HUD ELEMENTS TO REPOSITION";
            float s = 11, ls = .06f, lh = Ui.lineH(Ui.MONO, s);
            float mw = Ui.width(Ui.MONO, msg, s, ls);
            float rw = Ui.width(Ui.MONO, "RESET POSITIONS", s, ls) + 26, dw = Ui.width(Ui.MONO, "DONE", s, ls) + 26;
            float bh = lh + 14;
            float w = 16 + mw + 10 + rw + 6 + dw + 7, h = bh + 14;
            float x = (Ui.vw - w) / 2, y = Ui.vh - 22 - h;
            Ui.round(x, y, w, h, 3, 0xDB09090B);
            Ui.rect(x, y + 1, 2, h - 2, th.a1());
            Ui.mono(msg, x + 16, y + (h - lh) / 2, s, Theme.INK_DIM, ls);
            float bx = x + 16 + mw + 10, by = y + 7;
            resetBox = new float[]{bx, by, rw, bh};
            Ui.round(bx, by, rw, bh, 2, Ui.in(mx, my, bx, by, rw, bh) ? 0x29FFFFFF : 0x14FFFFFF);
            Ui.mono("Reset Positions", bx + 13, by + 7, s, Theme.INK, ls);
            bx += rw + 6;
            doneBox = new float[]{bx, by, dw, bh};
            Ui.round(bx, by, dw, bh, 2, Ui.in(mx, my, bx, by, dw, bh) ? th.hi() : th.a1());
            Ui.mono("Done", bx + 13, by + 7, s, 0xFF0B0B0D, ls);
        } finally {
            Ui.end();
        }
    }

    private static void dashed(float x, float y, float w, float h, int col) {
        float d = 4;
        for (float i = 0; i < w; i += d * 2) {
            Ui.rect(x + i, y, Math.min(d, w - i), 1, col);
            Ui.rect(x + i, y + h - 1, Math.min(d, w - i), 1, col);
        }
        for (float i = 0; i < h; i += d * 2) {
            Ui.rect(x, y + i, 1, Math.min(d, h - i), col);
            Ui.rect(x + w - 1, y + i, 1, Math.min(d, h - i), col);
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent e, boolean dbl) {
        float mx = Ui.mx(e.x()), my = Ui.my(e.y());
        if (Ui.in(mx, my, doneBox[0], doneBox[1], doneBox[2], doneBox[3])) {
            onClose();
            return true;
        }
        if (Ui.in(mx, my, resetBox[0], resetBox[1], resetBox[2], resetBox[3])) {
            Config.get().hud.clear();
            Config.get().markDirty();
            return true;
        }
        String hit = null;
        for (Map.Entry<String, float[]> en : boxes.entrySet()) {
            float[] b = en.getValue();
            if (Ui.in(mx, my, b[0], b[1], b[2], b[3])) hit = en.getKey(); // the last one drawn is on top
        }
        if (hit != null) {
            dragging = hit;
            float[] b = boxes.get(hit);
            offX = mx - b[0];
            offY = my - b[1];
        }
        return true;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent e, double dx, double dy) {
        if (dragging != null) {
            float[] b = boxes.get(dragging);
            float x = Math.max(0, Math.min(Ui.vw - b[2], Ui.mx(e.x()) - offX));
            float y = Math.max(0, Math.min(Ui.vh - b[3], Ui.my(e.y()) - offY));
            Config.get().hud.put(dragging, new float[]{x / Ui.vw, y / Ui.vh});
            Config.get().markDirty();
        }
        return true;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent e) {
        dragging = null;
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent e) {
        if (e.key() == InputConstants.KEY_ESCAPE) {
            onClose();
            return true;
        }
        return super.keyPressed(e);
    }

    @Override
    public void onClose() {
        Config.get().saveNow();
        V.open(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return true;
    }
}
