package net.minecraft.client.renderer;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.lax1dude.eaglercraft.opengl.ImageData;
import net.lax1dude.eaglercraft.opengl.WorldRenderer;
import net.minecraft.block.Block;
import net.minecraft.block.BlockBed;
import net.minecraft.block.BlockChest;
import net.minecraft.block.BlockEnderChest;
import net.minecraft.block.BlockHorizontal;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.TreasureMods;
import net.minecraft.client.model.ModelBed;
import net.minecraft.client.model.ModelBox;
import net.minecraft.client.model.ModelChest;
import net.minecraft.client.model.ModelLargeChest;
import net.minecraft.client.model.ModelRenderer;
import net.minecraft.client.model.PositionTextureVertex;
import net.minecraft.client.model.TexturedQuad;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.FaceBakery;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.block.model.ItemCameraTransforms;
import net.minecraft.client.renderer.block.model.ItemOverrideList;
import net.minecraft.client.renderer.block.model.SimpleBakedModel;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.item.EnumDyeColor;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityBed;
import net.minecraft.tileentity.TileEntityChest;
import net.minecraft.tileentity.TileEntityEnderChest;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;

/**
 * Treasure Client: Enhanced Block Entities, based on the mod by FoundationGames.
 *
 * <p>In 1.12, chests, trapped chests, ender chests and beds aren't part of the
 * world mesh: each one is redrawn piece by piece every frame by its block
 * entity renderer. That gets expensive in lobbies full of chests and on Bedwars
 * maps. This bakes them into the chunk mesh like normal blocks instead, so they
 * cost almost nothing to draw. A chest goes back to its normal renderer only
 * while its lid moves, then gets baked again.
 *
 * <p>The geometry comes straight from the game's own ModelChest / ModelBed and
 * the same transforms their renderers use, so baked blocks look the same. Their
 * textures are added to the block atlas (double chest textures are padded to a
 * square, since the atlas needs square images).
 */
public final class TCBlockEntities {

	private TCBlockEntities() {
	}

	private static final String[] CHEST_TEXTURES = { "entity/chest/normal", "entity/chest/trapped", "entity/chest/ender",
			"entity/chest/normal_double", "entity/chest/trapped_double" };

	private static final Map<String, IBakedModel> MODELS = new HashMap<>();
	private static Boolean christmas;
	/** Video Settings: which block entities are baked into the chunk mesh. */
	public static boolean bakeChests = true, bakeBeds = true;

	public static boolean enabled() {
		return TreasureMods.enhancedBlockEntities();
	}

	private static boolean isChristmas() {
		if (christmas == null) {
			Calendar c = Calendar.getInstance();
			christmas = c.get(Calendar.MONTH) + 1 == 12 && c.get(Calendar.DAY_OF_MONTH) >= 24
					&& c.get(Calendar.DAY_OF_MONTH) <= 26;
		}
		return christmas;
	}

	// ---- textures --------------------------------------------------------

	/** Adds the chest and bed textures to the block atlas. Called while the atlas collects its sprites. */
	public static void registerSprites(TextureMap map) {
		for (String s : CHEST_TEXTURES) {
			map.registerSprite(new ResourceLocation(s));
		}
		for (EnumDyeColor c : EnumDyeColor.values()) {
			map.registerSprite(new ResourceLocation("entity/bed/" + c.func_192396_c()));
		}
	}

	/** The atlas only takes square images: pad the 128x64 double chest textures to 128x128. */
	public static ImageData fixSpriteImage(String path, ImageData img) {
		if (img == null || !path.contains("entity/chest/") || img.width != img.height * 2) {
			return img;
		}
		int[] px = new int[img.width * img.width];
		System.arraycopy(img.pixels, 0, px, 0, img.pixels.length);
		return new ImageData(img.width, img.width, px, true);
	}

	/** The atlas was rebuilt: baked models point at old sprite positions, so make them again. */
	public static void onAtlasReload() {
		MODELS.clear();
	}

	// ---- chunk meshing ---------------------------------------------------

	/**
	 * Draws a chest or bed into the chunk mesh. Returns false (and draws nothing)
	 * for other blocks, or when this one should use its normal renderer.
	 */
	public static boolean renderBlock(BlockModelRenderer renderer, IBlockAccess world, IBlockState state, BlockPos pos,
			WorldRenderer buffer) {
		if (!enabled()) {
			return false;
		}
		TileEntity te = world.getTileEntity(pos);
		if (te == null) {
			return false;
		}
		te.tcInMesh = false; // until it's drawn below; the normal renderer takes over meanwhile
		try {
			IBakedModel model = modelFor(world, state, pos, te);
			if (model == null) {
				return false;
			}
			boolean res = renderer.renderModel(world, model, state, pos, buffer, true);
			te.tcInMesh = true;
			return res;
		} catch (Throwable t) {
			return false;
		}
	}

