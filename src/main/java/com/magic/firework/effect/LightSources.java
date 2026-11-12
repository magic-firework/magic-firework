package com.magic.firework.effect;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.Map;

import com.magic.firework.config.FireworkLimits;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;

/**
 * Dynamic light for the {@code light} op: places a vanilla light block for N ticks and
 * removes it again. Only ever touches air, so it cannot destroy terrain.
 *
 * <p>One instance per runtime: the placed-block map is no longer a static that survives a
 * world switch. {@link #clear()} runs when the runtime closes.
 */
public final class LightSources {
	private final Map<ServerLevel, Map<BlockPos, Long>> placed = new IdentityHashMap<>();

	public void light(ServerLevel level, BlockPos pos, int ticks) {
		if (!FireworkLimits.allowLightBlocks || ticks <= 0) {
			return;
		}
		if (!level.getBlockState(pos).isAir()) {
			return;
		}
		Map<BlockPos, Long> blocks = placed.computeIfAbsent(level, key -> new HashMap<>());
		if (blocks.containsKey(pos)) {
			return;
		}
		blocks.put(pos.immutable(), level.getGameTime() + ticks);
		level.setBlockAndUpdate(pos, Blocks.LIGHT.defaultBlockState());
	}

	/** Called once per server tick to restore the blocks whose light expired. */
	public void tick(MinecraftServer server) {
		if (placed.isEmpty()) {
			return;
		}
		for (Iterator<Map.Entry<ServerLevel, Map<BlockPos, Long>>> levels = placed.entrySet().iterator(); levels
				.hasNext();) {
			Map.Entry<ServerLevel, Map<BlockPos, Long>> entry = levels.next();
			ServerLevel level = entry.getKey();
			for (Iterator<Map.Entry<BlockPos, Long>> blocks = entry.getValue().entrySet().iterator(); blocks
					.hasNext();) {
				Map.Entry<BlockPos, Long> block = blocks.next();
				if (level.getGameTime() <= block.getValue()) {
					continue;
				}
				if (level.getBlockState(block.getKey()).is(Blocks.LIGHT)) {
					level.removeBlock(block.getKey(), false);
				}
				blocks.remove();
			}
			if (entry.getValue().isEmpty()) {
				levels.remove();
			}
		}
	}

	public void clear() {
		placed.clear();
	}
}
