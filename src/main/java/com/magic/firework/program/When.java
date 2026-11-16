package com.magic.firework.program;

import net.minecraft.world.phys.Vec3;

/**
 * Optional physics guard on an {@link Instruction}: the op runs once its tick has come
 * <em>and</em> the condition holds.
 *
 * <p>This is the only place flight physics reaches the program. It is a guard, not a blocking
 * wait: time still comes from {@link Instruction#at()}, and the program never depends on a
 * condition to make progress when {@code timeout} is set.
 *
 * <p>{@code timeout} is how many ticks the guard may keep failing after its tick; a negative
 * value waits indefinitely.
 */
public record When(Double heightAbove, Double heightBelow, boolean collision, int timeout) {
	public static final When NONE = new When(null, null, false, -1);

	public When {
		timeout = timeout < 0 ? -1 : timeout;
	}

	/** @return whether the guard allows the op to run at this tick */
	public boolean satisfied(Vec3 position, boolean collided) {
		if (heightAbove != null && position.y <= heightAbove) {
			return false;
		}
		if (heightBelow != null && position.y >= heightBelow) {
			return false;
		}
		return !collision || collided;
	}

	/** @param ticksWaiting ticks elapsed since the guard's own tick came */
	public boolean expired(int ticksWaiting) {
		return timeout >= 0 && ticksWaiting >= timeout;
	}

	public boolean isEmpty() {
		return heightAbove == null && heightBelow == null && !collision;
	}
}
