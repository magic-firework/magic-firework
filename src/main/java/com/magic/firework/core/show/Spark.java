package com.magic.firework.core.show;

/**
 * Turns every point of a stamp into a vanilla firework spark.
 *
 * <p>The shape still decides <em>where the sparks are aimed</em>; this record decides how they
 * fly. Vanilla's firework particle uses a radial velocity, drag and gravity, a random lifetime,
 * an optional trail, a flicker flag and an optional fade colour. Those are all per-spark facts,
 * so they travel with the baked point instead of becoming a global show setting.
 *
 * <p>{@code normalize} chooses between the two vanilla emitters: a ball normalizes the emitted
 * point to a direction and gives every spark the same speed (vanilla {@code createParticleBall}),
 * while a shape keeps the point's own length so the outline survives (vanilla
 * {@code createParticleShape}).
 */
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
