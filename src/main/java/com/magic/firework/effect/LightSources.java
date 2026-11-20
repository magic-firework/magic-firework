package com.magic.firework.effect;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.Map;

import com.magic.firework.config.FireworkLimits;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;

/**
 * Dynamic light for the {@code light} op: places a vanilla light block for N ticks and
 * removes it again. Only ever touches air, so it cannot destroy terrain.
 *
 * <p>One instance per runtime: the placed-block map is no longer a static that survives a
 * world switch. A {@link LightLedger} mirrors every placement into the world save, so a crash
 * is cleaned up on the next start; {@link #clear()} runs when the runtime closes.
 */
public final class LightSources {
	private final Map<ServerLevel, Map<BlockPos, Long>> placed = new IdentityHashMap<>();
	private LightLedger ledger;

	/**
	 * Adopt the world's ledger and remove every light block a previous run left behind.
	 * Runs once the server is started, when the levels exist.
	 */
	public void bind(MinecraftServer server) {
		this.ledger = LightLedger.get(server);
		recover(server);
	}

	/** @return how many leftover light blocks were removed */
	public int recover(MinecraftServer server) {
		LightLedger current = ledger;
		if (current == null) {
			return 0;
		}
		int removed = 0;
		for (Map.Entry<ResourceKey<Level>, Map<BlockPos, Long>> entry : current.entries().entrySet()) {
			ServerLevel level = server.getLevel(entry.getKey());
			if (level == null) {
				continue;
			}
			for (BlockPos pos : entry.getValue().keySet()) {
				if (level.getBlockState(pos).is(Blocks.LIGHT)) {
					level.removeBlock(pos, false);
					removed++;
				}
			}
		}
		current.clear();
		return removed;
	}

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
		long until = level.getGameTime() + ticks;
		blocks.put(pos.immutable(), until);
		level.setBlockAndUpdate(pos, Blocks.LIGHT.defaultBlockState());
		if (ledger != null) {
			ledger.put(level.dimension(), pos, until);
		}
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
				if (ledger != null) {
					ledger.remove(level.dimension(), block.getKey());
				}
				blocks.remove();
			}
			if (entry.getValue().isEmpty()) {
				levels.remove();
			}
		}
	}

	public void clear() {
		for (Map.Entry<ServerLevel, Map<BlockPos, Long>> entry : placed.entrySet()) {
			ServerLevel level = entry.getKey();
			for (BlockPos pos : entry.getValue().keySet()) {
				if (level.getBlockState(pos).is(Blocks.LIGHT)) {
					level.removeBlock(pos, false);
				}
			}
		}
		placed.clear();
		if (ledger != null) {
			ledger.clear();
		}
	}
}
