package com.magic.firework.config;

/** Hard safety limits. Immutable: change them here and rebuild, or wrap them in a config later. */
public final class FireworkLimits {
	private FireworkLimits() {
	}

	/** Hardest cap on the nesting depth below a rocket, whatever the content asks for. */
	public static final int maxDepth = 3;
	/**
	 * Children a single rocket may create over its whole life.
	 *
	 * <p>Kept below {@link #maxNestingNodes} on purpose: a budget of its own, so a split show can
	 * never spend the whole item tree in one generation.
	 */
	public static final int maxChildren = 32;
	/**
	 * Ceiling of the {@code count} a single {@code spawn} op may ask for; anything past the
	 * runtime budget is simply never reached.
	 */
	public static final int maxChildrenCeiling = 256;
	/**
	 * Hard ceiling on a rocket's velocity in blocks/tick.
	 *
	 * <p>A launch moves at the speed its {@code launch} op or {@code flight.speed} asked for, and
	 * that is already clamped to this number; the brake only ever makes it smaller. The cap is the
	 * backstop that keeps a forged or future velocity from teleporting it across the world.
	 */
	public static final double maxSpeed = 4.0;
	/**
	 * Per-tick brake along the flight line: the vanilla gravity value, so a rocket slows down at
	 * the rate a falling entity speeds up.
	 *
	 * <p>The rocket applies it itself (see {@code FlightStep}) instead of switching vanilla gravity
	 * on, for two reasons: 1.20.1 only applies gravity inside {@code LivingEntity.travel}, which
	 * pulls unconditionally <em>downward</em> — the rocket has to stop at rest and hover instead of
	 * falling back on the player, and a diagonally aimed shot has to slow down the same way a
	 * vertical one does.
	 */
	public static final double deceleration = 0.08;
	/**
	 * Hard cap on how many rockets one item's nesting tree may describe, the root included.
	 *
	 * <p>This is the only structural bound on an item tree, and it is enough: a chain spends one
	 * node per level, so a forged stack can never make the loader walk deeper than this many
	 * rockets. How deep a show may <em>actually</em> split is a separate runtime budget
	 * ({@link #maxDepth}); the two are deliberately independent, so raising the data budget never
	 * raises what a launch is allowed to do.
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
	/** Whether the {@code light} op may place light blocks in the world. */
	public static final boolean allowLightBlocks = true;
	/** Upper bound of points a single baked stroke show may emit. */
	public static final int maxPointsPerShow = 2048;
	/** Global cap of baked stroke points per server tick (all shows together). */
	public static final int maxPointsPerTick = 8192;
	/** Radius in blocks a burst is sent to; {@code <= 0} disables distance culling. */
	public static final double maxShowDistance = 256.0;
}
