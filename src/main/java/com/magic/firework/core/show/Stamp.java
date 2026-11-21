package com.magic.firework.core.show;

import java.util.List;

import com.magic.firework.core.Transform;
import com.magic.firework.core.path.Path;
import com.magic.firework.core.shape.Form;

/**
 * One instance of a form: when, how long, how it moves, and how it is brushed. The shape lives in a
 * {@link Form} tree; a stamp only says how that tree is placed and when it appears, so the bottom-level
 * loop stays a loop. {@link #spark()} is optional: without it every point is a static puff, with it each
 * point becomes a vanilla spark carrying its own velocity and lifetime.
 */
public record Stamp(
		Form form,
		int at,
		int duration,
		Path path,
		Transform place,
		double size,
		double thickness,
		double jitter,
		List<EventSpec> events,
		Spark spark) {

	public Stamp {
		if (form == null) {
			throw new IllegalArgumentException("stamp needs a form");
		}
		at = Math.max(0, Math.min(at, 1_000_000));
		duration = Math.max(0, Math.min(duration, 4096));
		path = path == null ? new Path.None() : path;
		place = place == null ? Transform.IDENTITY : place;
		size = size <= 0.0 ? 1.0 : Math.min(size, 64.0);
		thickness = Math.max(0.0, Math.min(thickness, 16.0));
		jitter = Math.max(0.0, Math.min(jitter, 16.0));
		events = events == null ? List.of() : List.copyOf(events);
	}

	/** A static stamp, kept for callers that predate {@link Spark}. */
	public Stamp(Form form, int at, int duration, Path path, Transform place, double size,
			double thickness, double jitter, List<EventSpec> events) {
		this(form, at, duration, path, place, size, thickness, jitter, events, null);
	}

	public static Stamp of(Form form) {
		return new Stamp(form, 0, 0, null, null, 1.0, 0.0, 0.0, null, null);
	}

	@Override
	public String toString() {
		return "Stamp[" + form + ", at=" + at + ", duration=" + duration
				+ (spark == null ? "" : ", " + spark) + "]";
	}
}
