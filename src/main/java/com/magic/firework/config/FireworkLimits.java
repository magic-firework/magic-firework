package com.magic.firework.config;

/** Hard safety limits. Immutable: change them here and rebuild, or wrap them in a config later. */
public final class FireworkLimits {
	private FireworkLimits() {
	}

	/** Maximum nesting depth of split / nested rockets. */
	public static final int maxDepth = 3;
	/** Maximum children a single rocket may create over its whole life. */
	public static final int maxChildren = 32;
	/** Global cap of rocket entities spawned per server tick. */
	public static final int maxSpawnsPerTick = 64;
	/** Maximum call-stack depth of the instruction interpreter (nested {@code call} ops). */
	public static final int maxStackDepth = 8;
	/** Maximum instructions executed per rocket per tick. */
	public static final int maxOpsPerTick = 256;
	/** Whether the {@code light} op may place light blocks in the world. */
	public static final boolean allowLightBlocks = true;
	/** Upper bound of points a single baked stroke show may emit. */
	public static final int maxPointsPerShow = 2048;
	/** Global cap of baked stroke points per server tick (all shows together). */
	public static final int maxPointsPerTick = 8192;
	/** Radius in blocks a burst is sent to; {@code <= 0} disables distance culling. */
	public static final double maxShowDistance = 256.0;
}
