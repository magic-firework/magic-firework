package com.magic.firework.recipe;

import java.util.HashMap;
import java.util.Map;

import com.magic.firework.craft.FormulaMatch;
import com.magic.firework.item.BlueprintDataHelper;
import com.magic.firework.item.FireworkDataHelper;
import com.magic.firework.registry.ModRegistries;

import net.minecraft.core.NonNullList;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;

/**
 * A written blueprint plus its frozen bill = the firework it describes.
 *
 * <p>The bill is read from the blueprint NBT, never recomputed, so a data pack edit cannot
 * change the price of a sheet that is already written. The blueprint is a mold: it is
 * returned by {@link #getRemainingItems} and only the materials are consumed.
 */
public final class BlueprintCraftingRecipe extends CustomRecipe {
	public BlueprintCraftingRecipe(ResourceLocation id, CraftingBookCategory category) {
		super(id, category);
	}

	@Override
	public boolean matches(CraftingContainer grid, Level level) {
		ItemStack blueprint = null;
		Map<String, Integer> have = new HashMap<>();
		for (int i = 0; i < grid.getContainerSize(); i++) {
			ItemStack stack = grid.getItem(i);
			if (stack.isEmpty()) {
				continue;
			}
			if (stack.is(ModRegistries.FIREWORK_BLUEPRINT_ITEM)) {
				if (blueprint != null || !BlueprintDataHelper.isWritten(stack)) {
					return false;
				}
				blueprint = stack;
			} else {
				ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
				have.merge(id.toString(), stack.getCount(), Integer::sum);
			}
		}
		if (blueprint == null) {
			return false;
		}
		if (BlueprintDataHelper.source(blueprint).getString(FireworkDataHelper.PROFILE).isEmpty()) {
			return false;
		}
		return FormulaMatch.matches(BlueprintDataHelper.cost(blueprint), have);
	}

	@Override
	public ItemStack assemble(CraftingContainer grid, RegistryAccess access) {
		ItemStack blueprint = findWrittenBlueprint(grid);
		if (blueprint == null) {
			return ItemStack.EMPTY;
		}
		CompoundTag source = BlueprintDataHelper.source(blueprint);
		return FireworkDataHelper.create(ModRegistries.MAGIC_FIREWORK_ITEM,
				FireworkDataHelper.profileId(source), FireworkDataHelper.overlay(source));
	}

	@Override
	public NonNullList<ItemStack> getRemainingItems(CraftingContainer grid) {
		NonNullList<ItemStack> remaining = NonNullList.withSize(grid.getContainerSize(), ItemStack.EMPTY);
		for (int i = 0; i < grid.getContainerSize(); i++) {
			if (grid.getItem(i).is(ModRegistries.FIREWORK_BLUEPRINT_ITEM)) {
				remaining.set(i, grid.getItem(i).copyWithCount(1));
				break;
			}
		}
		return remaining;
	}

	@Override
	public boolean canCraftInDimensions(int width, int height) {
		return width * height >= 4;
	}

	@Override
	public RecipeSerializer<?> getSerializer() {
		return ModRecipes.BLUEPRINT_CRAFTING;
	}

	private static ItemStack findWrittenBlueprint(CraftingContainer grid) {
		for (int i = 0; i < grid.getContainerSize(); i++) {
			ItemStack stack = grid.getItem(i);
			if (stack.is(ModRegistries.FIREWORK_BLUEPRINT_ITEM) && BlueprintDataHelper.isWritten(stack)) {
				return stack;
			}
		}
		return null;
	}
}
