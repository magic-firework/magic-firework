package com.magic.firework.effect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;

import com.magic.firework.config.FireworkLimits;

import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.Level;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** Placement, expiry and unload rules of the {@code light} op, without a live level. */
class LightSourcesTest {
	static {
		// ResourceKey.create and Level's dimension keys live in the vanilla registries.
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
	}

	private static final ResourceKey<Level> OVERWORLD = ResourceKey.create(Registries.DIMENSION,
			new ResourceLocation("test", "overworld"));
	private static final ResourceKey<Level> NETHER = ResourceKey.create(Registries.DIMENSION,
			new ResourceLocation("test", "nether"));
	private static final BlockPos A = new BlockPos(1, 64, 2);
	private static final BlockPos B = new BlockPos(-3, 70, 8);
	private static final BlockPos SOLID = new BlockPos(0, 60, 0);

	@AfterEach
	void restoreTheDefault() {
		FireworkLimits.allowLightBlocks = true;
	}

	@Test
	void onlyAirGetsALightAndTheSamePositionIsPlacedOnce() {
		Surface surface = new Surface(OVERWORLD);
		LightSources lights = new LightSources();

		lights.light(surface, A, 20);
		lights.light(surface, A, 20);

		assertEquals(1, lights.placedCount(OVERWORLD));
		assertTrue(surface.lights.contains(A));
		assertEquals(1, surface.placements, "the second call is a no-op");
	}

	@Test
	void aSolidBlockAndANonPositiveDurationPlaceNothing() {
		Surface surface = new Surface(OVERWORLD);
		surface.solid.add(SOLID);
		LightSources lights = new LightSources();

		lights.light(surface, SOLID, 20);
		lights.light(surface, A, 0);
		lights.light(surface, B, -5);

		assertEquals(0, lights.placedCount(OVERWORLD));
		assertTrue(surface.lights.isEmpty());
	}

	@Test
	void theConfigSwitchDisablesPlacement() {
		Surface surface = new Surface(OVERWORLD);
		LightSources lights = new LightSources();

		FireworkLimits.allowLightBlocks = false;
		lights.light(surface, A, 20);

		assertEquals(0, lights.placedCount(OVERWORLD));
		assertTrue(surface.lights.isEmpty());
	}

	@Test
	void aLightIsRemovedWhenItExpiresButNotBefore() {
		Surface surface = new Surface(OVERWORLD);
		LightSources lights = new LightSources();
		lights.light(surface, A, 5);

		surface.now = 5;
		lights.tick();
		assertEquals(1, lights.placedCount(OVERWORLD), "expiry is exclusive");
		assertTrue(surface.lights.contains(A));

		surface.now = 6;
		lights.tick();
		assertEquals(0, lights.placedCount(OVERWORLD));
		assertTrue(surface.lights.isEmpty());
	}

	@Test
	void aBlockAlreadyReplacedByThePlayerIsNotRemovedAgain() {
		Surface surface = new Surface(OVERWORLD);
		LightSources lights = new LightSources();
		lights.light(surface, A, 5);
		lights.light(surface, B, 5);

		// Simulate a player replacing one light block with something else.
		surface.lights.remove(A);
		surface.now = 10;
		lights.tick();

		assertEquals(0, lights.placedCount(OVERWORLD));
		assertEquals(1, surface.removals, "only the block that was still light is removed");
		assertFalse(surface.lights.contains(B));
	}

	@Test
	void unloadingADimensionDropsOnlyItsBlocks() {
		Surface overworld = new Surface(OVERWORLD);
		Surface nether = new Surface(NETHER);
		LightSources lights = new LightSources();
		lights.light(overworld, A, 100);
		lights.light(nether, B, 100);

		lights.onDimensionUnload(OVERWORLD);

		assertEquals(0, lights.placedCount(OVERWORLD));
		assertEquals(1, lights.placedCount(NETHER));
		assertTrue(overworld.lights.isEmpty(), "the live block is removed");
		assertTrue(nether.lights.contains(B), "the other dimension is untouched");
	}

	@Test
	void clearingRemovesEveryLiveBlockAndForgetsThem() {
		Surface surface = new Surface(OVERWORLD);
		LightSources lights = new LightSources();
		lights.light(surface, A, 100);
		lights.light(surface, B, 100);
		surface.lights.remove(A); // replaced by the player; must not be removed again

		lights.clear();

		assertEquals(0, lights.placedCount(OVERWORLD));
		assertEquals(1, surface.removals);
		assertFalse(surface.lights.contains(B));
	}

	@Test
	void startupCleanupRemovesOnlyStillLightBlocksAndDropsTheLedger() {
		Surface surface = new Surface(OVERWORLD);
		surface.lights.add(A); // left behind by a killed server
		LightLedger ledger = new LightLedger();
		ledger.put(OVERWORLD, A, 100L);
		ledger.put(NETHER, B, 100L); // its dimension is not loaded this start
		LightSources lights = new LightSources();

		int removed = lights.recover(ledger, dimension -> dimension.equals(OVERWORLD) ? surface : null);

		assertEquals(1, removed);
		assertFalse(surface.lights.contains(A));
		assertTrue(ledger.isEmpty(), "recovery consumes the ledger, loaded or not");
	}

	/** A detached level: positions and a clock, no chunks. */
	private static final class Surface implements LightSurface {
		private final ResourceKey<Level> dimension;
		private final Set<BlockPos> solid = new HashSet<>();
		private final Set<BlockPos> lights = new HashSet<>();
		private long now;
		private int placements;
		private int removals;

		private Surface(ResourceKey<Level> dimension) {
			this.dimension = dimension;
		}

		@Override
		public ResourceKey<Level> dimension() {
			return dimension;
		}

		@Override
		public boolean isAir(BlockPos pos) {
			return !solid.contains(pos) && !lights.contains(pos);
		}

		@Override
		public boolean isLight(BlockPos pos) {
			return lights.contains(pos);
		}

		@Override
		public void setLight(BlockPos pos) {
			placements++;
			lights.add(pos);
		}

		@Override
		public void removeLight(BlockPos pos) {
			removals++;
			lights.remove(pos);
		}

		@Override
		public long gameTime() {
			return now;
		}
	}
}
