package com.magic.firework.registry;

import com.magic.firework.MagicFirework;

import net.fabricmc.fabric.api.particle.v1.FabricParticleTypes;

import net.minecraft.core.Registry;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;

/**
 * Particle types this mod owns. The baked show needs a spark whose colour, fade colour, lifetime, trail
 * and flicker can be set after creation; vanilla's spark class is package-private, so the mod registers
 * its own type and client provider instead of reaching into a vanilla private class.
 */
public final class ModParticles {
	public static final SimpleParticleType FIREWORK_SPARK = FabricParticleTypes.simple();

	private ModParticles() {
	}

	public static void init() {
		Registry.register(BuiltInRegistries.PARTICLE_TYPE, MagicFirework.id("firework_spark"),
				FIREWORK_SPARK);
	}
}
