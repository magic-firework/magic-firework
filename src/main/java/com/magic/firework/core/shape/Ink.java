package com.magic.firework.core.shape;


import com.magic.firework.core.Id;
import com.magic.firework.core.Vec3;
import com.magic.firework.core.path.Path;

/**
 * A point-cloud shape in unit space, loaded from {@code inks/*.json}.
 *
 * <p>An ink is the leaf of a {@link Form} tree: composition (groups, rings, arrays,
 * mirrors, colour and scale) lives in {@code Form}, so a leaf stays a plain cloud that can
 * be inspected and edited without code.
 */
public final class Ink {
	private final Id id;
	private final Vec3[] points;
	private final int[] colors;
	private final double[] sizes;

	private Ink(Id id, Vec3[] points, int[] colors, double[] sizes) {
		this.id = id;
		this.points = points;
		this.colors = colors;
		this.sizes = sizes;
	}

	public static Ink of(Id id, Vec3[] points, int[] colors, double[] sizes) {
		if (id == null) {
			throw new IllegalArgumentException("ink needs an id");
		}
		if (points == null || points.length == 0) {
			throw new IllegalArgumentException("ink " + id + " has no points");
		}
		int count = points.length;
		if (colors != null && colors.length != 0 && colors.length != count) {
			throw new IllegalArgumentException("ink " + id + ": colour array must be empty or " + count + " long");
		}
		if (sizes != null && sizes.length != 0 && sizes.length != count) {
			throw new IllegalArgumentException("ink " + id + ": size array must be empty or " + count + " long");
		}
		return new Ink(id, points.clone(), colors == null ? new int[0] : colors.clone(),
				sizes == null ? new double[0] : sizes.clone());
	}

	/** One point brush: the atom of every figure. */
	public static Ink dot(Id id, double size) {
		return of(id, new Vec3[] { Vec3.ZERO }, new int[0], new double[] { size });
	}

	/** Sample a pen path into a static ink (also used for inline {@code path} shapes). */
	public static Ink path(Id id, Path path, int samples, double size) {
		int n = Math.max(2, Math.min(samples, 4096));
		Vec3[] points = new Vec3[n];
		double[] sizes = new double[n];
		for (int i = 0; i < n; i++) {
			points[i] = path.at(i / (double) (n - 1));
			sizes[i] = size;
		}
		return of(id, points, new int[0], sizes);
	}

	public Id id() {
		return id;
	}

	public int points() {
		return points.length;
	}

	public Vec3 point(int index) {
		return points[index];
	}

	public int color(int index) {
		return colors.length == 0 ? 0 : colors[index];
	}

	public double size(int index) {
		return sizes.length == 0 ? 1.0 : sizes[index];
	}

	/** Static content: the count is exactly the number of authored points. */
	public int estimatePoints() {
		return points.length;
	}

	/**
	 * Emit at most the authored points. A stamp asking for more is capped instead of
	 * duplicating points (there is no random densify jitter any more).
	 */
	public void emit(Emit ctx, Sink sink) {
		int emit = Math.min(ctx.points(), points.length);
		if (emit <= 0) {
			return;
		}
		for (int i = 0; i < emit; i++) {
			int index = (int) ((long) i * points.length / emit);
			sink.point(points[index], ctx.colorBase() + color(index), size(index));
		}
	}

	public record Emit(int colorBase, int points) {
		public Emit {
			points = Math.max(0, points);
		}
	}

	public interface Sink {
		void point(Vec3 unitPoint, int colorIndex, double size);
	}

	@Override
	public String toString() {
		return "Ink[" + id + ", " + points.length + " points]";
	}
}
