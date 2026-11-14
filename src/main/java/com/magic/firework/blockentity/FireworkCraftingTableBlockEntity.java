package com.magic.firework.blockentity;

import com.magic.firework.attribute.AttributeBag;
import com.magic.firework.attribute.Attributes;
import com.magic.firework.item.FireworkDataHelper;
import com.magic.firework.registry.Definitions;
import com.magic.firework.registry.ModRegistries;
import com.magic.firework.screen.FireworkEditorMenu;

import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The block entity behind the firework crafting table. Slot 0 holds the firework being
 * edited; the flight page adjusts its values by consuming / refunding materials from the
 * player's own inventory.
 */
public class FireworkCraftingTableBlockEntity extends BlockEntity implements Container, MenuProvider {
	public static final int SLOT_FIREWORK = 0;
	/** Legacy material slots from the earlier slot-based flight page; contents are handed back. */
	private static final int LEGACY_SLOT_SPEED = 1;
	private static final int LEGACY_SLOT_FUSE = 2;

	/** One gunpowder raises {@link Attributes#SPEED} by this much. */
	public static final Item SPEED_MATERIAL = Items.GUNPOWDER;
	public static final float SPEED_PER_ITEM = 0.1F;
	/** One string raises {@link Attributes#FUSE_TICKS} (flight time) by this many ticks. */
	public static final Item FUSE_MATERIAL = Items.STRING;
	public static final int FUSE_PER_ITEM = 20;

	private NonNullList<ItemStack> items = NonNullList.withSize(3, ItemStack.EMPTY);

	public FireworkCraftingTableBlockEntity(BlockPos pos, BlockState state) {
		super(ModRegistries.FIREWORK_CRAFTING_TABLE_BLOCK_ENTITY, pos, state);
	}

	// ── persistence ───────────────────────────────────────────────────────────

	@Override
	protected void saveAdditional(CompoundTag tag) {
		super.saveAdditional(tag);
		ContainerHelper.saveAllItems(tag, this.items);
	}

	@Override
	public void load(CompoundTag tag) {
		super.load(tag);
		this.items = NonNullList.withSize(this.getContainerSize(), ItemStack.EMPTY);
		ContainerHelper.loadAllItems(tag, this.items);
	}

	// ── menu ──────────────────────────────────────────────────────────────────

	@Override
	public Component getDisplayName() {
		return Component.translatable("block.magic-firework.firework_crafting_table");
	}

