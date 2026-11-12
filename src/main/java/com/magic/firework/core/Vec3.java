package com.magic.firework.core;

import java.util.Objects;

/** Immutable double-precision vector. Core owns this type so it never depends on Minecraft. */
public final class Vec3 {
	public static final Vec3 ZERO = new Vec3(0.0, 0.0, 0.0);

	public final double x;
	public final double y;
	public final double z;

	public Vec3(double x, double y, double z) {
		this.x = x;
		this.y = y;
		this.z = z;
	}

	public Vec3 add(Vec3 other) {
		return new Vec3(x + other.x, y + other.y, z + other.z);
	}

	public Vec3 add(double dx, double dy, double dz) {
		return new Vec3(x + dx, y + dy, z + dz);
	}

	public Vec3 scale(double factor) {
		return new Vec3(x * factor, y * factor, z * factor);
	}

	public double lengthSqr() {
		return x * x + y * y + z * z;
	}

	public double length() {
		return Math.sqrt(lengthSqr());
	}

	public Vec3 normalize() {
		double length = length();
		return length < 1.0E-9 ? ZERO : scale(1.0 / length);
	}

	@Override
	public boolean equals(Object other) {
		return other instanceof Vec3 vec && vec.x == x && vec.y == y && vec.z == z;
	}

	@Override
	public int hashCode() {
		return Objects.hash(x, y, z);
	}

	@Override
	public String toString() {
		return "(" + x + ", " + y + ", " + z + ")";
	}
}
