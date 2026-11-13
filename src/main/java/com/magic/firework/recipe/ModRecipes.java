package com.magic.firework.recipe;

import com.magic.firework.MagicFirework;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.Registry;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.SimpleCraftingRecipeSerializer;

/**
 * Recipe serializers for the blueprint.
 *
 * <p>Only writing is a recipe today. Turning a written blueprint back into a firework is
 * deliberately not a crafting recipe: that will live in the editor, which is still to come.
 * A dynamic recipe needs no per-recipe JSON beyond its type, and it uses the vanilla
 * {@code crafting} recipe type so {@code RecipeManager.getRemainingItemsFor} can find it
 * when the result is taken.
 */
public final class ModRecipes {
	public static final RecipeSerializer<BlueprintWritingRecipe> BLUEPRINT_WRITING =
			new SimpleCraftingRecipeSerializer<>(BlueprintWritingRecipe::new);

	private ModRecipes() {
	}

	public static void init() {
		Registry.register(BuiltInRegistries.RECIPE_SERIALIZER, MagicFirework.id("blueprint_writing"),
				BLUEPRINT_WRITING);
	}
}
