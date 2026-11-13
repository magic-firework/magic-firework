package com.magic.firework.item;

import java.util.List;

import com.magic.firework.MagicFirework;
import com.magic.firework.craft.Cost;

import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/**
 * The blueprint: one saved firework formula on a sheet of paper.
 *
 * <p>Blank until an anvil-free writing recipe pairs it with a magic firework. Once written
 * it is never edited again — the bill lives in the NBT, so the same sheet always costs the
 * same materials.
 */
public class FireworkBlueprintItem extends Item {
	public FireworkBlueprintItem(Properties properties) {
		super(properties);
	}

	@Override
	public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag flag) {
		if (!BlueprintDataHelper.isWritten(stack)) {
			tooltip.add(Component.translatable("tooltip.magic-firework.blueprint.blank")
					.withStyle(ChatFormatting.GRAY));
			tooltip.add(Component.translatable("tooltip.magic-firework.blueprint.blank_hint")
					.withStyle(ChatFormatting.DARK_GRAY));
			return;
		}
		ResourceLocation profile = FireworkDataHelper.profileId(BlueprintDataHelper.source(stack));
		tooltip.add(Component.literal(profile.toString()).withStyle(ChatFormatting.GRAY));
		tooltip.add(Component.translatable("tooltip.magic-firework.blueprint.cost", materials(stack))
				.withStyle(ChatFormatting.DARK_GRAY));
		tooltip.add(Component.translatable("tooltip.magic-firework.blueprint.locked")
				.withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
	}

	private static Component materials(ItemStack stack) {
		MutableComponent line = Component.empty();
		boolean first = true;
		for (Cost cost : BlueprintDataHelper.cost(stack)) {
			if (!first) {
				line.append(Component.literal(", "));
			}
			first = false;
			line.append(Component.translatable("tooltip.magic-firework.blueprint.entry",
					itemName(cost.item()), cost.count()));
		}
		return line;
	}

	private static Component itemName(String id) {
		ResourceLocation key = ResourceLocation.tryParse(id);
		Item item = key == null ? null : BuiltInRegistries.ITEM.get(key);
		if (item == null) {
			MagicFirework.LOGGER.debug("blueprint names an unknown item {}; showing its id", id);
			return Component.literal(id);
		}
		return item.getDescription();
	}
}
