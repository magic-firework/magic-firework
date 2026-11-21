package com.magic.firework.client.screen;

import java.util.List;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/** Draws the editor's panel, pages and tooltips; input and state stay in the screen. */
final class EditorRenderer {
	private final FireworkEditorScreen screen;

	EditorRenderer(FireworkEditorScreen screen) {
		this.screen = screen;
	}

	void renderBg(GuiGraphics g, int mouseX, int mouseY) {
		int x = screen.left();
		int y = screen.top();
		Font font = screen.font();

		EditorChrome.drawPanel(g, x, y);
		EditorChrome.drawSlotBackgrounds(g, screen.menu(), x, y);

		g.drawCenteredString(font, Component.translatable("screen.magic-firework.editor.title"),
				x + 190, y + 6, 0xFFFFFFFF);
		ItemStack center = screen.menu().getCenterStack();
		if (!center.isEmpty()) {
			List<Component> lines = EditorText.attributeLines(screen.menu(), center);
			for (int i = 0; i < lines.size(); i++) {
				int col = i / 4;
				int row = i % 4;
				g.drawString(font, lines.get(i),
						x + EditorLayout.ATTR_X + col * EditorLayout.ATTR_COL_GAP,
						y + 22 + row * EditorLayout.ATTR_ROW_GAP, 0xFFC8C8C8);
			}
		}
		g.fill(x + 12, y + 64, x + EditorLayout.IMAGE_W - 12, y + 65, 0xFF2E2E34);

		if (screen.mainAlpha() > 0.02F) {
			renderMainPage(g, x, y, center, mouseX, mouseY);
		}
		if (screen.subAlpha() > 0.02F) {
			if (screen.shownPage() == FireworkEditorScreen.Page.DIRECTION) {
				renderDirectionPage(g, x, y, mouseX, mouseY);
			} else {
				renderFlightPage(g, x, y, mouseX, mouseY);
			}
		}
	}

	void renderCategoryTooltip(GuiGraphics g, int mouseX, int mouseY) {
		if (!screen.mainInteractive() || screen.reveal() <= 0.5F || screen.mainAlpha() < 0.5F) {
			return;
		}
		int slide = screen.categorySlide();
		for (int i = 0; i < EditorLayout.CATEGORIES.size(); i++) {
			if (screen.over(EditorLayout.categoryX(i) + slide, EditorLayout.categoryY(i),
					EditorLayout.SLOT_SIZE, EditorLayout.SLOT_SIZE, mouseX, mouseY)) {
				g.renderTooltip(screen.font(),
						Component.translatable(EditorLayout.CATEGORIES.get(i).labelKey()), mouseX, mouseY);
				break;
			}
		}
	}

	private void renderMainPage(GuiGraphics g, int x, int y, ItemStack center, int mouseX, int mouseY) {
		float alpha = screen.mainAlpha();
		int a = (int) (alpha * 255);

		if (center.isEmpty()) {
			if (alpha > 0.5F) {
				g.drawCenteredString(screen.font(),
						Component.translatable("screen.magic-firework.editor.place_hint"),
						x + 190, y + 110, (a << 24) | 0xE0E0E0);
				g.drawCenteredString(screen.font(),
						Component.translatable("screen.magic-firework.editor.reveal_hint"),
						x + 190, y + 124, (a << 24) | 0x8B8B8B);
			}
			return;
		}
		if (screen.reveal() <= 0.02F) {
			return;
		}
		int slide = screen.categorySlide();
		int slotAlpha = (int) (screen.reveal() * alpha * 255);
		boolean clickable = screen.mainInteractive() && screen.reveal() > 0.5F;
		for (int i = 0; i < EditorLayout.CATEGORIES.size(); i++) {
			EditorLayout.Category category = EditorLayout.CATEGORIES.get(i);
			boolean hover = clickable && screen.over(EditorLayout.categoryX(i) + slide,
					EditorLayout.categoryY(i), EditorLayout.SLOT_SIZE, EditorLayout.SLOT_SIZE, mouseX, mouseY);
			int border = hover ? 0xFFFFFFFF : ((slotAlpha << 24) | 0x9A9AA4);
			EditorChrome.drawSlot(g, screen.font(), x + EditorLayout.categoryX(i) + slide,
					y + EditorLayout.categoryY(i), EditorLayout.SLOT_SIZE, category.icon(), border,
					screen.reveal() >= 0.4F);
			g.drawCenteredString(screen.font(), Component.translatable(category.labelKey()),
					x + EditorLayout.categoryX(i) + slide + EditorLayout.SLOT_SIZE / 2,
					y + EditorLayout.categoryY(i) + EditorLayout.SLOT_SIZE + 3,
					hover ? 0xFFFFFFFF : ((slotAlpha << 24) | 0xB0B0B0));
		}
	}

