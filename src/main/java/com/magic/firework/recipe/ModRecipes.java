package com.magic.firework.recipe;

import com.magic.firework.MagicFirework;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.Registry;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.SimpleCraftingRecipeSerializer;

/**
 * Recipe serializers for the blueprint.
 *
 * <p>Both recipes are dynamic ({@link net.minecraft.world.item.crafting.CustomRecipe}), so
 * they are registered as serializers only and need no per-recipe JSON beyond a type. They
 * use the vanilla {@code crafting} recipe type, which also means
 * {@code RecipeManager.getRemainingItemsFor} finds them when the result is taken.
 */
public final class ModRecipes {
	public static final RecipeSerializer<BlueprintWritingRecipe> BLUEPRINT_WRITING =
			new SimpleCraftingRecipeSerializer<>(BlueprintWritingRecipe::new);
	public static final RecipeSerializer<BlueprintCraftingRecipe> BLUEPRINT_CRAFTING =
			new SimpleCraftingRecipeSerializer<>(BlueprintCraftingRecipe::new);

	private ModRecipes() {
	}

	public static void init() {
		Registry.register(BuiltInRegistries.RECIPE_SERIALIZER, MagicFirework.id("blueprint_writing"),
				BLUEPRINT_WRITING);
		Registry.register(BuiltInRegistries.RECIPE_SERIALIZER, MagicFirework.id("blueprint_crafting"),
				BLUEPRINT_CRAFTING);
	}
}
