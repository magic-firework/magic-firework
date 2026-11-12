package com.magic.firework.platform;

import com.magic.firework.config.FireworkLimits;

/**
 * Per-tick counters owned by one runtime instead of static fields.
 *
 * <p>Two limits share a ledger because both are "what may happen this tick": rocket spawns
 * and baked points. Resetting it is part of the runtime's tick, so two servers (or a test)
 * can never steal each other's allowance.
 */
public final class TickLedger {
	private int spawns;
	private int points;

	public boolean trySpawn() {
		if (spawns >= FireworkLimits.maxSpawnsPerTick) {
			return false;
		}
		spawns++;
		return true;
	}

	/** @return how many points were granted (0 when exhausted) */
	public int claimPoints(int requested) {
		int remaining = Math.max(0, FireworkLimits.maxPointsPerTick - points);
		int granted = Math.min(Math.max(0, requested), remaining);
		points += granted;
		return granted;
	}

	public void reset() {
		spawns = 0;
		points = 0;
	}

	public int spawnsThisTick() {
		return spawns;
	}

	public int pointsThisTick() {
		return points;
	}
}