	private static IBakedModel modelFor(IBlockAccess world, IBlockState state, BlockPos pos, TileEntity te) {
		Block block = state.getBlock();
		if (block instanceof BlockChest) {
			if (isChristmas() || !bakeChests) {
				return null;
			}
			if (!(te instanceof TileEntityChest) || te.tcBakeAnimating) {
				return null;
			}
			// like the chest renderer: of a double chest, the one with no chest at -X or -Z draws both halves
			if (world.getBlockState(pos.north()).getBlock() == block || world.getBlockState(pos.west()).getBlock() == block) {
				return null; // drawn by the other half (or by its renderer while that half's lid moves)
			}
			boolean xPos = world.getBlockState(pos.east()).getBlock() == block;
			boolean zPos = !xPos && world.getBlockState(pos.south()).getBlock() == block;
			if (xPos || zPos) {
				TileEntity other = world.getTileEntity(xPos ? pos.east() : pos.south());
				if (other != null && other.tcBakeAnimating) {
					return null;
				}
			}
			boolean trapped = ((BlockChest) block).chestType == BlockChest.Type.TRAP;
			int meta = state.getValue(BlockChest.FACING).getIndex();
			return chestModel(trapped ? "trapped" : "normal", meta, xPos, zPos);
		}
		if (block instanceof BlockEnderChest) {
			if (!bakeChests || !(te instanceof TileEntityEnderChest) || te.tcBakeAnimating) {
				return null;
			}
			return chestModel("ender", state.getValue(BlockEnderChest.FACING).getIndex(), false, false);
		}
		if (block instanceof BlockBed) {
			if (!bakeBeds || !(te instanceof TileEntityBed)) {
				return null;
			}
			EnumDyeColor color = ((TileEntityBed) te).func_193048_a();
			boolean head = state.getValue(BlockBed.PART) == BlockBed.EnumPartType.HEAD;
			int facing = state.getValue(BlockHorizontal.FACING).getHorizontalIndex();
			return bedModel(color, head, facing);
		}
		return null;
	}

	// ---- block entity renderers ------------------------------------------

	/**
	 * Called before a block entity renderer draws. Returns true when the block is
	 * baked into the chunk mesh, so its renderer should skip it. Also notices when
	 * a chest lid starts or stops moving and re-bakes that chunk.
	 */
	public static boolean skipRenderer(TileEntity te) {
		if (!enabled() || te.getWorld() == null || !te.getWorld().isRemote) {
			return false;
		}
		if (te instanceof TileEntityChest) {
			if (isChristmas()) {
				return false;
			}
			TileEntityChest c = (TileEntityChest) te;
			updateAnimating(te, c.numPlayersUsing > 0 || c.lidAngle > 0.0F || c.prevLidAngle > 0.0F || partnerOpen(c));
		} else if (te instanceof TileEntityEnderChest) {
			TileEntityEnderChest c = (TileEntityEnderChest) te;
			updateAnimating(te, c.numPlayersUsing > 0 || c.lidAngle > 0.0F || c.prevLidAngle > 0.0F);
		} else if (!(te instanceof TileEntityBed)) {
			return false;
		}
		// skip only while the mesh really has it; while the chunk re-bakes, the normal renderer draws it
		return te.tcInMesh && !te.tcBakeAnimating;
	}

	/** The other half of a double chest is open (its lid is drawn by this half's renderer). */
	private static boolean partnerOpen(TileEntityChest c) {
		return isOpen(c.adjacentChestXPos) || isOpen(c.adjacentChestZPos) || isOpen(c.adjacentChestXNeg)
				|| isOpen(c.adjacentChestZNeg);
	}

	private static boolean isOpen(TileEntityChest c) {
		return c != null && (c.numPlayersUsing > 0 || c.lidAngle > 0.0F || c.prevLidAngle > 0.0F);
	}

	/** When a lid starts or stops moving, re-bake the chunk right away (without / with the chest). */
	private static void updateAnimating(TileEntity te, boolean moving) {
		if (moving != te.tcBakeAnimating) {
			te.tcBakeAnimating = moving;
			IBlockState st = te.getWorld().getBlockState(te.getPos());
			// flag 8 = rebuild now instead of queueing it
			Minecraft.getMinecraft().renderGlobal.notifyBlockUpdate(te.getWorld(), te.getPos(), st, st, 8);
		}
	}

	// ---- baking ----------------------------------------------------------

