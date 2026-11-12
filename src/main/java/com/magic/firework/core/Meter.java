package com.magic.firework.core;

/**
 * Running counters for one evaluation: the only place that decides "are we over budget".
 *
 * <p>Deliberately an object passed down the stamps instead of a static field, so a show can
 * never borrow another show's leftover allowance and so tests can run in parallel.
 */
public final class Meter {
	private final Budget budget;
	private int points;
	private int events;

	public Meter(Budget budget) {
		this.budget = budget == null ? Budget.of(0) : budget;
	}

	/** @return how many points were actually granted (0 when exhausted). */
	public int takePoints(int requested) {
		int remaining = budget.points() - points;
		int granted = Math.min(Math.max(0, requested), Math.max(0, remaining));
		points += granted;
		return granted;
	}

	/** True once no further point can be granted; callers use it to stop expanding. */
	public boolean exhausted() {
		return points >= budget.points();
	}

	public boolean takeEvent() {
		if (events >= budget.events()) {
			return false;
		}
		events++;
		return true;
	}
}
