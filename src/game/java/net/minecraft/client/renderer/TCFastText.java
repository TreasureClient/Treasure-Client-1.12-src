package net.minecraft.client.renderer;

import java.util.List;

import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiUtilRenderComponents;
import net.minecraft.client.gui.TreasureMods;
import net.minecraft.entity.Entity;
import net.minecraft.entity.passive.EntityVillager;
import net.minecraft.scoreboard.ScorePlayerTeam;
import net.minecraft.tileentity.TileEntitySign;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextFormatting;

/**
 * Treasure Client: ImmediatelyFast-style text savings, based on the mod by
 * RaphiMC. Server lobbies are full of floating names and signs, and vanilla
 * redoes a lot of work for each of them every frame:
 * <ul>
 * <li>Name labels built a whole chat component per entity per frame, including
 * a hover event made by writing the entity's data out as text, only to keep
 * the plain text. Now the same text is made directly.</li>
 * <li>Signs re-split and re-measured every line every frame. Now each line is
 * worked out once and reused until the sign's text changes.</li>
 * <li>Sign text is skipped beyond 32 blocks, where it's too small to read.</li>
 * </ul>
 */
public final class TCFastText {

	private TCFastText() {
	}

	/** Sign text further than this (in blocks) isn't drawn: it would be about 2 pixels tall. */
	private static final double SIGN_TEXT_DISTANCE_SQ = 32.0 * 32.0;

	public static boolean enabled() {
		return TreasureMods.immediatelyFast();
	}

	/** The text shown above an entity: same result as getDisplayName().getFormattedText(), much cheaper. */
	public static String labelText(Entity entity) {
		if (!enabled() || entity instanceof EntityVillager) {
			return entity.getDisplayName().getFormattedText();
		}
		// getDisplayName's text with no colors of its own; the hover and click
		// parts never change the formatted text
		String s = ScorePlayerTeam.formatPlayerName(entity.getTeam(), entity.getName());
		return s.isEmpty() ? "" : s + TextFormatting.RESET;
	}

	/** Whether a sign this far away (squared distance in blocks) should draw its text. */
	public static boolean drawSignText(double distanceSq) {
		return !enabled() || distanceSq < SIGN_TEXT_DISTANCE_SQ;
	}

	/** One sign line, split to the sign's width like vanilla does, worked out once per text change. */
	public static String signLine(TileEntitySign sign, int line, FontRenderer font) {
		ITextComponent text = sign.signText[line];
		if (text == null) {
			return null;
		}
		if (enabled()) {
			if (sign.tcLineFrom == null) {
				sign.tcLineFrom = new ITextComponent[sign.signText.length];
				sign.tcLine = new String[sign.signText.length];
			}
			if (sign.tcLineFrom[line] == text) {
				return sign.tcLine[line];
			}
		}
		List<ITextComponent> list = GuiUtilRenderComponents.splitText(text, 90, font, false, true);
		String s = list != null && !list.isEmpty() ? list.get(0).getFormattedText() : "";
		if (enabled()) {
			sign.tcLineFrom[line] = text;
			sign.tcLine[line] = s;
		}
		return s;
	}
}
