package com.magic.firework.physics;

import com.magic.firework.attribute.AttributeBag;
import com.magic.firework.attribute.Attributes;

import net.minecraft.world.phys.Vec3;

/**
 * The one piece of physics the entity and the headless simulator must share, otherwise
 * a simulated test would not describe what actually happens in game.
 */
public final class FlightStep {
	private final static double EPSILON = 1.0E-6;

	private FlightStep() {
	}

	/** Advance the velocity by one tick: drag, gravity, then the speed cap. */
	public static Vec3 step(AttributeBag bag, Vec3 velocity) {
		double drag = Attributes.DRAG.get(bag);
		double gravity = Attributes.GRAVITY.get(bag);
		Vec3 next = velocity.scale(drag).add(0.0, -gravity, 0.0);

		// The cap is also the hover switch: max_speed 0 freezes the rocket in place.
		double maxSpeed = Attributes.MAX_SPEED.get(bag);
		double length = next.length();
		if (length > maxSpeed && length > EPSILON) {
			next = next.scale(maxSpeed / length);
		}
		return next;
	}
}
