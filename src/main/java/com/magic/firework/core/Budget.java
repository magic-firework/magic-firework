package com.magic.firework.core;

/** Hard caps for one evaluation. Passed in, never global: two evaluations cannot leak into each other. */
public record Budget(int points, int events) {
	public Budget {
		points = Math.max(0, points);
		events = Math.max(0, events);
	}

	public static Budget of(int points) {
		return new Budget(points, 64);
	}
}