	@Override
	public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
		return new FireworkEditorMenu(containerId, playerInventory, this);
	}

	// ── flight values ─────────────────────────────────────────────────────────

	public float effectiveSpeed(Definitions definitions) {
		FireworkDataHelper.Resolved resolved = resolve(definitions);
		return resolved == null ? 0.0F : Attributes.SPEED.get(effective(resolved));
	}

	public int effectiveFuse(Definitions definitions) {
		FireworkDataHelper.Resolved resolved = resolve(definitions);
		return resolved == null ? 0 : Attributes.FUSE_TICKS.get(effective(resolved));
	}

	/**
	 * Moves one step along the speed or flight-time scale. Both may reach zero but never go
	 * negative.
	 *
	 * <p>Materials buy the steps *above* the profile's own value; below that they are free, so
	 * lowering can never mint materials that were never paid for.
	 *
	 * @param which {@link FireworkEditorMenu#ADJUST_SPEED} or {@link FireworkEditorMenu#ADJUST_FUSE}
	 * @param delta {@code +1} to raise (consumes a material) or {@code -1} to lower (refunds one)
	 */
	public void adjustFlight(Definitions definitions, Player player, int which, int delta) {
		if (delta != 1 && delta != -1) {
			return;
		}
		FireworkDataHelper.Resolved resolved = resolve(definitions);
		if (resolved == null) {
			return;
		}
		ItemStack firework = getItem(SLOT_FIREWORK);
		AttributeBag effective = effective(resolved);

		if (which == FireworkEditorMenu.ADJUST_SPEED) {
			double raw = Attributes.SPEED.get(effective) / (double) SPEED_PER_ITEM;
			int maxSteps = Math.max(0, Math.round(Attributes.SPEED.max() / SPEED_PER_ITEM));
			int freeSteps = Math.max(0, Math.round(profileBaseSpeed(resolved) / SPEED_PER_ITEM));
			int nearest = nearestStep(raw);
			int next = stepped(raw, delta, maxSteps);
			if (onGrid(raw) && next == nearest) {
				return;
			}
			if (!pay(player, SPEED_MATERIAL, paid(freeSteps, next) - paid(freeSteps, nearest))) {
				return;
			}
			float value = (float) (next * (double) SPEED_PER_ITEM);
			FireworkDataHelper.setOverlay(firework,
					resolved.overlay().withFloat(Attributes.SPEED.key(), Attributes.SPEED.clamp(value)));
			setItem(SLOT_FIREWORK, firework);
		} else if (which == FireworkEditorMenu.ADJUST_FUSE) {
			double raw = Attributes.FUSE_TICKS.get(effective) / (double) FUSE_PER_ITEM;
			int maxSteps = Math.max(0, Attributes.FUSE_TICKS.max() / FUSE_PER_ITEM);
			int freeSteps = Math.max(0, (int) Math.round(profileBaseFuse(resolved) / (double) FUSE_PER_ITEM));
			int nearest = nearestStep(raw);
			int next = stepped(raw, delta, maxSteps);
			if (onGrid(raw) && next == nearest) {
				return;
			}
			if (!pay(player, FUSE_MATERIAL, paid(freeSteps, next) - paid(freeSteps, nearest))) {
				return;
			}
			int value = next * FUSE_PER_ITEM;
			FireworkDataHelper.setOverlay(firework,
					resolved.overlay().withInt(Attributes.FUSE_TICKS.key(), Attributes.FUSE_TICKS.clamp(value)));
			setItem(SLOT_FIREWORK, firework);
		}
	}

	/** True when the value already sits exactly on the material step grid. */
	private static boolean onGrid(double raw) {
		return Math.abs(raw - Math.round(raw)) < 1.0E-4;
	}

	private static int nearestStep(double raw) {
		return Math.max(0, (int) Math.round(raw));
	}

	/**
	 * The step index a press moves to. A value off the grid (a profile's 45-tick fuse, say)
	 * first snaps to the grid in the pressed direction instead of jumping a whole step.
	 */
	private static int stepped(double raw, int delta, int maxSteps) {
		int target;
		if (onGrid(raw)) {
			target = (int) Math.round(raw) + delta;
		} else {
			target = delta > 0 ? (int) Math.ceil(raw) : (int) Math.floor(raw);
		}
		return Math.max(0, Math.min(maxSteps, target));
	}

	/** Steps above the free allowance, i.e. how many materials the current value has cost. */
	private static int paid(int freeSteps, int steps) {
		return Math.max(0, steps - freeSteps);
	}

	/**
	 * Applies a material difference: a positive amount must be available and is taken, a
	 * negative amount is refunded.
	 *
	 * @return false when the rise cannot be paid for
	 */
	private static boolean pay(Player player, Item item, int difference) {
		if (difference > 0) {
			if (!hasMaterial(player, item)) {
				return false;
			}
			takeMaterial(player, item);
		} else if (difference < 0) {
			giveMaterial(player, item);
		}
		return true;
	}

	/** Hands back anything left in the old material slots, so it is not lost invisibly. */
	public void returnLegacyMaterials(Player player) {
		for (int slot = LEGACY_SLOT_SPEED; slot <= LEGACY_SLOT_FUSE; slot++) {
			ItemStack stack = getItem(slot);
			if (!stack.isEmpty()) {
				setItem(slot, ItemStack.EMPTY);
				if (!player.getInventory().add(stack)) {
					player.drop(stack, false);
				}
			}
		}
	}

	private FireworkDataHelper.Resolved resolve(Definitions definitions) {
		ItemStack firework = getItem(SLOT_FIREWORK);
		if (firework.isEmpty()) {
			return null;
		}
		return FireworkDataHelper.resolve(firework, definitions);
	}

	private static AttributeBag effective(FireworkDataHelper.Resolved resolved) {
		return resolved.profile() == null
				? resolved.overlay()
				: resolved.profile().defaults().overlay(resolved.overlay());
	}

	private static float profileBaseSpeed(FireworkDataHelper.Resolved resolved) {
		return resolved.profile() == null
				? Attributes.SPEED.def()
				: Attributes.SPEED.get(resolved.profile().defaults());
	}

	private static int profileBaseFuse(FireworkDataHelper.Resolved resolved) {
		return resolved.profile() == null
				? Attributes.FUSE_TICKS.def()
				: Attributes.FUSE_TICKS.get(resolved.profile().defaults());
	}

	/** Creative players do not spend materials. */
	private static boolean hasMaterial(Player player, Item item) {
		return player.getAbilities().instabuild || countMaterial(player, item) > 0;
	}

	private static int countMaterial(Player player, Item item) {
		int count = 0;
		Inventory inventory = player.getInventory();
		for (int i = 0; i < inventory.getContainerSize(); i++) {
			ItemStack stack = inventory.getItem(i);
			if (stack.is(item)) {
				count += stack.getCount();
			}
		}
		return count;
	}

	private static void takeMaterial(Player player, Item item) {
		if (player.getAbilities().instabuild) {
			return;
		}
		Inventory inventory = player.getInventory();
		for (int i = 0; i < inventory.getContainerSize(); i++) {
			ItemStack stack = inventory.getItem(i);
			if (stack.is(item)) {
				stack.shrink(1);
				if (stack.isEmpty()) {
					inventory.setItem(i, ItemStack.EMPTY);
				}
				inventory.setChanged();
				return;
			}
		}
	}

	private static void giveMaterial(Player player, Item item) {
		ItemStack stack = new ItemStack(item);
		if (!player.getInventory().add(stack)) {
			player.drop(stack, false);
		}
	}

	// ── Container ─────────────────────────────────────────────────────────────

	@Override
	public int getContainerSize() {
		return this.items.size();
	}

	@Override
	public boolean isEmpty() {
		for (ItemStack stack : this.items) {
			if (!stack.isEmpty()) {
				return false;
			}
		}
		return true;
	}

	@Override
	public ItemStack getItem(int slot) {
		return this.items.get(slot);
	}

	@Override
	public ItemStack removeItem(int slot, int amount) {
		ItemStack removed = ContainerHelper.removeItem(this.items, slot, amount);
		if (!removed.isEmpty()) {
			this.setChanged();
		}
		return removed;
	}

	@Override
	public ItemStack removeItemNoUpdate(int slot) {
		ItemStack removed = ContainerHelper.takeItem(this.items, slot);
		if (!removed.isEmpty()) {
			this.setChanged();
		}
		return removed;
	}

	@Override
	public void setItem(int slot, ItemStack stack) {
		this.items.set(slot, stack);
		if (stack.getCount() > stack.getMaxStackSize()) {
			stack.setCount(stack.getMaxStackSize());
		}
		this.setChanged();
	}

	@Override
	public boolean stillValid(Player player) {
		return Container.stillValidBlockEntity(this, player);
	}

	@Override
	public boolean canPlaceItem(int slot, ItemStack stack) {
		return slot == SLOT_FIREWORK && stack.is(ModRegistries.MAGIC_FIREWORK_ITEM);
	}

	@Override
	public void clearContent() {
		for (int i = 0; i < this.items.size(); i++) {
			this.items.set(i, ItemStack.EMPTY);
		}
		this.setChanged();
	}

	@Override
	public void setChanged() {
		super.setChanged();
	}
}
