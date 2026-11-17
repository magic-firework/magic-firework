package com.magic.firework.core.eval;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import com.magic.firework.core.Transform;
import com.magic.firework.core.Vec3;

/**
 * The baked result of a pure show: offsets from the burst origin plus a per-point time.
 *
 * <p>Points are ordered by time so a player can walk the stream with a single cursor.
 */
public final class FrameStream {
	private final List<Point> points;

	private FrameStream(List<Point> points) {
		this.points = points;
	}

	/** Stable sort by time: equal timestamps keep their tree order, so the result is reproducible. */
	public static FrameStream of(List<Point> raw) {
		if (raw == null || raw.isEmpty()) {
			return new FrameStream(List.of());
		}
		List<Point> sorted = new ArrayList<>(raw);
		sorted.sort(Comparator.comparingDouble(Point::time));
		return new FrameStream(List.copyOf(sorted));
	}

	public static FrameStream empty() {
		return new FrameStream(List.of());
	}

	public int size() {
		return points.size();
	}

	public boolean isEmpty() {
		return points.isEmpty();
	}

	public Point point(int index) {
		return points.get(index);
	}

	public List<Point> points() {
		return points;
	}

	public boolean isTimeOrdered() {
		for (int i = 1; i < points.size(); i++) {
			if (points.get(i).time() < points.get(i - 1).time()) {
				return false;
			}
		}
		return true;
	}

	/** One baked particle: offset in blocks, tick offset, ARGB colour, size multiplier. */
	public record Point(Vec3 offset, float time, int argb, float size) {
		public Point {
			offset = offset == null ? Vec3.ZERO : offset;
			size = size <= 0.0F ? 1.0F : size;
		}
	}

	/**
	 * Apply a per-burst adjustment to an already baked stream.
	 *
	 * <p>A show is baked once and shared by every burst that uses it, so whatever differs per
	 * rocket (colour, scale, rotation) is applied here instead of inside the bake. Time is
	 * untouched, so the stream stays ordered and the cached plan is never mutated.
	 *
	 * @param transform rotation and scale around the burst origin, or {@code null} to keep the geometry
	 * @param argb      colour override for every point, or {@code null} to keep the show's palette
	 */
	public FrameStream transformed(Transform transform, Integer argb) {
		if (points.isEmpty() || (transform == null && argb == null)) {
			return this;
		}
		float scale = transform == null ? 1.0F : (float) transform.scale();
		List<Point> adjusted = new ArrayList<>(points.size());
		for (Point point : points) {
			Vec3 offset = transform == null ? point.offset() : transform.apply(point.offset());
			int color = argb == null ? point.argb() : argb;
			adjusted.add(new Point(offset, point.time(), color, point.size() * scale));
		}
		return new FrameStream(List.copyOf(adjusted));
	}
}