	private static IBakedModel chestModel(String type, int meta, boolean xPos, boolean zPos) {
		boolean large = xPos || zPos;
		String key = "chest/" + type + "/" + meta + "/" + (xPos ? "x" : zPos ? "z" : "s");
		IBakedModel m = MODELS.get(key);
		if (m != null) {
			return m;
		}
		TextureAtlasSprite sprite = sprite("entity/chest/" + type + (large ? "_double" : ""));
		ModelChest model = large ? new ModelLargeChest() : new ModelChest();
		model.chestLid.rotateAngleX = 0.0F;
		model.chestKnob.rotateAngleX = 0.0F;

		// same steps as TileEntityChestRenderer
		Mat mat = new Mat();
		mat.translate(0.0, 1.0, 1.0);
		mat.scale(1.0, -1.0, -1.0);
		mat.translate(0.5, 0.5, 0.5);
		int angle = meta == 2 ? 180 : meta == 4 ? 90 : meta == 5 ? -90 : 0;
		if (meta == 2 && xPos) {
			mat.translate(1.0, 0.0, 0.0);
		}
		if (meta == 5 && zPos) {
			mat.translate(0.0, 0.0, -1.0);
		}
		mat.rotate(angle, 0.0, 1.0, 0.0);
		mat.translate(-0.5, -0.5, -0.5);

		List<BakedQuad> quads = new ArrayList<>();
		float vScale = large ? 0.5F : 1.0F; // double textures were padded to twice their height
		addPart(quads, model.chestLid, mat, sprite, vScale);
		addPart(quads, model.chestKnob, mat, sprite, vScale);
		addPart(quads, model.chestBelow, mat, sprite, vScale);
		m = build(quads, sprite("blocks/planks_oak"));
		MODELS.put(key, m);
		return m;
	}

	private static IBakedModel bedModel(EnumDyeColor color, boolean head, int facing) {
		String key = "bed/" + color.getMetadata() + "/" + head + "/" + facing;
		IBakedModel m = MODELS.get(key);
		if (m != null) {
			return m;
		}
		TextureAtlasSprite sprite = sprite("entity/bed/" + color.func_192396_c());
		ModelBed model = new ModelBed();
		model.func_193769_a(head);

		// same steps as TileEntityBedRenderer
		float rot = 0.0F, tx = 0.0F, tz = 0.0F;
		if (facing == EnumFacing.SOUTH.getHorizontalIndex()) {
			rot = 180.0F;
			tx = 1.0F;
			tz = 1.0F;
		} else if (facing == EnumFacing.WEST.getHorizontalIndex()) {
			rot = -90.0F;
			tz = 1.0F;
		} else if (facing == EnumFacing.EAST.getHorizontalIndex()) {
			rot = 90.0F;
			tx = 1.0F;
		}
		Mat mat = new Mat();
		mat.translate(tx, 0.5625, tz);
		mat.rotate(90.0, 1.0, 0.0, 0.0);
		mat.rotate(rot, 0.0, 0.0, 1.0);

		List<BakedQuad> quads = new ArrayList<>();
		addPart(quads, model.field_193772_a, mat, sprite, 1.0F);
		addPart(quads, model.field_193773_b, mat, sprite, 1.0F);
		for (ModelRenderer leg : model.field_193774_c) {
			addPart(quads, leg, mat, sprite, 1.0F);
		}
		m = build(quads, sprite("blocks/planks_oak"));
		MODELS.put(key, m);
		return m;
	}

	private static TextureAtlasSprite sprite(String name) {
		return Minecraft.getMinecraft().getTextureMapBlocks().getAtlasSprite("minecraft:" + name);
	}

	private static IBakedModel build(List<BakedQuad> quads, TextureAtlasSprite particle) {
		Map<EnumFacing, List<BakedQuad>> faces = new HashMap<>();
		for (EnumFacing f : EnumFacing.values()) {
			faces.put(f, new ArrayList<BakedQuad>());
		}
		return new SimpleBakedModel(quads, faces, true, true, particle, ItemCameraTransforms.DEFAULT,
				ItemOverrideList.NONE);
	}

