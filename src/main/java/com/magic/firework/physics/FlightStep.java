package com.magic.firework.physics;

import com.magic.firework.config.FireworkLimits;

import net.minecraft.world.phys.Vec3;

/**
 * The one piece of physics the entity and the headless simulator must share, otherwise
 * a simulated test would not describe what actually happens in game.
 *
 * <p>Two things happen every tick: the flight line is braked, then the hard speed ceiling
 * applies. Braking is a single force along whichever way the rocket is flying, so a straight shot
 * and an aimed one behave the same: the speed runs down to zero and stays there for whatever is
 * left of the flight time. Nothing ever pushes the rocket off its launch line, and it never turns
 * around — it hangs where it stopped until its program ends.
 */
public final class FlightStep {
	private final static double EPSILON = 1.0E-6;

	private FlightStep() {
	}

	/** Advance one tick: brake along the flight line, then apply the speed ceiling. */
	public static Vec3 step(Vec3 velocity) {
		Vec3 next = slower(velocity);
		double maxSpeed = FireworkLimits.maxSpeed;
		double length = next.length();
		if (length > maxSpeed && length > EPSILON) {
			return next.scale(maxSpeed / length);
		}
		return next;
	}

	/**
	 * One tick of braking: {@link FireworkLimits#deceleration} comes off the <em>speed</em>, never
	 * off a single axis, so only the magnitude shrinks and the heading is untouched. Taking it from
	 * the whole vector is what keeps a diagonal shot flying straight instead of bending toward its
	 * slower axis; stopping at zero instead of overshooting is what lets the rocket hover.
	 */
	private static Vec3 slower(Vec3 velocity) {
		double speed = velocity.length();
		if (speed <= FireworkLimits.deceleration) {
			return Vec3.ZERO;
		}
		return velocity.scale((speed - FireworkLimits.deceleration) / speed);
	}
}
