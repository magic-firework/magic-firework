package com.magic.firework.core.eval;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import com.magic.firework.core.Transform;
import com.magic.firework.core.Vec3;

/**
 * The baked result of a pure show: offsets from the burst origin plus a per-point time, ordered so a
 * player can walk the stream with a single cursor. A point with a lifetime is a vanilla firework
 * spark the client spawns once and lets vanilla physics carry; a point without one is a static puff
 * emitted at its baked position.
 */
public final class FrameStream {
	private final List<Point> points;
	private final List<Flash> flashes;

	private FrameStream(List<Point> points, List<Flash> flashes) {
		this.points = points;
		this.flashes = flashes;
	}

	public static FrameStream of(List<Point> raw) {
		return of(raw, List.of());
	}

	/** Stable sort by time: equal timestamps keep their tree order, so the result is reproducible. */
	public static FrameStream of(List<Point> raw, List<Flash> rawFlashes) {
		if ((raw == null || raw.isEmpty()) && (rawFlashes == null || rawFlashes.isEmpty())) {
			return empty();
		}
		List<Point> sorted = raw == null ? List.of() : new ArrayList<>(raw);
		sorted.sort(Comparator.comparingDouble(Point::time));
		List<Flash> sortedFlashes = rawFlashes == null ? List.of() : new ArrayList<>(rawFlashes);
		sortedFlashes.sort(Comparator.comparingDouble(Flash::time));
		return new FrameStream(List.copyOf(sorted), List.copyOf(sortedFlashes));
	}

	public static FrameStream empty() {
		return new FrameStream(List.of(), List.of());
	}

	public int size() {
		return points.size();
	}

	public boolean isEmpty() {
		return points.isEmpty() && flashes.isEmpty();
	}

	public Point point(int index) {
		return points.get(index);
	}

	public List<Point> points() {
		return points;
	}

	public Flash flash(int index) {
		return flashes.get(index);
	}

	public List<Flash> flashes() {
		return flashes;
	}

	public int flashCount() {
		return flashes.size();
	}

	public boolean isTimeOrdered() {
		for (int i = 1; i < points.size(); i++) {
			if (points.get(i).time() < points.get(i - 1).time()) {
				return false;
			}
		}
		for (int i = 1; i < flashes.size(); i++) {
			if (flashes.get(i).time() < flashes.get(i - 1).time()) {
				return false;
			}
		}
		return true;
	}

	/**
	 * One baked particle: offset in blocks, tick offset, ARGB colour, size multiplier.
	 * {@code lifetime <= 0} means a static puff; when positive the point is a spark replayed with a
	 * vanilla firework particle, {@code velocity} as its initial motion and {@code fadeArgb} (alpha 0 =
	 * none) as the colour it fades toward.
	 */
	public record Point(Vec3 offset, float time, int argb, float size, Vec3 velocity,
			float lifetime, int fadeArgb, boolean trail, boolean flicker) {
		public Point {
			offset = offset == null ? Vec3.ZERO : offset;
			size = size <= 0.0F ? 1.0F : size;
			velocity = velocity == null ? Vec3.ZERO : velocity;
			lifetime = Math.max(0.0F, lifetime);
		}

		/** A static puff, the shape of every point before sparks existed. */
		public Point(Vec3 offset, float time, int argb, float size) {
			this(offset, time, argb, size, Vec3.ZERO, 0.0F, 0, false, false);
		}

		/** True when the client should use the vanilla firework particle instead of a dust puff. */
		public boolean hasMotion() {
			return lifetime > 0.0F || velocity.lengthSqr() > 0.0;
		}
	}

	/** The vanilla flash overlay: one short-lived tinted flash at the burst origin. */
	public record Flash(Vec3 offset, float time, int argb) {
		public Flash {
			offset = offset == null ? Vec3.ZERO : offset;
		}
	}

	/**
	 * Apply a per-burst adjustment to an already baked stream. A show is baked once and shared, so
	 * colour, scale and rotation are applied here, leaving time and the cached plan untouched.
	 * @param transform rotation and scale around the burst origin, or {@code null} to keep the geometry
	 * @param argb      colour override for every point, or {@code null} to keep the show's palette
	 */
	public FrameStream transformed(Transform transform, Integer argb) {
		if (isEmpty() || (transform == null && argb == null)) {
			return this;
		}
		float scale = transform == null ? 1.0F : (float) transform.scale();
		List<Point> adjusted = new ArrayList<>(points.size());
		for (Point point : points) {
			Vec3 offset = transform == null ? point.offset() : transform.apply(point.offset());
			Vec3 velocity = transform == null ? point.velocity() : transform.rotate(point.velocity());
			int color = argb == null ? point.argb() : argb;
			adjusted.add(new Point(offset, point.time(), color, point.size() * scale, velocity,
					point.lifetime(), point.fadeArgb(), point.trail(), point.flicker()));
		}
		List<Flash> adjustedFlashes = new ArrayList<>(flashes.size());
		for (Flash flash : flashes) {
			Vec3 offset = transform == null ? flash.offset() : transform.apply(flash.offset());
			int color = argb == null ? flash.argb() : argb;
			adjustedFlashes.add(new Flash(offset, flash.time(), color));
		}
		return new FrameStream(List.copyOf(adjusted), List.copyOf(adjustedFlashes));
	}
}
