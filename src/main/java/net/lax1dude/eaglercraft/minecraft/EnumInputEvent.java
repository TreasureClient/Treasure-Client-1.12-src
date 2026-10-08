package net.lax1dude.eaglercraft.minecraft;

/**
 * Ported from EaglercraftX 1.8. In 1.8 GuiScreen dispatched clipboard events via
 * fireInputEvent(EnumInputEvent, String); 1.12's GuiScreen has no such hook, so
 * the ported screens keep their fireInputEvent methods as harmless dead code and
 * this enum only exists so those signatures still resolve.
 */
public enum EnumInputEvent {
	CLIPBOARD_COPY, CLIPBOARD_PASTE;
}
