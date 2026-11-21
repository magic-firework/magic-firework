package com.magic.firework.recipe;

import com.magic.firework.MagicFirework;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.SimpleCraftingRecipeSerializer;

/**
 * Recipe serializers for the blueprint: writing freezes the bill, and the reverse craft pays
 * that bill back to turn the written sheet into its firework. Both are dynamic recipes, so a
 * single JSON of their type is enough; they use the vanilla {@code crafting} type so
 * {@code RecipeManager.getRemainingItemsFor} can find them when the result is taken.
 */
public final class ModRecipes {
	public static final RecipeSerializer<BlueprintWritingRecipe> BLUEPRINT_WRITING = new SimpleCraftingRecipeSerializer<>(
			BlueprintWritingRecipe::new);
	public static final RecipeSerializer<BlueprintCraftingRecipe> BLUEPRINT_CRAFTING = new SimpleCraftingRecipeSerializer<>(
			BlueprintCraftingRecipe::new);

	private ModRecipes() {
	}

	public static void init() {
		Registry.register(BuiltInRegistries.RECIPE_SERIALIZER, MagicFirework.id("blueprint_writing"),
				BLUEPRINT_WRITING);
		Registry.register(BuiltInRegistries.RECIPE_SERIALIZER, MagicFirework.id("blueprint_crafting"),
				BLUEPRINT_CRAFTING);
	}
}
