package net.treasureclient.ui;

import net.minecraft.resources.Identifier;
import net.treasureclient.core.Theme;

/** The theme's background picture (the same pictures as the browser client's title screen), filling the screen. */
public final class TitleBackground {
    private TitleBackground() {}

    /** The sharp picture picked for this theme (also used for the theme cards). */
    public static Identifier texture(Theme t) {
        return Backgrounds.sharp(Backgrounds.of(t));
    }

    /** Draws the picture like CSS background-size: cover (cropped, never stretched); blurred only if the theme asks. */
    public static void draw(Theme t) {
        boolean blur = Backgrounds.blur(t);
        Identifier tex = blur ? Backgrounds.blurred(Backgrounds.of(t)) : texture(t);
        float sw = Ui.vw, sh = Ui.vh;
        float tw = blur ? 512 : 1024, th = blur ? 256 : 512;
        float scale = Math.max(sw / tw, sh / th);
        float uw = sw / scale, vh = sh / scale;
        Ui.image(tex, 0, 0, sw, sh, (tw - uw) / 2f, (th - vh) / 2f, Math.round(uw), Math.round(vh), (int) tw, (int) th, 0xFFFFFFFF);
    }
}
