package net.lax1dude.eaglercraft.minecraft;

import static net.lax1dude.eaglercraft.opengl.RealOpenGLEnums.*;

import net.lax1dude.eaglercraft.Mouse;
import net.lax1dude.eaglercraft.internal.EnumCursorType;
import net.lax1dude.eaglercraft.opengl.GlStateManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.util.ResourceLocation;

/**
 * Copyright (c) 2024 lax1dude. All Rights Reserved.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED.
 * IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT,
 * INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT
 * NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
 * PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 * 
 */
public class GuiButtonWithStupidIcons extends GuiButton {

	protected ResourceLocation leftIcon;
	protected float leftIconAspect;
	protected ResourceLocation rightIcon;
	protected float rightIconAspect;

	public GuiButtonWithStupidIcons(int buttonId, int x, int y, int widthIn, int heightIn, String buttonText) {
		super(buttonId, x, y, widthIn, heightIn, buttonText);
	}

	public GuiButtonWithStupidIcons(int buttonId, int x, int y, String buttonText) {
		super(buttonId, x, y, buttonText);
	}

	public GuiButtonWithStupidIcons(int buttonId, int x, int y, int widthIn, int heightIn, String buttonText,
			ResourceLocation leftIcon, float leftIconAspect, ResourceLocation rightIcon, float rightIconAspect) {
		super(buttonId, x, y, widthIn, heightIn, buttonText);
		this.leftIcon = leftIcon;
		this.leftIconAspect = leftIconAspect;
		this.rightIcon = rightIcon;
		this.rightIconAspect = rightIconAspect;
	}

	public GuiButtonWithStupidIcons(int buttonId, int x, int y, String buttonText, ResourceLocation leftIcon,
			float leftIconAspect, ResourceLocation rightIcon, float rightIconAspect) {
		super(buttonId, x, y, buttonText);
		this.leftIcon = leftIcon;
		this.leftIconAspect = leftIconAspect;
		this.rightIcon = rightIcon;
		this.rightIconAspect = rightIconAspect;
	}

	public ResourceLocation getLeftIcon() {
		return leftIcon;
	}

	public ResourceLocation getRightIcon() {
		return rightIcon;
	}

	public void setLeftIcon(ResourceLocation leftIcon, float aspectRatio) {
		this.leftIcon = leftIcon;
		this.leftIconAspect = aspectRatio;
	}

	public void setRightIcon(ResourceLocation rightIcon, float aspectRatio) {
		this.rightIcon = rightIcon;
		this.rightIconAspect = aspectRatio;
	}

	public void func_191745_a(Minecraft mc, int mouseX, int mouseY, float p_191745_4_) {
		if (this.visible) {
			FontRenderer fontrenderer = mc.fontRendererObj;
			GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
			this.hovered = mouseX >= this.xPosition && mouseY >= this.yPosition && mouseX < this.xPosition + this.width
					&& mouseY < this.yPosition + this.height;
			if (this.enabled && this.hovered) {
				Mouse.showCursor(EnumCursorType.HAND);
			}
			GlStateManager.enableBlend();
			GlStateManager.tryBlendFuncSeparate(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA, 1, 0);
			GlStateManager.blendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);

			// Treasure Client: draw the themed rounded button (same as every other
			// button), instead of the vanilla button texture, then draw the icons
			// and label on top. This gives the pause-menu buttons the custom look.
			int textColor = net.minecraft.client.gui.TreasureButtons.drawButton(this, this.xPosition, this.yPosition,
					this.width, this.height, this.hovered, this.enabled);
			GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
			GlStateManager.enableTexture2D();
			GlStateManager.enableBlend();

			this.mouseDragged(mc, mouseX, mouseY);
			int j = textColor;

			int strWidth = fontrenderer.getStringWidth(displayString);
			int strWidthAdj = strWidth - (leftIcon != null ? (int) (16 * leftIconAspect) : 0)
					+ (rightIcon != null ? (int) (16 * rightIconAspect) : 0);
			if (net.minecraft.client.gui.TreasureButtons.lastTextShadow) {
				this.drawString(fontrenderer, this.displayString, this.xPosition + (this.width - strWidthAdj) / 2,
						this.yPosition + (this.height - 8) / 2, j);
			} else {
				fontrenderer.drawString(this.displayString, (float) (this.xPosition + (this.width - strWidthAdj) / 2),
						(float) (this.yPosition + (this.height - 8) / 2), j, false);
			}
			if(leftIcon != null) {
				GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
				mc.getTextureManager().bindTexture(leftIcon);
				GlStateManager.pushMatrix();
				GlStateManager.translate(this.xPosition + (this.width - strWidthAdj) / 2 - 3 - 16 * leftIconAspect, this.yPosition + 2, 0.0f);
				float f = 16.0f / 256.0f;
				GlStateManager.scale(f * leftIconAspect, f, f);
				this.drawTexturedModalRect(0, 0, 0, 0, 256, 256);
				GlStateManager.popMatrix();
			}
			if(rightIcon != null) {
				GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
				mc.getTextureManager().bindTexture(rightIcon);
				GlStateManager.pushMatrix();
				GlStateManager.translate(this.xPosition + (this.width - strWidthAdj) / 2 + strWidth + 3, this.yPosition + 2, 0.0f);
				float f = 16.0f / 256.0f;
				GlStateManager.scale(f * rightIconAspect, f, f);
				this.drawTexturedModalRect(0, 0, 0, 0, 256, 256);
				GlStateManager.popMatrix();
			}
		}
	}

}
