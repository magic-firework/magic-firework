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
import com.magic.firework.core.show.Spark;
import com.magic.firework.core.show.Stamp;

/**
 * Turns a {@link Show} into a {@link Plan} with one loop.
 *
 * <p>A stamp either bursts once or walks its path over {@code duration} ticks; each emitted
 * point is transformed by {@code place * path(u)}. Form trees are flattened lazily under the
 * {@link Meter}, so repetition is bounded by the budget rather than by the author.
 *
 * <p>A stamp with a {@link Spark} bakes vanilla firework particles instead of static puffs:
 * every point gets an initial velocity, a lifetime, an optional fade colour and the trail /
 * flicker flags. The client then lets the real vanilla particle tick, so the show does not
 * have to bake every position of every spark.
 *
 * <p>Deterministic by construction: the same {@code (show, seed, budget)} gives the same
 * plan, byte for byte, because every random stream is derived from an explicit seed path.
 */
public final class Evaluate {
	private static final long JITTER_SALT = 0x5EED5EEDL;
	private static final long SPARK_SALT = 0x5A4B5A4BL;

	private Evaluate() {
	}

	public static Plan evaluate(Show show, Seed seed, Budget budget) {
		if (show == null || seed == null || show.stamps().isEmpty()) {
			return Plan.empty();
		}
		Meter meter = new Meter(budget);
		List<FrameStream.Point> points = new ArrayList<>();
		List<FrameStream.Flash> flashes = new ArrayList<>();
		List<Plan.ScheduledEvent> events = new ArrayList<>();
		List<Stamp> stamps = show.stamps();
		int limit = Math.min(stamps.size(), ContentLimits.MAX_STAMPS);
		int steps = 0;
		boolean stepsTruncated = false;
		for (int index = 0; index < limit; index++) {
			Stamp stamp = stamps.get(index);
			Seed stampSeed = seed.child(index);
			int stepCount = stamp.duration() <= 0 ? 1 : stamp.duration();
			for (int step = 0; step < stepCount; step++) {
				if (meter.exhausted() || ++steps > ContentLimits.MAX_STEPS_PER_SHOW) {
					stepsTruncated = true;
					break;
				}
				double u = stepCount == 1 ? 0.0 : (step + 1) / (double) stepCount;
				Transform pen = stamp.place().compose(Transform.translation(stamp.path().at(u)));
				int tick = stamp.at() + step;
				emit(stamp, pen, tick, stampSeed.child(step), meter, show, points);
			}
			collectFlash(stamp, show, flashes);
			collectEvents(stamp, meter, events);
		}
		return new Plan(FrameStream.of(points, flashes), events, stepsTruncated || meter.truncated());
	}

	private static void emit(Stamp stamp, Transform pen, int tick, Seed pointSeed, Meter meter, Show show,
			List<FrameStream.Point> points) {
		double thickness = stamp.thickness();
		double jitter = stamp.jitter();
		Rng noise = thickness > 0.0 || jitter > 0.0 ? pointSeed.fork(JITTER_SALT).rng() : null;
		Spark spark = stamp.spark();
		Rng sparkNoise = spark == null ? null : pointSeed.fork(SPARK_SALT).rng();
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
			if (spark == null) {
				points.add(new FrameStream.Point(pen.apply(point), tick, show.color(colorIndex),
						(float) pointSize));
				return;
			}
			points.add(spark(spark, sparkNoise, pen, tick, point, colorIndex, pointSize, show));
		});
	}

	/** Bake one point as a vanilla spark: position at the burst origin, velocity along the point. */
	@SuppressWarnings("IdentityBinaryExpression") // the three nextDouble() pairs are independent draws
	private static FrameStream.Point spark(Spark spark, Rng random, Transform pen, int tick,
			Vec3 point, int colorIndex, double pointSize, Show show) {
		Vec3 direction = point;
		if (spark.directionSpread() > 0.0) {
			direction = direction.add(
					(random.nextDouble() - random.nextDouble()) * spark.directionSpread(),
					(random.nextDouble() - random.nextDouble()) * spark.directionSpread(),
					(random.nextDouble() - random.nextDouble()) * spark.directionSpread());
		}
		double speed = spark.speed();
		double speedNoise = spark.speedSpread() > 0.0 ? random.nextGaussian() * spark.speedSpread() : 0.0;
		Vec3 velocity;
		if (spark.normalize()) {
			// Vanilla createParticleBall: |v| = 1 / (|point| / speed + gaussian * spread).
			double length = direction.length();
			if (speed <= 1.0E-9 || length <= 1.0E-9) {
				velocity = Vec3.ZERO;
			} else {
				double divisor = length / speed + speedNoise;
				velocity = Math.abs(divisor) <= 1.0E-9 ? Vec3.ZERO : direction.scale(1.0 / divisor);
			}
		} else {
			// Vanilla createParticleShape: the point is already the velocity shape.
			velocity = direction.scale(speed + speedNoise);
		}
		int lifetime = spark.lifetime()
				+ (spark.lifetimeSpread() > 0 ? random.nextInt(spark.lifetimeSpread()) : 0);
		int color = spark.colorCount() == 0
				? colorIndex
				: spark.colorAt(random.nextInt(spark.colorCount()));
		int fade = spark.fadeColorCount() == 0
				? 0
				: show.color(spark.fadeColorAt(random.nextInt(spark.fadeColorCount())));
		Vec3 offset = spark.atOrigin() ? pen.position() : pen.apply(point);
		return new FrameStream.Point(offset, tick, show.color(color), (float) pointSize,
				pen.rotate(velocity), lifetime, fade, spark.trail(), spark.flicker());
	}

	/** One flash per flashing stamp, at the tick the stamp starts and at its origin. */
	private static void collectFlash(Stamp stamp, Show show, List<FrameStream.Flash> flashes) {
		Spark spark = stamp.spark();
		if (spark == null || !spark.flash()) {
			return;
		}
		Transform pen = stamp.place().compose(Transform.translation(stamp.path().at(0.0)));
		int color = spark.colorCount() == 0 ? 0 : spark.colorAt(0);
		flashes.add(new FrameStream.Flash(pen.position(), stamp.at(), show.color(color)));
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
