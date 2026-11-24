import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;

/** Makes the blurred theme backgrounds used behind menus (same blur as the browser client: 3 box blurs, radius width/170; saved at half size). */
public class Blur {
    public static void main(String[] a) throws Exception {
        File in = new File(a[0]), out = new File(a[1]);
        out.mkdirs();
        for (File f : in.listFiles((d, n) -> n.endsWith(".png"))) {
            BufferedImage img = ImageIO.read(f);
            int w = img.getWidth(), h = img.getHeight();
            int[] px = img.getRGB(0, 0, w, h, null, 0, w);
            int r = Math.max(2, w / 170);
            for (int p = 0; p < 3; p++) {
                box(px, w, h, r, true);
                box(px, w, h, r, false);
            }
            // half size is plenty for a blurred picture (and 4x smaller)
            BufferedImage o = new BufferedImage(w / 2, h / 2, BufferedImage.TYPE_INT_RGB);
            for (int y = 0; y < h / 2; y++)
                for (int x = 0; x < w / 2; x++) {
                    int rr = 0, gg = 0, bb = 0;
                    for (int dy = 0; dy < 2; dy++)
                        for (int dx = 0; dx < 2; dx++) {
                            int c = px[(y * 2 + dy) * w + x * 2 + dx];
                            rr += c >> 16 & 255; gg += c >> 8 & 255; bb += c & 255;
                        }
                    o.setRGB(x, y, (rr / 4) << 16 | (gg / 4) << 8 | (bb / 4));
                }
            ImageIO.write(o, "png", new File(out, f.getName()));
        }
    }

    static void box(int[] px, int w, int h, int r, boolean horiz) {
        int len = horiz ? w : h, lines = horiz ? h : w, div = r * 2 + 1;
        int[] line = new int[len];
        for (int l = 0; l < lines; l++) {
            for (int i = 0; i < len; i++) line[i] = px[horiz ? l * w + i : i * w + l];
            int sr = 0, sg = 0, sb = 0;
            for (int k = -r; k <= r; k++) {
                int c = line[Math.max(0, Math.min(len - 1, k))];
                sr += c >> 16 & 255; sg += c >> 8 & 255; sb += c & 255;
            }
            for (int i = 0; i < len; i++) {
                px[horiz ? l * w + i : i * w + l] = 0xFF000000 | (sr / div) << 16 | (sg / div) << 8 | (sb / div);
                int add = line[Math.min(len - 1, i + r + 1)], sub = line[Math.max(0, i - r)];
                sr += (add >> 16 & 255) - (sub >> 16 & 255);
                sg += (add >> 8 & 255) - (sub >> 8 & 255);
                sb += (add & 255) - (sub & 255);
            }
        }
    }
}
