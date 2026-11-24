import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;

/** Writes flat see-through textures that replace Minecraft's dark list panels and header lines. */
public class Flat {
    static void img(File dir, String name, int w, int h, int[] rows) throws Exception {
        BufferedImage o = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) o.setRGB(x, y, rows[Math.min(y, rows.length - 1)]);
        ImageIO.write(o, "png", new File(dir, name));
    }
    public static void main(String[] a) throws Exception {
        File d = new File(a[0]);
        d.mkdirs();
        img(d, "menu_list_background.png", 16, 16, new int[]{0x55000000});
        img(d, "inworld_menu_list_background.png", 16, 16, new int[]{0x40000000});
        img(d, "menu_background.png", 16, 16, new int[]{0x30000000});
        img(d, "inworld_menu_background.png", 16, 16, new int[]{0x30000000});
        img(d, "tab_header_background.png", 16, 16, new int[]{0x66000000});
        // a faint light line over a faint dark one, like a thin divider
        img(d, "header_separator.png", 32, 2, new int[]{0x2EFFFFFF, 0x33000000});
        img(d, "inworld_header_separator.png", 32, 2, new int[]{0x2EFFFFFF, 0x33000000});
        img(d, "footer_separator.png", 32, 2, new int[]{0x33000000, 0x2EFFFFFF});
        img(d, "inworld_footer_separator.png", 32, 2, new int[]{0x33000000, 0x2EFFFFFF});
    }
}
