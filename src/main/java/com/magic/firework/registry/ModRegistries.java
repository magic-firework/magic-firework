package com.magic.firework.registry;

import com.magic.firework.MagicFirework;
import com.magic.firework.attribute.AttributeBag;
import com.magic.firework.content.DefinitionIds;
import com.magic.firework.entity.MagicFireworkEntity;
import com.magic.firework.item.FireworkBlueprintItem;
import com.magic.firework.item.FireworkDataHelper;
import com.magic.firework.item.MagicFireworkItem;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.Registry;

/** Static game registries (entity type + item). */
public final class ModRegistries {
	public static final ResourceLocation MAGIC_FIREWORK_ID = new ResourceLocation(MagicFirework.MOD_ID, "magic_firework");
	public static final ResourceLocation FIREWORK_BLUEPRINT_ID = new ResourceLocation(MagicFirework.MOD_ID, "firework_blueprint");

	public static final EntityType<MagicFireworkEntity> MAGIC_FIREWORK_ENTITY = Registry.register(
			BuiltInRegistries.ENTITY_TYPE,
			MAGIC_FIREWORK_ID,
			EntityType.Builder.<MagicFireworkEntity>of(MagicFireworkEntity::new, MobCategory.MISC)
					.sized(0.25F, 0.25F)
					.clientTrackingRange(8)
					.build(MAGIC_FIREWORK_ID.toString()));

	public static final Item MAGIC_FIREWORK_ITEM = Registry.register(
			BuiltInRegistries.ITEM,
			MAGIC_FIREWORK_ID,
			new MagicFireworkItem(new Item.Properties().stacksTo(16)));

	public static final Item FIREWORK_BLUEPRINT_ITEM = Registry.register(
			BuiltInRegistries.ITEM,
			FIREWORK_BLUEPRINT_ID,
			new FireworkBlueprintItem(new Item.Properties().stacksTo(1)));

	private ModRegistries() {
	}

	/** The stack shown in the creative tab and used as the default item. */
	public static ItemStack defaultStack() {
		return FireworkDataHelper.create(MAGIC_FIREWORK_ITEM, DefinitionIds.RING12, AttributeBag.EMPTY);
	}

	/** A blank sheet; the writing recipe turns it into a written one. */
	public static ItemStack defaultBlueprint() {
		return new ItemStack(FIREWORK_BLUEPRINT_ITEM);
	}

	public static void init() {
	}
}
