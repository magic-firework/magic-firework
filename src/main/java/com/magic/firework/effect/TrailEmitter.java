package com.magic.firework.effect;

import com.magic.firework.attribute.AttributeBag;
import com.magic.firework.attribute.Attributes;

import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Client-side ascent trail. Derived entirely from the synced attributes, so the trail
 * costs no packets and stays in sync with the server's idea of the profile.
 */
public final class TrailEmitter {
	private TrailEmitter() {
	}

	public static void trail(Level level, Vec3 position, AttributeBag bag, long seed, int age) {
		if (!Attributes.TRAIL.get(bag)) {
			return;
		}
		int interval = Attributes.TRAIL_INTERVAL.get(bag);
		if (age % interval != 0) {
			return;
		}
		RandomSource random = RandomSource.create(seed + age);
		int[] colors = bag.intArray(Attributes.TRAIL_COLORS);
		float scale = Attributes.TRAIL_SCALE.get(bag);
		ParticleOptions options = Palette.particleFor(colors, scale, random);
		level.addParticle(options,
				position.x + (random.nextDouble() - 0.5) * 0.1,
				position.y + (random.nextDouble() - 0.5) * 0.1,
				position.z + (random.nextDouble() - 0.5) * 0.1,
				0.0, 0.0, 0.0);
	}
}
