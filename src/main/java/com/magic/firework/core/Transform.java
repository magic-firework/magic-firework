package com.magic.firework.core;

/**
 * Rigid transform with uniform scale: {@code v -> position + R * (scale * v)}.
 *
 * <p>Rotation is a 3x3 matrix, not Euler angles, so {@link #compose} is exact: nesting must
 * not drift, otherwise a figure would look different at different depths.
 */
public final class Transform {
	public static final Transform IDENTITY = new Transform(Vec3.ZERO, identity(), 1.0);

	private final Vec3 position;
	private final double[] rotation;
	private final double scale;

	private Transform(Vec3 position, double[] rotation, double scale) {
		this.position = position;
		this.rotation = rotation;
		this.scale = scale;
	}

	public static Transform of(Vec3 position, double rotX, double rotY, double rotZ, double scale) {
		return new Transform(position == null ? Vec3.ZERO : position, matrix(rotX, rotY, rotZ),
				scale > 0.0 ? scale : 1.0);
	}

	public static Transform translation(Vec3 offset) {
		return new Transform(offset == null ? Vec3.ZERO : offset, identity(), 1.0);
	}

	public static Transform rotation(double rotX, double rotY, double rotZ) {
		return new Transform(Vec3.ZERO, matrix(rotX, rotY, rotZ), 1.0);
	}

	/** Reflection across the selected local axes; used by {@link com.magic.firework.core.shape.Form.Mirror}. */
	public static Transform mirror(boolean x, boolean y, boolean z) {
		double sx = x ? -1.0 : 1.0;
		double sy = y ? -1.0 : 1.0;
		double sz = z ? -1.0 : 1.0;
		return new Transform(Vec3.ZERO, new double[] { sx, 0, 0, 0, sy, 0, 0, 0, sz }, 1.0);
	}

	/** This transform's local space applied to {@code child}: child first, then this. */
	public Transform compose(Transform child) {
		return new Transform(apply(child.position), multiply(rotation, child.rotation), scale * child.scale);
	}

	public Vec3 apply(Vec3 local) {
		return rotate(local).add(position);
	}

	/** Rotation + scale only (no translation). */
	public Vec3 rotate(Vec3 local) {
		double sx = local.x * scale;
		double sy = local.y * scale;
		double sz = local.z * scale;
		return new Vec3(
				rotation[0] * sx + rotation[1] * sy + rotation[2] * sz,
				rotation[3] * sx + rotation[4] * sy + rotation[5] * sz,
				rotation[6] * sx + rotation[7] * sy + rotation[8] * sz);
	}

	public Vec3 position() {
		return position;
	}

	public double scale() {
		return scale;
	}

	@Override
	public String toString() {
		return "Transform[" + position + ", scale=" + scale + "]";
	}

	private static double[] identity() {
		return new double[] { 1, 0, 0, 0, 1, 0, 0, 0, 1 };
	}

	/** {@code Ry * Rx * Rz} (radians). */
	private static double[] matrix(double rotX, double rotY, double rotZ) {
		double cx = Math.cos(rotX);
		double sx = Math.sin(rotX);
		double cy = Math.cos(rotY);
		double sy = Math.sin(rotY);
		double cz = Math.cos(rotZ);
		double sz = Math.sin(rotZ);

		double[] rz = { cz, -sz, 0, sz, cz, 0, 0, 0, 1 };
		double[] rx = { 1, 0, 0, 0, cx, -sx, 0, sx, cx };
		double[] ry = { cy, 0, sy, 0, 1, 0, -sy, 0, cy };
		return multiply(ry, multiply(rx, rz));
	}

	private static double[] multiply(double[] a, double[] b) {
		double[] out = new double[9];
		for (int row = 0; row < 3; row++) {
			for (int column = 0; column < 3; column++) {
				double sum = 0.0;
				for (int k = 0; k < 3; k++) {
					sum += a[row * 3 + k] * b[k * 3 + column];
				}
				out[row * 3 + column] = sum;
			}
		}
		return out;
	}
}
