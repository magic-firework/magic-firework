package com.magic.firework.core;

/** SplitMix64 based random stream: no platform dependency, identical on both sides. */
public final class Rng {
	private long state;

	public Rng(long seed) {
		this.state = seed;
	}

	public long nextLong() {
		state += 0x9E3779B97F4A7C15L;
		long z = state;
		z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
		z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
		return z ^ (z >>> 31);
	}

	/** Uniform in {@code [0, 1)}. */
	public double nextDouble() {
		return (nextLong() >>> 11) * 0x1.0p-53;
	}

	/** Standard normal (Box-Muller, one sample per call for simplicity). */
	public double nextGaussian() {
		double u1 = Math.max(1.0E-12, nextDouble());
		double u2 = nextDouble();
		return Math.sqrt(-2.0 * Math.log(u1)) * Math.cos(2.0 * Math.PI * u2);
	}
}
