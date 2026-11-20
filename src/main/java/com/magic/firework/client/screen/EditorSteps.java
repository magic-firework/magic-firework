package com.magic.firework.client.screen;

import com.magic.firework.attribute.Attributes;
import com.magic.firework.screen.FireworkEditorMenu;
import com.magic.firework.blockentity.FireworkCraftingTableBlockEntity;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;

/** The four stepper rows and their bounds, replacing the pair of can* methods per row. */
@SuppressWarnings("ImmutableEnumChecker") // Item is a registry singleton; the enum is immutable in practice
enum EditorSteps {
	SPEED(0, FireworkEditorMenu.ADJUST_SPEED, 1.0E-4F, Attributes.SPEED.max() - 1.0E-4F,
			FireworkCraftingTableBlockEntity.SPEED_MATERIAL), FLIGHT(0, FireworkEditorMenu.ADJUST_FLIGHT_TIME, 0.0F,
					Attributes.FLIGHT_TICKS.max(),
					FireworkCraftingTableBlockEntity.FLIGHT_MATERIAL), YAW(1, FireworkEditorMenu.ADJUST_YAW, 0.01F,
							359.99F, null), PITCH(1, FireworkEditorMenu.ADJUST_PITCH, 0.01F, 359.99F, null);

	static final int CHANNEL_FLIGHT = 0;
	static final int CHANNEL_DIRECTION = 1;
	static final int DIRECTION_STEP = FireworkCraftingTableBlockEntity.DIRECTION_STEP_DEGREES;

	private final int channel;
	private final int which;
	private final float min;
	private final float max;
	private final Item material;

	EditorSteps(int channel, int which, float min, float max, Item material) {
		this.channel = channel;
		this.which = which;
		this.min = min;
		this.max = max;
		this.material = material;
	}

	int channel() {
		return channel;
	}

	int which() {
		return which;
	}

	int rowY() {
		return which == FireworkEditorMenu.ADJUST_FLIGHT_TIME || which == FireworkEditorMenu.ADJUST_PITCH
				? EditorLayout.ROW2_Y
				: EditorLayout.ROW1_Y;
	}

	int amount() {
		return channel == CHANNEL_DIRECTION ? DIRECTION_STEP : 1;
	}

	boolean canDecrease(FireworkEditorMenu menu) {
		return value(menu) > min;
	}

	boolean canIncrease(FireworkEditorMenu menu, Player player) {
		return value(menu) < max && (material == null || hasMaterial(player, material));
	}

	static EditorSteps of(int channel, int which) {
		for (EditorSteps step : values()) {
			if (step.channel == channel && step.which == which) {
				return step;
			}
		}
		return null;
	}

	private float value(FireworkEditorMenu menu) {
		return switch (this) {
			case SPEED -> menu.displayedSpeed();
			case FLIGHT -> menu.displayedFlight();
			case YAW -> menu.displayedYaw();
			case PITCH -> menu.displayedPitch();
		};
	}

	/** Creative players can raise a value without holding the material. */
	private static boolean hasMaterial(Player player, Item item) {
		if (player == null) {
			return false;
		}
		if (player.getAbilities().instabuild) {
			return true;
		}
		Inventory inventory = player.getInventory();
		for (int i = 0; i < inventory.getContainerSize(); i++) {
			if (inventory.getItem(i).is(item)) {
				return true;
			}
		}
		return false;
	}
}
