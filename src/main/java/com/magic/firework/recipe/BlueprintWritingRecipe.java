package com.magic.firework.recipe;

import java.util.List;

import com.magic.firework.content.FireworkProfile;
import com.magic.firework.craft.Cost;
import com.magic.firework.craft.FormulaCost;
import com.magic.firework.item.BlueprintDataHelper;
import com.magic.firework.item.FireworkDataHelper;
import com.magic.firework.platform.FireworkRuntime;
import com.magic.firework.platform.RuntimeRegistry;
import com.magic.firework.registry.ModRegistries;

import net.minecraft.core.NonNullList;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;

/**
 * Blank blueprint + one magic firework = the same blueprint, written.
 *
 * <p>The bill is computed here, once, from the firework's resolved attributes and then
 * frozen into the blueprint NBT. The firework is a sample: it is returned by
 * {@link #getRemainingItems} so a carefully assembled rocket is not lost.
 *
 * <p>Only a <em>blank</em> blueprint matches, which is what makes a written one immutable.
 */
public final class BlueprintWritingRecipe extends CustomRecipe {
	public BlueprintWritingRecipe(ResourceLocation id, CraftingBookCategory category) {
		super(id, category);
	}

	@Override
	public boolean matches(CraftingContainer grid, Level level) {
		boolean blueprint = false;
		boolean firework = false;
		for (int i = 0; i < grid.getContainerSize(); i++) {
			ItemStack stack = grid.getItem(i);
			if (stack.isEmpty()) {
				continue;
			}
			if (stack.is(ModRegistries.FIREWORK_BLUEPRINT_ITEM)) {
				if (blueprint || BlueprintDataHelper.isWritten(stack)) {
					return false;
				}
				blueprint = true;
			} else if (stack.is(ModRegistries.MAGIC_FIREWORK_ITEM)) {
				if (firework) {
					return false;
				}
				firework = true;
			} else {
				return false;
			}
		}
		return blueprint && firework;
	}

	@Override
	public ItemStack assemble(CraftingContainer grid, RegistryAccess access) {
		ItemStack sample = find(grid, ModRegistries.MAGIC_FIREWORK_ITEM);
		if (sample == null) {
			return ItemStack.EMPTY;
		}
		CompoundTag source = FireworkDataHelper.source(sample);
		ResourceLocation profileId = FireworkDataHelper.profileId(source);
		FireworkRuntime runtime = RuntimeRegistry.current();
		FireworkProfile profile = runtime == null ? null : runtime.definitions().get(profileId);
		if (runtime != null && profile == null) {
			// A data pack that does not install the profile cannot produce a launchable item.
			return ItemStack.EMPTY;
		}
		List<Cost> cost = FormulaCost.of(profile, FireworkDataHelper.overlay(source));
		ItemStack written = new ItemStack(ModRegistries.FIREWORK_BLUEPRINT_ITEM);
		BlueprintDataHelper.write(written, source, cost);
		return written;
	}

	@Override
	public NonNullList<ItemStack> getRemainingItems(CraftingContainer grid) {
		NonNullList<ItemStack> remaining = NonNullList.withSize(grid.getContainerSize(), ItemStack.EMPTY);
		for (int i = 0; i < grid.getContainerSize(); i++) {
			if (grid.getItem(i).is(ModRegistries.MAGIC_FIREWORK_ITEM)) {
				// A one-item remainder is added back to whatever is left in the slot, so the
				// sample survives regardless of how big the stack was.
				remaining.set(i, grid.getItem(i).copyWithCount(1));
				break;
			}
		}
		return remaining;
	}

	@Override
	public boolean canCraftInDimensions(int width, int height) {
		return width * height >= 2;
	}

	@Override
	public RecipeSerializer<?> getSerializer() {
		return ModRecipes.BLUEPRINT_WRITING;
	}

	private static ItemStack find(CraftingContainer grid, Item item) {
		for (int i = 0; i < grid.getContainerSize(); i++) {
			ItemStack stack = grid.getItem(i);
			if (!stack.isEmpty() && stack.is(item)) {
				return stack;
			}
		}
		return null;
	}
}
