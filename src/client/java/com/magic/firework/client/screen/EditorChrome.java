package com.magic.firework.client.screen;

import com.magic.firework.screen.FireworkEditorMenu;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/** Small drawing primitives shared by the editor pages. */
final class EditorChrome {
	private EditorChrome() {
	}

	static void drawPanel(GuiGraphics g, int x, int y) {
		g.fill(x, y, x + EditorLayout.IMAGE_W, y + EditorLayout.IMAGE_H, 0xE6101014);
		int border = 0xFF4A4A52;
		g.fill(x, y, x + EditorLayout.IMAGE_W, y + 1, border);
		g.fill(x, y + EditorLayout.IMAGE_H - 1, x + EditorLayout.IMAGE_W, y + EditorLayout.IMAGE_H, border);
		g.fill(x, y, x + 1, y + EditorLayout.IMAGE_H, border);
		g.fill(x + EditorLayout.IMAGE_W - 1, y, x + EditorLayout.IMAGE_W, y + EditorLayout.IMAGE_H, border);
	}

	/** Draws the 18px background box for every real slot (centre + inventory). */
	static void drawSlotBackgrounds(GuiGraphics g, FireworkEditorMenu menu, int left, int top) {
		for (Slot slot : menu.slots) {
			if (slot.isActive()) {
				int sx = left + slot.x;
				int sy = top + slot.y;
				g.fill(sx, sy, sx + 18, sy + 18, 0xFF1C1C22);
				int border = 0xFF5A5A62;
				g.fill(sx, sy, sx + 18, sy + 1, border);
				g.fill(sx, sy + 17, sx + 18, sy + 18, border);
				g.fill(sx, sy, sx + 1, sy + 18, border);
				g.fill(sx + 17, sy, sx + 18, sy + 18, border);
			}
		}
	}

	static void drawSlot(GuiGraphics g, Font font, int x, int y, int size, ItemStack item, int border,
			boolean showIcon) {
		g.fill(x, y, x + size, y + size, 0xFF232329);
		g.fill(x, y, x + size, y + 1, border);
		g.fill(x, y + size - 1, x + size, y + size, border);
		g.fill(x, y, x + 1, y + size, border);
		g.fill(x + size - 1, y, x + size, y + size, border);
		if (showIcon && !item.isEmpty()) {
			int pad = (size - 16) / 2;
			g.renderItem(item, x + pad, y + pad);
			g.renderItemDecorations(font, item, x + pad, y + pad);
		}
	}

	/** A small square stepper button; disabled buttons are dimmed and ignore clicks. */
	static void drawStepButton(GuiGraphics g, Font font, int x, int y, String label, boolean enabled,
			boolean hover, int alpha) {
		int fill = !enabled ? 0xFF1E1E24 : (hover ? 0xFF3A3A44 : 0xFF2A2A32);
		int border = !enabled ? ((alpha << 24) | 0x4A4A52) : (hover ? 0xFFFFFFFF : ((alpha << 24) | 0x6A6A72));
		int text = !enabled ? ((alpha << 24) | 0x6A6A72) : (hover ? 0xFFFFFFFF : ((alpha << 24) | 0xD0D0D0));
		g.fill(x, y, x + EditorLayout.STEP_BTN_W, y + EditorLayout.STEP_BTN_H, fill);
		g.fill(x, y, x + EditorLayout.STEP_BTN_W, y + 1, border);
		g.fill(x, y + EditorLayout.STEP_BTN_H - 1, x + EditorLayout.STEP_BTN_W, y + EditorLayout.STEP_BTN_H, border);
		g.fill(x, y, x + 1, y + EditorLayout.STEP_BTN_H, border);
		g.fill(x + EditorLayout.STEP_BTN_W - 1, y, x + EditorLayout.STEP_BTN_W, y + EditorLayout.STEP_BTN_H, border);
		g.drawCenteredString(font, label, x + EditorLayout.STEP_BTN_W / 2,
				y + (EditorLayout.STEP_BTN_H - 8) / 2, text);
	}

	static void drawTextButton(GuiGraphics g, Font font, int x, int y, int w, int h, Component label,
			boolean hover, int alpha) {
		int fill = hover ? 0xFF3A3A44 : 0xFF2A2A32;
		int border = hover ? 0xFFFFFFFF : ((alpha << 24) | 0x6A6A72);
		g.fill(x, y, x + w, y + h, fill);
		g.fill(x, y, x + w, y + 1, border);
		g.fill(x, y + h - 1, x + w, y + h, border);
		g.fill(x, y, x + 1, y + h, border);
		g.fill(x + w - 1, y, x + w, y + h, border);
		g.drawCenteredString(font, label, x + w / 2, y + (h - 8) / 2,
				hover ? 0xFFFFFFFF : ((alpha << 24) | 0xD0D0D0));
	}
}
