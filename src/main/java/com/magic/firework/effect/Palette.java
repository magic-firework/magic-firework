package com.magic.firework.effect;


import org.joml.Vector3f;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;

/** Colour selection and particle option creation. Keeps shapes free of particle types. */
public final class Palette {
	private Palette() {
	}

	public static int pick(int[] colors, RandomSource random) {
		if (colors == null || colors.length == 0) {
			return 0xFFFFFF;
		}
		return colors[random.nextInt(colors.length)];
	}

	public static Vector3f toVector(int rgb) {
		return new Vector3f(
				((rgb >> 16) & 0xFF) / 255.0F,
				((rgb >> 8) & 0xFF) / 255.0F,
				(rgb & 0xFF) / 255.0F);
	}

	public static ParticleOptions particleFor(int[] colors, float scale, RandomSource random) {
		if (colors == null || colors.length == 0) {
			return ParticleTypes.FIREWORK;
		}
		return new DustParticleOptions(toVector(pick(colors, random)), scale);
	}
}
