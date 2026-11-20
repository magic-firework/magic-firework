package com.magic.firework.config;

/**
 * Safety limits. The configurable fields are defaults; {@link FireworkConfig} may replace them
 * once at startup. Treat them as read-only after that.
 */
public final class FireworkLimits {
	private FireworkLimits() {
	}

	/** Hardest cap on the nesting depth below a rocket, whatever the content asks for. */
	public static int maxDepth = 3;
	/**
	 * Children a single rocket may create over its whole life.
	 *
	 * <p>Kept below {@link #maxNestingNodes} on purpose: a budget of its own, so a split show can
	 * never spend the whole item tree in one generation.
	 */
	public static int maxChildren = 32;
	/**
	 * Ceiling of the {@code count} a single {@code spawn} op may ask for; anything past the
	 * runtime budget is simply never reached.
	 */
	public static final int maxChildrenCeiling = 256;
	/**
	 * Hard ceiling on a rocket's velocity in blocks/tick. A launch moves at the speed its {@code launch}
	 * op or {@code flight.speed} asked for, already clamped to this number; the brake only makes it
	 * smaller. The cap is the backstop that keeps a forged or future velocity from teleporting it
	 * across the world.
	 */
	public static double maxSpeed = 4.0;
	/**
	 * Per-tick brake along the flight line, the vanilla gravity value: a rocket slows down at the
	 * rate a falling entity speeds up. The rocket applies it itself ({@code FlightStep}) because
	 * 1.20.1 only applies gravity inside {@code LivingEntity.travel}, which pulls unconditionally
	 * <em>downward</em>; the rocket must hover, and a diagonal shot must brake like a vertical one.
	 */
	public static final double deceleration = 0.08;
	/**
	 * Hard cap on how many rockets one item's nesting tree may describe, the root included. A chain
	 * spends one node per level, so a forged stack cannot make the loader walk deeper, and raising it
	 * never raises what a launch may do: how deep a show actually splits is the separate runtime
	 * budget {@link #maxDepth}.
	 */
	public static final int maxNestingNodes = 128;
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
	/** Hard cap on pending scheduled effects across every dimension. */
	public static final int maxPendingEffects = 1024;
	/** Whether the {@code light} op may place light blocks in the world. */
	public static boolean allowLightBlocks = true;
	/** Upper bound of points a single baked stroke show may emit. */
	public static int maxPointsPerShow = 2048;
	/** Global cap of baked stroke points per server tick (all shows together). */
	public static int maxPointsPerTick = 8192;
	/** Client-side cap of particle spawns per tick across all playing clouds. */
	public static final int maxClientPointsPerTick = 4096;
	/** Radius in blocks a burst is sent to; {@code <= 0} disables distance culling. */
	public static final double maxShowDistance = 256.0;
}
