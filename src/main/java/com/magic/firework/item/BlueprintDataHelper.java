package com.magic.firework.item;

import java.util.List;

import com.magic.firework.craft.BlueprintData;
import com.magic.firework.craft.Cost;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

/** {@link ItemStack}-level access to the {@code MagicFireworkBlueprint} tag. */
public final class BlueprintDataHelper {
	/** {@code CustomModelData} value the item model uses to pick the written texture. */
	public static final int WRITTEN_MODEL_DATA = 1;

	private BlueprintDataHelper() {
	}

	public static boolean isWritten(ItemStack stack) {
		return BlueprintData.isWritten(root(stack));
	}

	/** @return the stored firework tag (a copy), empty when the blueprint is blank */
	public static CompoundTag source(ItemStack stack) {
		return BlueprintData.source(root(stack));
	}

	/** @return the bill frozen into the blueprint, empty when it is blank or malformed */
	public static List<Cost> cost(ItemStack stack) {
		return BlueprintData.cost(root(stack));
	}

	/** Turn a blank blueprint into a written one, freezing the bill into its NBT. */
	public static void write(ItemStack stack, CompoundTag source, List<Cost> cost) {
		CompoundTag tag = stack.getOrCreateTag();
		tag.put(BlueprintData.ROOT, BlueprintData.written(source, cost));
		tag.putInt("CustomModelData", WRITTEN_MODEL_DATA);
	}

	private static CompoundTag root(ItemStack stack) {
		CompoundTag tag = stack.getTag();
		return tag != null && tag.get(BlueprintData.ROOT) instanceof CompoundTag root ? root : new CompoundTag();
	}
}
