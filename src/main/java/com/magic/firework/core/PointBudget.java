package com.magic.firework.core;

/**
 * Per-tick cap on client-side particle spawns. A burst arriving after a lag spike, or several bursts
 * in one tick, must not turn into unbounded particles. A claim is cheap and single-threaded: the
 * client tick is the only writer. Deliberately not a queue, because a particle that misses its tick
 * is dropped and counted: replaying it later would break the show's timing.
 */
public final class PointBudget {
	private final int perTick;
	private int remaining;
	private int dropped;

	public PointBudget(int perTick) {
		this.perTick = Math.max(0, perTick);
	}

	/** Start a new tick: the whole budget is available and the drop counter resets. */
	public void beginTick() {
		remaining = perTick;
		dropped = 0;
	}

	/** @return true when this particle may be spawned; false means it was dropped */
	public boolean claim() {
		if (remaining <= 0) {
			dropped++;
			return false;
		}
		remaining--;
		return true;
	}

	public int perTick() {
		return perTick;
	}

	public int remaining() {
		return remaining;
	}

	public int droppedThisTick() {
		return dropped;
	}
}
