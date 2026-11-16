package com.magic.firework.program;

import com.magic.firework.attribute.AttributeBag;
import com.magic.firework.attribute.Attributes;

/**
 * One timed entry of a program: what to do, and when.
 *
 * <p>Timing is a tick offset relative to the start of the frame, so a nested {@link Op.Call}
 * shifts a whole program without rewriting a single op. Speed is not part of timing: a rocket
 * that should hover while the clock runs carries {@code flight.max_speed: 0} from launch.
 *
 * <p>{@link #FUSE_AT} defers the offset to the {@code fuse.ticks} attribute, which is how the
 * crafting table stretches a rocket's flight time without editing the data pack.
 */
public record Instruction(Op op, int at, When when) {
	/** Use the {@code fuse.ticks} attribute instead of a fixed tick offset. */
	public static final int FUSE_AT = -1;

	public Instruction {
		at = at < FUSE_AT ? FUSE_AT : at;
		when = when == null ? When.NONE : when;
	}

	/** A plain op that runs as soon as the frame reaches it. */
	public static Instruction now(Op op) {
		return new Instruction(op, 0, When.NONE);
	}

	/**
	 * The tick offset this instruction waits for. Read live from the bag, so an item overlay
	 * that raises {@code fuse.ticks} stretches the whole show.
	 */
	public int resolveAt(AttributeBag bag) {
		return at == FUSE_AT ? Math.max(0, Attributes.FUSE_TICKS.get(bag)) : at;
	}

	public boolean guarded() {
		return when != When.NONE;
	}
}
