package com.magic.firework.item;

import java.util.List;

import com.magic.firework.MagicFirework;
import com.magic.firework.entity.MagicFireworkEntity;
import com.magic.firework.platform.FireworkRuntime;
import com.magic.firework.platform.RuntimeRegistry;
import com.magic.firework.rocket.RocketSpec;

import net.minecraft.ChatFormatting;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** The launchable item: a nested rocket tree (profile id, per-instance overlay, child rockets). */
public class MagicFireworkItem extends Item {
	/** Vanilla's offset: the rocket sits just off the face the player clicked. */
	public static final double PLACEMENT_OFFSET = 0.15;
	/** How deep the tooltip describes the child tree before it stops listing. */
	private static final int TOOLTIP_DEPTH = 3;

	public MagicFireworkItem(Properties properties) {
		super(properties);
	}

	/**
	 * Clicking a block launches from the click, not from the player's face: the rocket appears at
	 * the hit point pushed {@value #PLACEMENT_OFFSET} blocks along the clicked face, exactly like
	 * a vanilla firework rocket. That is also what makes it visible where the player aimed.
	 */
	@Override
	public InteractionResult useOn(UseOnContext context) {
		Player player = context.getPlayer();
		if (player == null) {
			return InteractionResult.PASS;
		}
		if (!(context.getLevel() instanceof ServerLevel level)) {
			return InteractionResult.SUCCESS;
		}
		Vec3 hit = context.getClickLocation();
		Direction face = context.getClickedFace();
		Vec3 position = hit.add(face.getStepX() * PLACEMENT_OFFSET,
				face.getStepY() * PLACEMENT_OFFSET,
				face.getStepZ() * PLACEMENT_OFFSET);
		return launchFrom(level, player, context.getItemInHand(), position);
	}

	private InteractionResult launchFrom(ServerLevel level, Player player, ItemStack stack, Vec3 position) {
		FireworkRuntime runtime = RuntimeRegistry.of(level.getServer());
		if (runtime == null) {
			return InteractionResult.FAIL;
		}
		RocketSpec spec = FireworkDataHelper.spec(stack);
		if (runtime.definitions().get(spec.profileId()) == null) {
			MagicFirework.LOGGER.warn("unknown firework profile {}", spec.profileId());
			return InteractionResult.FAIL;
		}
		MagicFireworkEntity rocket = MagicFireworkEntity.launch(runtime, level, position, player.getLookAngle(), spec,
				player, null);
		if (rocket == null) {
			return InteractionResult.FAIL;
		}
		if (!player.getAbilities().instabuild) {
			stack.shrink(1);
		}
		return InteractionResult.CONSUME;
	}

	@Override
	public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag flag) {
		RocketSpec spec = FireworkDataHelper.spec(stack);
		tooltip.add(Component.literal(spec.profileId().toString()).withStyle(ChatFormatting.GRAY));
		for (String key : spec.overlay().keys()) {
			Tag value = spec.overlay().raw().get(key);
			String text = value instanceof CompoundTag ? "<inline show>" : value.getAsString();
			tooltip.add(Component.literal(" " + key + " = " + text)
					.withStyle(ChatFormatting.DARK_GRAY));
		}
		childLines(spec.children(), 0, tooltip);
	}

	/**
	 * Lists the child tree, one line per rocket. The index is what a program's
	 * {@code "children": [n]} refers to, so showing it is the only way a player can tell which
	 * crafted rocket a spawn op will pick.
	 */
	private static void childLines(List<RocketSpec> children, int depth, List<Component> tooltip) {
		if (children.isEmpty()) {
			return;
		}
		String indent = " ".repeat(depth + 1);
		ChatFormatting style = depth == 0 ? ChatFormatting.DARK_AQUA : ChatFormatting.DARK_GRAY;
		tooltip.add(Component.literal(indent)
				.append(Component.translatable("tooltip.magic-firework.firework.children", children.size()))
				.withStyle(style));
		for (int i = 0; i < children.size(); i++) {
			RocketSpec child = children.get(i);
			Component line = Component.literal(indent)
					.append(Component.translatable("tooltip.magic-firework.firework.child", i,
							child.profileId().toString()));
			if (!child.children().isEmpty()) {
				line = line.copy().append(Component.translatable(
						"tooltip.magic-firework.firework.child_nested", child.children().size()));
			}
			tooltip.add(line.copy().withStyle(ChatFormatting.DARK_GRAY));
			if (depth + 1 < TOOLTIP_DEPTH) {
				childLines(child.children(), depth + 1, tooltip);
			}
		}
	}
}
