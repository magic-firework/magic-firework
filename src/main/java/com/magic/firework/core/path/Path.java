package com.magic.firework.core.path;

import com.magic.firework.core.Vec3;

/**
 * The curve the pen travels, in the stamp's local space. Pure geometry: no time, no world.
 *
 * <p>{@code at(0)} is where the stroke starts; {@code at(1)} is where it ends.
 */
public sealed interface Path {
	static double clamp01(double u) {
		return u < 0.0 ? 0.0 : (u > 1.0 ? 1.0 : u);
	}

	Vec3 at(double u);

	default double length() {
		return 0.0;
	}

	/** The pen does not move. */
	record None() implements Path {
		@Override
		public Vec3 at(double u) {
			return Vec3.ZERO;
		}
	}

	/** Straight line along local +Y. */
	record Line(double length) implements Path {
		public Line {
			length = Math.max(0.0, length);
		}

		@Override
		public Vec3 at(double u) {
			return new Vec3(0.0, length * clamp01(u), 0.0);
		}

		@Override
		public double length() {
			return length;
		}
	}

	/** Circular arc in the local XY plane, leaving the origin along +X. */
	record Arc(double radius, double degrees) implements Path {
		public Arc {
			radius = Math.max(0.0, radius);
			degrees = Math.abs(degrees);
		}

		@Override
		public Vec3 at(double u) {
			double angle = Math.toRadians(degrees) * clamp01(u);
			return new Vec3(radius * Math.sin(angle), radius * (1.0 - Math.cos(angle)), 0.0);
		}

		@Override
		public double length() {
			return radius * Math.toRadians(degrees);
		}
	}
}
