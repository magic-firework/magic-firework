package com.magic.firework.core.show;

/**
 * Turns every point of a stamp into a vanilla firework spark: the shape decides <em>where</em> sparks are
 * aimed, this record decides how they fly. Velocity, drag, gravity, lifetime, trail, flicker and fade
 * travel per point, not per show. {@code normalize} picks the emitter: a ball normalizes to a direction
 * and equalises speed ({@code createParticleBall}), a shape keeps the point's length so the outline survives.
 */
@SuppressWarnings("ArrayRecordComponent") // arrays are cloned in the compact constructor and in the accessors
public record Spark(
		double speed,
		double speedSpread,
		double directionSpread,
		int lifetime,
		int lifetimeSpread,
		boolean trail,
		boolean flicker,
		boolean flash,
		boolean normalize,
		boolean atOrigin,
		int[] colors,
		int[] fadeColors) {

	public static final double DEFAULT_SPEED = 0.25;
	public static final double DEFAULT_SPEED_SPREAD = 0.05;
	public static final double DEFAULT_DIRECTION_SPREAD = 0.5;
	public static final int DEFAULT_LIFETIME = 48;
	public static final int DEFAULT_LIFETIME_SPREAD = 12;
	public static final double MAX_SPEED = 16.0;
	public static final int MAX_LIFETIME = 4096;

	public Spark {
		speed = clamp(speed, 0.0, MAX_SPEED);
		speedSpread = clamp(speedSpread, 0.0, MAX_SPEED);
		directionSpread = clamp(directionSpread, 0.0, MAX_SPEED);
		lifetime = Math.max(1, Math.min(lifetime, MAX_LIFETIME));
		lifetimeSpread = Math.max(0, Math.min(lifetimeSpread, MAX_LIFETIME));
		colors = colors == null ? new int[0] : colors.clone();
		fadeColors = fadeColors == null ? new int[0] : fadeColors.clone();
	}

	/** The default is the small-ball spark vanilla creates for an unconfigured firework star. */
	public static Spark smallBall() {
		return new Spark(DEFAULT_SPEED, DEFAULT_SPEED_SPREAD, DEFAULT_DIRECTION_SPREAD,
				DEFAULT_LIFETIME, DEFAULT_LIFETIME_SPREAD, false, false, true, true, true,
				new int[0], new int[0]);
	}

	/** Fast path for the bake loop; the public accessors still return a defensive copy. */
	public int colorCount() {
		return colors.length;
	}

	public int colorAt(int index) {
		return colors[index];
	}

	public int fadeColorCount() {
		return fadeColors.length;
	}

	public int fadeColorAt(int index) {
		return fadeColors[index];
	}

	@Override
	public int[] colors() {
		return colors.clone();
	}

	@Override
	public int[] fadeColors() {
		return fadeColors.clone();
	}

	/** True when two baked plans of this spark would differ for different burst seeds. */
	public boolean seedSensitive() {
		return speedSpread > 0.0
				|| directionSpread > 0.0
				|| lifetimeSpread > 0
				|| colors.length > 1
				|| fadeColors.length > 1;
	}

	private static double clamp(double value, double min, double max) {
		if (!Double.isFinite(value)) {
			return min;
		}
		return Math.max(min, Math.min(value, max));
	}

	@Override
	public String toString() {
		return "Spark[speed=" + speed + ", life=" + lifetime + "+" + lifetimeSpread
				+ (trail ? ", trail" : "") + (flicker ? ", flicker" : "") + "]";
	}
}
