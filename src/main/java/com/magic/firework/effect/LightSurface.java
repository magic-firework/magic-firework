package com.magic.firework.effect;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/**
 * The small part of a level the light bookkeeping needs.
 *
 * <p>Extracted so {@link LightSources} can be tested without a running server: the placement
 * rules, expiry and dimension unloading are decisions about positions and time, not about
 * chunks. The one production implementation wraps a {@code ServerLevel}.
 */
public interface LightSurface {
	ResourceKey<Level> dimension();

	boolean isAir(BlockPos pos);

	boolean isLight(BlockPos pos);

	void setLight(BlockPos pos);

	void removeLight(BlockPos pos);

	long gameTime();
}
