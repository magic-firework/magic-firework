package com.magic.firework.client.screen;

import java.util.List;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Fixed geometry and the category grid of the editor screen. */
final class EditorLayout {
	static final int IMAGE_W = 380;
	static final int IMAGE_H = 260;

	private static final int[] GRID_COL_X = {180, 214, 248, 282};
	private static final int[] GRID_ROW_Y = {74, 122};

	static final int SLOT_SIZE = 22;

	static final int ATTR_X = 40;
	static final int ATTR_COL_GAP = 192;
	static final int ATTR_ROW_GAP = 10;

	static final int ROW_LABEL_X = 176;
	static final int ROW_VALUE_X = 252;
	static final int STEP_MINUS_X = 292;
	static final int STEP_PLUS_X = 316;
	static final int STEP_BTN_W = 18;
	static final int STEP_BTN_H = 16;
	static final int ROW1_Y = 78;
	static final int ROW2_Y = 126;

	static final int BACK_X = 42;
	static final int BACK_Y = 150;
	static final int BACK_W = 44;
	static final int BACK_H = 20;

	static final String FLIGHT_LABEL = "screen.magic-firework.editor.slot.flight";
	static final String DIRECTION_LABEL = "screen.magic-firework.editor.slot.direction";

	static final List<Category> CATEGORIES = List.of(
			new Category("screen.magic-firework.editor.slot.shape", new ItemStack(Items.FIREWORK_STAR)),
			new Category("screen.magic-firework.editor.slot.color", new ItemStack(Items.RED_DYE)),
			new Category("screen.magic-firework.editor.slot.effect", new ItemStack(Items.FIREWORK_ROCKET)),
			new Category(FLIGHT_LABEL, new ItemStack(Items.FEATHER)),
			new Category(DIRECTION_LABEL, new ItemStack(Items.COMPASS)),
			new Category("screen.magic-firework.editor.slot.sound", new ItemStack(Items.NOTE_BLOCK)),
			new Category("screen.magic-firework.editor.slot.light", new ItemStack(Items.GLOWSTONE)),
			new Category("screen.magic-firework.editor.slot.trail", new ItemStack(Items.GUNPOWDER)));

	private EditorLayout() {
	}

	static int categoryX(int index) {
		return GRID_COL_X[index % GRID_COL_X.length];
	}

	static int categoryY(int index) {
		return GRID_ROW_Y[index / GRID_COL_X.length];
	}

	record Category(String labelKey, ItemStack icon) {
	}
}
