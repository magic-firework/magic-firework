package com.magic.firework.core.eval;

import java.util.ArrayList;
import java.util.List;

import com.magic.firework.core.Budget;
import com.magic.firework.core.ContentLimits;
import com.magic.firework.core.Meter;
import com.magic.firework.core.Rng;
import com.magic.firework.core.Seed;
import com.magic.firework.core.Transform;
import com.magic.firework.core.Vec3;
import com.magic.firework.core.show.Show;
import com.magic.firework.core.show.Stamp;

/**
 * Turns a {@link Show} into a {@link Plan} with one loop.
 *
 * <p>A stamp either bursts once or walks its path over {@code duration} ticks; each emitted
 * point is transformed by {@code place * path(u)}. Form trees are flattened lazily under the
 * {@link Meter}, so repetition is bounded by the budget rather than by the author.
 *
 * <p>Deterministic by construction: the same {@code (show, seed, budget)} gives the same
 * plan, byte for byte, because every random stream is derived from an explicit seed path.
 */
public final class Evaluate {
	private static final long JITTER_SALT = 0x5EED5EEDL;

	private Evaluate() {
	}

	public static Plan evaluate(Show show, Seed seed, Budget budget) {
		if (show == null || seed == null || show.stamps().isEmpty()) {
			return Plan.empty();
		}
		Meter meter = new Meter(budget);
		List<FrameStream.Point> points = new ArrayList<>();
		List<Plan.ScheduledEvent> events = new ArrayList<>();
		List<Stamp> stamps = show.stamps();
		int limit = Math.min(stamps.size(), ContentLimits.MAX_STAMPS);
		int steps = 0;
		for (int index = 0; index < limit; index++) {
			Stamp stamp = stamps.get(index);
			Seed stampSeed = seed.child(index);
			int stepCount = stamp.duration() <= 0 ? 1 : stamp.duration();
			for (int step = 0; step < stepCount; step++) {
				if (meter.exhausted() || ++steps > ContentLimits.MAX_STEPS_PER_SHOW) {
					break;
				}
				double u = stepCount == 1 ? 0.0 : (step + 1) / (double) stepCount;
				Transform pen = stamp.place().compose(Transform.translation(stamp.path().at(u)));
				int tick = stamp.at() + step;
				emit(stamp, pen, tick, stampSeed.child(step), meter, show, points);
			}
			collectEvents(stamp, meter, events);
		}
		return new Plan(FrameStream.of(points), events);
	}

	private static void emit(Stamp stamp, Transform pen, int tick, Seed pointSeed, Meter meter, Show show,
			List<FrameStream.Point> points) {
		double thickness = stamp.thickness();
		double jitter = stamp.jitter();
		Rng noise = thickness > 0.0 || jitter > 0.0 ? pointSeed.fork(JITTER_SALT).rng() : null;
		stamp.form().emit(Transform.IDENTITY, 0, stamp.size(), meter, (unit, colorIndex, pointSize) -> {
			Vec3 point = unit;
			if (noise != null) {
				if (thickness > 0.0) {
					point = point.add(noise.nextGaussian() * thickness,
							noise.nextGaussian() * thickness,
							noise.nextGaussian() * thickness);
				}
				if (jitter > 0.0) {
					point = point.add((noise.nextDouble() - 0.5) * jitter,
							(noise.nextDouble() - 0.5) * jitter,
							(noise.nextDouble() - 0.5) * jitter);
				}
			}
			points.add(new FrameStream.Point(pen.apply(point), tick, show.color(colorIndex), (float) pointSize));
		});
	}

	private static void collectEvents(Stamp stamp, Meter meter, List<Plan.ScheduledEvent> events) {
		if (stamp.events().isEmpty()) {
			return;
		}
		Vec3 at = stamp.place().compose(Transform.translation(stamp.path().at(0.0))).position();
		for (var spec : stamp.events()) {
			if (!meter.takeEvent()) {
				return;
			}
			events.add(new Plan.ScheduledEvent(spec.kind(), stamp.at() + spec.tick(), at,
					spec.target(), spec.a(), spec.b()));
		}
	}
}
