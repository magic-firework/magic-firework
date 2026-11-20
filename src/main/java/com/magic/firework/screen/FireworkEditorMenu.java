package com.magic.firework.screen;

import com.magic.firework.blockentity.FireworkCraftingTableBlockEntity;
import com.magic.firework.platform.FireworkRuntime;
import com.magic.firework.platform.RuntimeRegistry;
import com.magic.firework.registry.Definitions;
import com.magic.firework.registry.ModRegistries;

import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * The firework editor container, bound to the firework crafting table. One menu backs both pages: the main
 * page shows the category grid, the flight page the speed and flight-time steppers; keeping a single menu
 * means switching pages never reopens a screen, so the cursor never jumps. The container is the block
 * entity, so the firework stays in the block when the menu closes, like a furnace, instead of dropping.
 */
public class FireworkEditorMenu extends AbstractContainerMenu {
	/** Which value the flight page's steppers adjust. */
	public static final int ADJUST_SPEED = 0;
	public static final int ADJUST_FLIGHT_TIME = 1;
	/** Which dial the direction page's steppers turn. */
	public static final int ADJUST_YAW = 0;
	public static final int ADJUST_PITCH = 1;

	public static final int CENTER_SLOT = 0;
	private static final int INV_START = 1;
	private static final int INV_END = 27; // inclusive
	private static final int HOTBAR_START = 28;
	private static final int HOTBAR_END = 36; // inclusive

	/** Centre slot position, relative to the screen's leftPos/topPos. */
	public static final int CENTER_X = 43;
	public static final int CENTER_Y = 107;

	// Synced display values: speed (milli-units), flight time (ticks), yaw / pitch (milli-degrees).
	private static final int DATA_SPEED = 0;
	private static final int DATA_FLIGHT = 1;
	private static final int DATA_YAW = 2;
	private static final int DATA_PITCH = 3;
	private static final int DATA_COUNT = 4;

	private final Container center;
	private final SimpleContainerData editorData = new SimpleContainerData(DATA_COUNT);

	public FireworkEditorMenu(int containerId, Inventory playerInventory) {
		this(containerId, playerInventory, new SimpleContainer(3));
	}

	public FireworkEditorMenu(int containerId, Inventory playerInventory, Container center) {
		super(ModRegistries.FIREWORK_EDITOR_MENU, containerId);
		this.center = center;
		this.addDataSlots(this.editorData);

		this.addSlot(new Slot(center, CENTER_SLOT, CENTER_X, CENTER_Y) {
			@Override
			public boolean mayPlace(ItemStack stack) {
				return stack.is(ModRegistries.MAGIC_FIREWORK_ITEM);
			}
		});

		for (int row = 0; row < 3; row++) {
			for (int col = 0; col < 9; col++) {
				this.addSlot(new Slot(playerInventory, col + row * 9 + 9, 109 + col * 18, 182 + row * 18));
			}
		}
		for (int col = 0; col < 9; col++) {
			this.addSlot(new Slot(playerInventory, col, 109 + col * 18, 236));
		}
	}

	/** @return the firework currently in the centre slot (may be empty) */
	public ItemStack getCenterStack() {
		return center.getItem(CENTER_SLOT);
	}

	/** @return the container backing the editor slots (the crafting table's block entity) */
	public Container center() {
		return center;
	}


	@Override
	public void broadcastChanges() {
		if (this.center instanceof FireworkCraftingTableBlockEntity table) {
			FireworkRuntime runtime = RuntimeRegistry.of(
					table.getLevel() == null ? null : table.getLevel().getServer());
			if (runtime != null) {
				Definitions definitions = runtime.definitions();
				this.editorData.set(DATA_SPEED, Math.round(table.effectiveSpeed(definitions) * 1000.0F));
				this.editorData.set(DATA_FLIGHT, table.effectiveFlight(definitions));
				this.editorData.set(DATA_YAW, Math.round(table.effectiveYaw(definitions) * 1000.0F));
				this.editorData.set(DATA_PITCH, Math.round(table.effectivePitch(definitions) * 1000.0F));
			}
		}
		super.broadcastChanges();
	}

	public float displayedSpeed() {
		return this.editorData.get(DATA_SPEED) / 1000.0F;
	}

	public int displayedFlight() {
		return this.editorData.get(DATA_FLIGHT);
	}

	public float displayedYaw() {
		return this.editorData.get(DATA_YAW) / 1000.0F;
	}

	public float displayedPitch() {
		return this.editorData.get(DATA_PITCH) / 1000.0F;
	}


	/**
	 * Shift-click routing. Each target range must be disjoint from the source slot: {@code moveItemStackTo}
	 * merges into any slot holding the same item and tags, including the source slot itself, which would
	 * double the stack. So the main inventory and the hotbar are routed into each other, never into themselves.
	 */
	@Override
	public ItemStack quickMoveStack(Player player, int index) {
		ItemStack result = ItemStack.EMPTY;
		Slot slot = this.slots.get(index);
		if (slot != null && slot.hasItem()) {
			ItemStack stack = slot.getItem();
			result = stack.copy();
			if (index == CENTER_SLOT) {
				if (!this.moveItemStackTo(stack, INV_START, HOTBAR_END + 1, true)) {
					return ItemStack.EMPTY;
				}
			} else if (index >= INV_START && index <= INV_END) {
				// Main inventory: try the firework slot, otherwise the hotbar.
				if (stack.is(ModRegistries.MAGIC_FIREWORK_ITEM)) {
					if (!this.moveItemStackTo(stack, CENTER_SLOT, CENTER_SLOT + 1, false)
							&& !this.moveItemStackTo(stack, HOTBAR_START, HOTBAR_END + 1, false)) {
						return ItemStack.EMPTY;
					}
				} else if (!this.moveItemStackTo(stack, HOTBAR_START, HOTBAR_END + 1, false)) {
					return ItemStack.EMPTY;
				}
			} else if (index >= HOTBAR_START && index <= HOTBAR_END) {
				// Hotbar: try the firework slot, otherwise the main inventory.
				if (stack.is(ModRegistries.MAGIC_FIREWORK_ITEM)) {
					if (!this.moveItemStackTo(stack, CENTER_SLOT, CENTER_SLOT + 1, false)
							&& !this.moveItemStackTo(stack, INV_START, INV_END + 1, false)) {
						return ItemStack.EMPTY;
					}
				} else if (!this.moveItemStackTo(stack, INV_START, INV_END + 1, false)) {
					return ItemStack.EMPTY;
				}
			}
			if (stack.isEmpty()) {
				slot.setByPlayer(ItemStack.EMPTY);
			} else {
				slot.setChanged();
			}
			if (stack.getCount() == result.getCount()) {
				return ItemStack.EMPTY;
			}
			slot.onTake(player, stack);
		}
		return result;
	}

	@Override
	public boolean stillValid(Player player) {
		return this.center instanceof BlockEntity blockEntity
				? Container.stillValidBlockEntity(blockEntity, player)
				: true;
	}
}
