package net.minecraft.client.renderer.texture;

import java.io.InputStream;
import java.io.IOException;
import java.util.List;

import net.lax1dude.eaglercraft.opengl.ImageData;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.item.EnumDyeColor;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.MathHelper;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class LayeredColorMaskTexture extends AbstractTexture {
	/** Access to the Logger, for all your logging needs. */
	private static final Logger LOG = LogManager.getLogger();

	/** The location of the texture. */
	private final ResourceLocation textureLocation;
	private final List<String> listTextures;
	private final List<EnumDyeColor> listDyeColors;

	public LayeredColorMaskTexture(ResourceLocation textureLocationIn, List<String> p_i46101_2_,
			List<EnumDyeColor> p_i46101_3_) {
		this.textureLocation = textureLocationIn;
		this.listTextures = p_i46101_2_;
		this.listDyeColors = p_i46101_3_;
	}

	/**
	 * Treasure Client: banner and shield pictures, decoded once. Every new banner or shield design
	 * (team-colored shields, banners in hotbars and on walls) decoded its base picture and each
	 * pattern picture from scratch: a 39 ms freeze the first time one showed up in a game on a slow
	 * laptop. Cleared when resource packs change. At most a few dozen 64x64 pictures.
	 */
	private static final java.util.HashMap<String, ImageData> TC_DECODED = new java.util.HashMap<String, ImageData>();

	public static void tcClearCache() {
		TC_DECODED.clear();
	}

	/** The decoded picture (shared, never changed by callers). */
	private static ImageData tcRead(IResourceManager resourceManager, ResourceLocation rl) throws IOException {
		String key = rl.toString();
		ImageData img = TC_DECODED.get(key);
		if (img == null) {
			img = TextureUtil.readBufferedImage(resourceManager.getResource(rl).getInputStream());
			if (TC_DECODED.size() < 256) {
				TC_DECODED.put(key, img);
			}
		}
		return img;
	}

	/** Treasure Client: decodes the common banner/shield pictures ahead of time (title screen). */
	public static void tcWarm(IResourceManager resourceManager, String path) {
		try {
			tcRead(resourceManager, new ResourceLocation(path));
		} catch (Throwable t) {
		}
	}

	public void loadTexture(IResourceManager resourceManager) throws IOException {
		this.deleteGlTexture();
		ImageData bufferedimage;
		try {
			ImageData bufferedimage1 = tcRead(resourceManager, this.textureLocation);
			bufferedimage = new ImageData(bufferedimage1.width, bufferedimage1.height, false);
			bufferedimage.drawLayer(bufferedimage1, 0, 0, bufferedimage1.width, bufferedimage1.height, 0, 0,
					bufferedimage1.width, bufferedimage1.height);
			bufferedimage.swapRB();

			for (int j = 0; j < 17 && j < this.listTextures.size() && j < this.listDyeColors.size(); ++j) {
				String s = this.listTextures.get(j);
				int k = ((EnumDyeColor) this.listDyeColors.get(j)).func_193350_e();

				if (s != null) {
					// a copy: the pixels below are recolored in place
					ImageData tcSrc = tcRead(resourceManager, new ResourceLocation(s));
					ImageData bufferedimage2 = new ImageData(tcSrc.width, tcSrc.height, tcSrc.pixels.clone(), tcSrc.alpha);

					if (bufferedimage2.width == bufferedimage.width
							&& bufferedimage2.height == bufferedimage.height /* && bufferedimage2.alpha = false !?!? */) {
						for (int l = 0; l < bufferedimage2.height; ++l) {
							for (int i1 = 0; i1 < bufferedimage2.width; ++i1) {
								int j1 = bufferedimage2.pixels[l * bufferedimage2.width + i1];
								if ((j1 & -16777216) != 0) {
									int k1 = (j1 & 16711680) << 8 & -16777216;
									int l1 = bufferedimage1.pixels[l * bufferedimage1.width + i1];
									int i2 = MathHelper.multiplyColor(l1, k) & 16777215;
									bufferedimage2.pixels[l * bufferedimage2.width + i1] = k1 | i2;
								}
							}
						}

						bufferedimage.drawLayer(bufferedimage2, 0, 0, bufferedimage2.width, bufferedimage2.height, 0, 0,
								bufferedimage2.width, bufferedimage2.height);
						bufferedimage.swapRB();
					}
				}
			}
		} catch (IOException ioexception) {
			LOG.error("Couldn't load layered image", (Throwable) ioexception);
			return;
		}

		TextureUtil.uploadTextureImage(this.getGlTextureId(), bufferedimage);
	}
}