	private void renderFlightPage(GuiGraphics g, int x, int y, int mouseX, int mouseY) {
		int alpha = (int) (screen.subAlpha() * 255);
		int slide = screen.subSlide();
		int labelColor = (alpha << 24) | 0xC8C8C8;
		int hintColor = (alpha << 24) | 0x8B8B8B;
		int valueColor = (alpha << 24) | 0xFFFFFF;
		float speed = screen.menu().displayedSpeed();
		int flight = screen.menu().displayedFlight();

		g.drawString(screen.font(), Component.translatable("screen.magic-firework.flight.speed"),
				x + EditorLayout.ROW_LABEL_X + slide, y + EditorLayout.ROW1_Y, labelColor);
		g.drawString(screen.font(), Component.translatable("screen.magic-firework.flight.speed_hint"),
				x + EditorLayout.ROW_LABEL_X + slide, y + EditorLayout.ROW1_Y + 13, hintColor);
		g.drawString(screen.font(), String.format(java.util.Locale.ROOT, "%.2f", speed),
				x + EditorLayout.ROW_VALUE_X + slide, y + EditorLayout.ROW1_Y, valueColor);
		boolean interactive = screen.subInteractive();
		boolean speedDown = EditorSteps.SPEED.canDecrease(screen.menu());
		boolean speedUp = EditorSteps.SPEED.canIncrease(screen.menu(), screen.player());
		drawStep(g, x + EditorLayout.STEP_MINUS_X + slide, y + EditorLayout.ROW1_Y - 4, "-", speedDown,
				interactive, mouseX, mouseY, alpha);
		drawStep(g, x + EditorLayout.STEP_PLUS_X + slide, y + EditorLayout.ROW1_Y - 4, "+", speedUp,
				interactive, mouseX, mouseY, alpha);

		g.drawString(screen.font(), Component.translatable("screen.magic-firework.flight.time"),
				x + EditorLayout.ROW_LABEL_X + slide, y + EditorLayout.ROW2_Y, labelColor);
		g.drawString(screen.font(), Component.translatable("screen.magic-firework.flight.time_hint"),
				x + EditorLayout.ROW_LABEL_X + slide, y + EditorLayout.ROW2_Y + 13, hintColor);
		g.drawString(screen.font(), EditorText.secondsText(flight),
				x + EditorLayout.ROW_VALUE_X + slide, y + EditorLayout.ROW2_Y, valueColor);
		boolean flightDown = EditorSteps.FLIGHT.canDecrease(screen.menu());
		boolean flightUp = EditorSteps.FLIGHT.canIncrease(screen.menu(), screen.player());
		drawStep(g, x + EditorLayout.STEP_MINUS_X + slide, y + EditorLayout.ROW2_Y - 4, "-", flightDown,
				interactive, mouseX, mouseY, alpha);
		drawStep(g, x + EditorLayout.STEP_PLUS_X + slide, y + EditorLayout.ROW2_Y - 4, "+", flightUp,
				interactive, mouseX, mouseY, alpha);

		drawBackButton(g, x, y, alpha, interactive, mouseX, mouseY);
	}

	private void renderDirectionPage(GuiGraphics g, int x, int y, int mouseX, int mouseY) {
		int alpha = (int) (screen.subAlpha() * 255);
		int slide = screen.subSlide();
		int labelColor = (alpha << 24) | 0xC8C8C8;
		int valueColor = (alpha << 24) | 0xFFFFFF;

		screen.direction().render(g, screen.font(), x, y, alpha);
		g.drawString(screen.font(), Component.translatable("screen.magic-firework.direction.horizontal"),
				x + EditorLayout.ROW_LABEL_X + slide, y + EditorLayout.ROW1_Y, labelColor);
		g.drawString(screen.font(), EditorText.degreesText(screen.menu().displayedYaw()),
				x + EditorLayout.ROW_VALUE_X + slide, y + EditorLayout.ROW1_Y, valueColor);
		boolean interactive = screen.subInteractive();
		boolean yawDown = EditorSteps.YAW.canDecrease(screen.menu());
		boolean yawUp = EditorSteps.YAW.canIncrease(screen.menu(), screen.player());
		drawStep(g, x + EditorLayout.STEP_MINUS_X + slide, y + EditorLayout.ROW1_Y - 4, "-", yawDown,
				interactive, mouseX, mouseY, alpha);
		drawStep(g, x + EditorLayout.STEP_PLUS_X + slide, y + EditorLayout.ROW1_Y - 4, "+", yawUp,
				interactive, mouseX, mouseY, alpha);

		g.drawString(screen.font(), Component.translatable("screen.magic-firework.direction.vertical"),
				x + EditorLayout.ROW_LABEL_X + slide, y + EditorLayout.ROW2_Y, labelColor);
		g.drawString(screen.font(), EditorText.degreesText(screen.menu().displayedPitch()),
				x + EditorLayout.ROW_VALUE_X + slide, y + EditorLayout.ROW2_Y, valueColor);
		boolean pitchDown = EditorSteps.PITCH.canDecrease(screen.menu());
		boolean pitchUp = EditorSteps.PITCH.canIncrease(screen.menu(), screen.player());
		drawStep(g, x + EditorLayout.STEP_MINUS_X + slide, y + EditorLayout.ROW2_Y - 4, "-", pitchDown,
				interactive, mouseX, mouseY, alpha);
		drawStep(g, x + EditorLayout.STEP_PLUS_X + slide, y + EditorLayout.ROW2_Y - 4, "+", pitchUp,
				interactive, mouseX, mouseY, alpha);

		drawBackButton(g, x, y, alpha, interactive, mouseX, mouseY);
	}

	private void drawStep(GuiGraphics g, int bx, int by, String label, boolean enabled, boolean interactive,
			int mouseX, int mouseY, int alpha) {
		boolean hover = interactive && enabled && screen.over(bx, by,
				EditorLayout.STEP_BTN_W, EditorLayout.STEP_BTN_H, mouseX, mouseY);
		EditorChrome.drawStepButton(g, screen.font(), bx, by, label, enabled, hover, alpha);
	}

	private void drawBackButton(GuiGraphics g, int x, int y, int alpha, boolean interactive, int mouseX,
			int mouseY) {
		boolean hover = interactive && screen.over(EditorLayout.BACK_X, EditorLayout.BACK_Y,
				EditorLayout.BACK_W, EditorLayout.BACK_H, mouseX, mouseY);
		EditorChrome.drawTextButton(g, screen.font(), x + EditorLayout.BACK_X, y + EditorLayout.BACK_Y,
				EditorLayout.BACK_W, EditorLayout.BACK_H,
				Component.translatable("screen.magic-firework.editor.back"), hover, alpha);
	}
}
