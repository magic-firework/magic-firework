package com.magic.firework.effect;

import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
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
 *
 * <p>Placements are keyed by dimension and go through {@link LightSurface}, so the placement,
 * expiry and unload rules can be tested without a live level.
 */
public final class LightSources {
	private final Map<ResourceKey<Level>, LightSurface> surfaces = new LinkedHashMap<>();
	private final Map<ResourceKey<Level>, Map<BlockPos, Long>> placed = new LinkedHashMap<>();
	private LightLedger ledger;

	/**
	 * Adopt the world's ledger and remove every light block a previous run left behind.
	 * Runs once the server is started, when the levels exist.
	 */
	public void bind(MinecraftServer server) {
		this.ledger = LightLedger.get(server);
		recover(server);
	}

	/** Drop a dimension that is going away; remove its live blocks and ledger rows. */
	public void onDimensionUnload(ResourceKey<Level> dimension) {
		Map<BlockPos, Long> blocks = placed.remove(dimension);
		LightSurface surface = surfaces.remove(dimension);
		if (blocks == null) {
			return;
		}
		for (BlockPos pos : blocks.keySet()) {
			if (surface != null && surface.isLight(pos)) {
				surface.removeLight(pos);
			}
			if (ledger != null) {
				ledger.remove(dimension, pos);
			}
		}
	}

	/** @return how many leftover light blocks were removed */
	public int recover(MinecraftServer server) {
		return recover(ledger, dimension -> {
			ServerLevel level = server.getLevel(dimension);
			return level == null ? null : new ServerLevelSurface(level);
		});
	}

	/** Test seam: startup cleanup only needs surfaces for the recorded dimensions. */
	int recover(LightLedger source, java.util.function.Function<ResourceKey<Level>, LightSurface> levels) {
		if (source == null) {
			return 0;
		}
		int removed = 0;
		for (Map.Entry<ResourceKey<Level>, Map<BlockPos, Long>> entry : source.entries().entrySet()) {
			LightSurface surface = levels.apply(entry.getKey());
			if (surface == null) {
				continue;
			}
			for (BlockPos pos : entry.getValue().keySet()) {
				if (surface.isLight(pos)) {
					surface.removeLight(pos);
					removed++;
				}
			}
		}
		source.clear();
		return removed;
	}

	public void light(ServerLevel level, BlockPos pos, int ticks) {
		light(surfaceOf(level), pos, ticks);
	}

	/** Test seam: the placement rules only need a {@link LightSurface}. */
	void light(LightSurface surface, BlockPos pos, int ticks) {
		if (!FireworkLimits.allowLightBlocks || ticks <= 0) {
			return;
		}
		if (!surface.isAir(pos)) {
			return;
		}
		surfaces.put(surface.dimension(), surface);
		Map<BlockPos, Long> blocks = placed.computeIfAbsent(surface.dimension(), key -> new HashMap<>());
		if (blocks.containsKey(pos)) {
			return;
		}
		long until = surface.gameTime() + ticks;
		blocks.put(pos.immutable(), until);
		surface.setLight(pos);
		if (ledger != null) {
			ledger.put(surface.dimension(), pos, until);
		}
	}

	/** Called once per server tick to restore the blocks whose light expired. */
	public void tick() {
		for (Iterator<Map.Entry<ResourceKey<Level>, Map<BlockPos, Long>>> dimensions = placed.entrySet()
				.iterator(); dimensions.hasNext();) {
			Map.Entry<ResourceKey<Level>, Map<BlockPos, Long>> entry = dimensions.next();
			LightSurface surface = surfaces.get(entry.getKey());
			if (surface == null) {
				// No live level for this dimension; keep the rows for the next startup recovery.
				continue;
			}
			for (Iterator<Map.Entry<BlockPos, Long>> blocks = entry.getValue().entrySet().iterator(); blocks
					.hasNext();) {
				Map.Entry<BlockPos, Long> block = blocks.next();
				if (surface.gameTime() <= block.getValue()) {
					continue;
				}
				if (surface.isLight(block.getKey())) {
					surface.removeLight(block.getKey());
				}
				if (ledger != null) {
					ledger.remove(entry.getKey(), block.getKey());
				}
				blocks.remove();
			}
			if (entry.getValue().isEmpty()) {
				dimensions.remove();
			}
		}
	}

	public void clear() {
		for (Map.Entry<ResourceKey<Level>, Map<BlockPos, Long>> entry : placed.entrySet()) {
			LightSurface surface = surfaces.get(entry.getKey());
			if (surface == null) {
				continue;
			}
			for (BlockPos pos : entry.getValue().keySet()) {
				if (surface.isLight(pos)) {
					surface.removeLight(pos);
				}
			}
		}
		placed.clear();
		surfaces.clear();
		if (ledger != null) {
			ledger.clear();
		}
	}

	/** @return how many blocks are currently recorded for a dimension */
	public int placedCount(ResourceKey<Level> dimension) {
		Map<BlockPos, Long> blocks = placed.get(dimension);
		return blocks == null ? 0 : blocks.size();
	}

	private LightSurface surfaceOf(ServerLevel level) {
		return surfaces.computeIfAbsent(level.dimension(), key -> new ServerLevelSurface(level));
	}

	/** The production surface: every decision delegates to the live {@link ServerLevel}. */
	private record ServerLevelSurface(ServerLevel level) implements LightSurface {
		@Override
		public ResourceKey<Level> dimension() {
			return level.dimension();
		}

		@Override
		public boolean isAir(BlockPos pos) {
			return level.getBlockState(pos).isAir();
		}

		@Override
		public boolean isLight(BlockPos pos) {
			return level.getBlockState(pos).is(Blocks.LIGHT);
		}

		@Override
		public void setLight(BlockPos pos) {
			level.setBlockAndUpdate(pos, Blocks.LIGHT.defaultBlockState());
		}

		@Override
		public void removeLight(BlockPos pos) {
			level.removeBlock(pos, false);
		}

		@Override
		public long gameTime() {
			return level.getGameTime();
		}
	}
}
