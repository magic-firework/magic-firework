package com.magic.firework.item;

import java.util.List;

import com.magic.firework.MagicFirework;
import com.magic.firework.entity.MagicFireworkEntity;
import com.magic.firework.platform.FireworkRuntime;
import com.magic.firework.platform.RuntimeRegistry;

import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** The launchable item: a profile id plus a per-instance attribute overlay. */
public class MagicFireworkItem extends Item {
	public MagicFireworkItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		if (level instanceof ServerLevel serverLevel) {
			FireworkRuntime runtime = RuntimeRegistry.current();
			if (runtime == null) {
				return InteractionResultHolder.fail(stack);
			}
			FireworkDataHelper.Resolved resolved = FireworkDataHelper.resolve(stack, runtime.definitions());
			if (resolved.profile() == null) {
				MagicFirework.LOGGER.warn("unknown firework profile {}", resolved.id());
				return InteractionResultHolder.fail(stack);
			}
			Vec3 spawn = player.getEyePosition().add(player.getLookAngle().scale(0.4));
			MagicFireworkEntity rocket = MagicFireworkEntity.launch(serverLevel, spawn, player.getLookAngle(),
					resolved.profile(), resolved.overlay(), player, null);
			if (rocket != null && !player.getAbilities().instabuild) {
				stack.shrink(1);
			}
		}
		return InteractionResultHolder.success(stack);
	}

	@Override
	public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag flag) {
		tooltip.add(Component.literal(FireworkDataHelper.profileId(stack).toString()).withStyle(ChatFormatting.GRAY));
		CompoundTag overlay = FireworkDataHelper.overlay(stack).save();
		for (String key : overlay.getAllKeys()) {
			Tag value = overlay.get(key);
			String text = value instanceof CompoundTag ? "<inline show>" : value.getAsString();
			tooltip.add(Component.literal(" " + key + " = " + text)
					.withStyle(ChatFormatting.DARK_GRAY));
		}
	}
}
