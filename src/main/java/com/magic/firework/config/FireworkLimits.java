package com.magic.firework.config;

/** Hard safety limits. Immutable: change them here and rebuild, or wrap them in a config later. */
public final class FireworkLimits {
	private FireworkLimits() {
	}

	/** Default nesting depth allowed below a rocket; the {@code max.depth} attribute's default. */
	public static final int maxDepth = 3;
	/** Ceiling of the {@code max.depth} attribute, so a forged item cannot ask for more. */
	public static final int maxDepthCeiling = 16;
	/** Default children a single rocket may create over its whole life; the {@code max.children} default. */
	public static final int maxChildren = 32;
	/** Ceiling of the {@code max.children} attribute. */
	public static final int maxChildrenCeiling = 256;
	/**
	 * Hard cap on the depth of the nesting tree one item may carry, counting the rocket itself.
	 *
	 * <p>Separate from {@link #maxDepth} on purpose: that attribute decides at launch time how
	 * deep a show may go, this one decides how much recursion an item's NBT may contain at all,
	 * so a forged stack cannot make the loader walk an unbounded tree.
	 */
	public static final int maxNestingDepth = 8;
	/** Hard cap on how many rockets one item's nesting tree may describe, the root included. */
	public static final int maxNestingNodes = 64;
	/** Global cap of rocket entities spawned per server tick. */
	public static final int maxSpawnsPerTick = 64;
	/** Maximum call-stack depth of the instruction interpreter (nested {@code call} ops). */
	public static final int maxStackDepth = 8;
	/**
	 * Longest single {@code wait} an op may express, which is also the longest flight time a
	 * profile can write down. Beyond this a rocket is a world border, not a firework.
	 */
	public static final int maxWaitTicks = 400;
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