	/** Turns one model part (and its children) into baked quads, the way ModelRenderer.render places it. */
	private static void addPart(List<BakedQuad> out, ModelRenderer part, Mat parent, TextureAtlasSprite sprite,
			float vScale) {
		if (part.isHidden || !part.showModel) {
			return;
		}
		final double s = 0.0625;
		Mat m = parent.copy();
		m.translate(part.offsetX, part.offsetY, part.offsetZ);
		m.translate(part.rotationPointX * s, part.rotationPointY * s, part.rotationPointZ * s);
		if (part.rotateAngleZ != 0.0F) {
			m.rotateRad(part.rotateAngleZ, 0.0, 0.0, 1.0);
		}
		if (part.rotateAngleY != 0.0F) {
			m.rotateRad(part.rotateAngleY, 0.0, 1.0, 0.0);
		}
		if (part.rotateAngleX != 0.0F) {
			m.rotateRad(part.rotateAngleX, 1.0, 0.0, 0.0);
		}
		for (ModelBox box : part.cubeList) {
			for (TexturedQuad q : box.tcQuads()) {
				out.add(bakeQuad(q, m, s, sprite, vScale));
			}
		}
		if (part.childModels != null) {
			for (ModelRenderer child : part.childModels) {
				addPart(out, child, m, sprite, vScale);
			}
		}
	}

	private static BakedQuad bakeQuad(TexturedQuad q, Mat m, double s, TextureAtlasSprite sprite, float vScale) {
		int[] data = new int[28];
		double[] p = new double[3];
		for (int i = 0; i < 4; ++i) {
			PositionTextureVertex v = q.vertexPositions[i];
			m.transform(v.vector3D.xCoord * s, v.vector3D.yCoord * s, v.vector3D.zCoord * s, p);
			int o = i * 7;
			data[o] = Float.floatToRawIntBits((float) p[0]);
			data[o + 1] = Float.floatToRawIntBits((float) p[1]);
			data[o + 2] = Float.floatToRawIntBits((float) p[2]);
			data[o + 4] = Float.floatToRawIntBits(sprite.getInterpolatedU(v.texturePositionX * 16.0));
			data[o + 5] = Float.floatToRawIntBits(sprite.getInterpolatedV(v.texturePositionY * vScale * 16.0));
		}
		EnumFacing face = FaceBakery.getFacingFromVertexData(data);
		int shade = shadeColor(face);
		for (int i = 0; i < 4; ++i) {
			data[i * 7 + 3] = shade;
		}
		return new BakedQuad(data, -1, face, sprite);
	}

	/** Same per-side shading as normal block models. */
	private static int shadeColor(EnumFacing face) {
		float f;
		switch (face) {
		case DOWN:
			f = 0.5F;
			break;
		case UP:
			f = 1.0F;
			break;
		case NORTH:
		case SOUTH:
			f = 0.8F;
			break;
		default:
			f = 0.6F;
		}
		int i = (int) (f * 255.0F);
		return 0xFF000000 | i << 16 | i << 8 | i;
	}

	/** A small 4x4 matrix that applies transforms in the same order as GL (each call multiplies on the right). */
	private static final class Mat {
		final double[] m = { 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1 }; // row-major

		Mat copy() {
			Mat c = new Mat();
			System.arraycopy(this.m, 0, c.m, 0, 16);
			return c;
		}

		void mul(double[] b) {
			double[] a = this.m, r = new double[16];
			for (int row = 0; row < 4; ++row) {
				for (int col = 0; col < 4; ++col) {
					r[row * 4 + col] = a[row * 4] * b[col] + a[row * 4 + 1] * b[4 + col] + a[row * 4 + 2] * b[8 + col]
							+ a[row * 4 + 3] * b[12 + col];
				}
			}
			System.arraycopy(r, 0, a, 0, 16);
		}

		void translate(double x, double y, double z) {
			mul(new double[] { 1, 0, 0, x, 0, 1, 0, y, 0, 0, 1, z, 0, 0, 0, 1 });
		}

		void scale(double x, double y, double z) {
			mul(new double[] { x, 0, 0, 0, 0, y, 0, 0, 0, 0, z, 0, 0, 0, 0, 1 });
		}

		void rotate(double degrees, double x, double y, double z) {
			rotateRad(Math.toRadians(degrees), x, y, z);
		}

		/** Rotation about a unit axis (only the X, Y or Z axis is used here). */
		void rotateRad(double a, double x, double y, double z) {
			double c = Math.cos(a), s = Math.sin(a), t = 1.0 - c;
			mul(new double[] { t * x * x + c, t * x * y - s * z, t * x * z + s * y, 0, t * x * y + s * z,
					t * y * y + c, t * y * z - s * x, 0, t * x * z - s * y, t * y * z + s * x, t * z * z + c, 0, 0,
					0, 0, 1 });
		}

		void transform(double x, double y, double z, double[] out) {
			double[] a = this.m;
			out[0] = a[0] * x + a[1] * y + a[2] * z + a[3];
			out[1] = a[4] * x + a[5] * y + a[6] * z + a[7];
			out[2] = a[8] * x + a[9] * y + a[10] * z + a[11];
		}
	}
}
