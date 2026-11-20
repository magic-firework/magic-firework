package com.magic.firework.recipe;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.magic.firework.craft.BlueprintCrafting;
import com.magic.firework.craft.Cost;
import com.magic.firework.item.BlueprintDataHelper;
import com.magic.firework.item.FireworkDataHelper;
import com.magic.firework.registry.ModRegistries;
import com.magic.firework.rocket.RocketSpec;

import net.minecraft.core.NonNullList;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;

/**
 * Written blueprint + its frozen bill -> the firework the blueprint describes. Only a written blueprint
 * matches; the bill is read from its NBT, so a data-pack change never changes what an existing blueprint
 * costs. Extra items are rejected, which keeps the grid a precise payment rather than a suggestion.
 */
public final class BlueprintCraftingRecipe extends CustomRecipe {
	public BlueprintCraftingRecipe(ResourceLocation id, CraftingBookCategory category) {
		super(id, category);
	}

	@Override
	public boolean matches(CraftingContainer grid, Level level) {
		int blueprintSlot = -1;
		int blueprints = 0;
		int written = 0;
		Map<String, Integer> provided = new LinkedHashMap<>();
		for (int i = 0; i < grid.getContainerSize(); i++) {
			ItemStack stack = grid.getItem(i);
			if (stack.isEmpty()) {
				continue;
			}
			if (stack.is(ModRegistries.FIREWORK_BLUEPRINT_ITEM)) {
				blueprints++;
				if (BlueprintDataHelper.isWritten(stack)) {
					written++;
					blueprintSlot = i;
				}
			} else {
				ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
				provided.merge(id.toString(), stack.getCount(), Integer::sum);
			}
		}
		if (blueprints != 1 || written != 1 || blueprintSlot < 0) {
			return false;
		}
		List<Cost> bill = BlueprintDataHelper.cost(grid.getItem(blueprintSlot));
		return BlueprintCrafting.matchesBill(bill, blueprints, written, provided);
	}

	@Override
	public ItemStack assemble(CraftingContainer grid, RegistryAccess access) {
		for (int i = 0; i < grid.getContainerSize(); i++) {
			ItemStack stack = grid.getItem(i);
			if (stack.is(ModRegistries.FIREWORK_BLUEPRINT_ITEM) && BlueprintDataHelper.isWritten(stack)) {
				RocketSpec spec = FireworkDataHelper.spec(BlueprintDataHelper.source(stack));
				return FireworkDataHelper.create(ModRegistries.MAGIC_FIREWORK_ITEM, spec);
			}
		}
		return ItemStack.EMPTY;
	}

	@Override
	public NonNullList<ItemStack> getRemainingItems(CraftingContainer grid) {
		return NonNullList.withSize(grid.getContainerSize(), ItemStack.EMPTY);
	}

	@Override
	public boolean canCraftInDimensions(int width, int height) {
		return width * height >= 2;
	}

	@Override
	public RecipeSerializer<?> getSerializer() {
		return ModRecipes.BLUEPRINT_CRAFTING;
	}
}
