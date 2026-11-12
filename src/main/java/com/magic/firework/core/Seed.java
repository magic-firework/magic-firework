package com.magic.firework.core;

/**
 * Deterministic random stream owned by the core.
 *
 * <p>Never a global: a node gets its own {@link Seed} derived from the base seed and its
 * path in the tree, so adding or removing a sibling cannot shift anyone else's noise.
 */
public record Seed(long value) {
	public Seed child(int index) {
		return new Seed(mix(value, index + 1L));
	}

	public Seed fork(long salt) {
		return new Seed(mix(value, salt));
	}

	public Rng rng() {
		return new Rng(value);
	}

	private static final long GOLDEN = 0x9E3779B97F4A7C15L;

	private static long mix(long seed, long salt) {
		long z = seed + GOLDEN * (salt + 1L);
		z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
		z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
		return z ^ (z >>> 31);
	}

	@Override
	public String toString() {
		return "Seed[" + Long.toHexString(value) + "]";
	}
}
