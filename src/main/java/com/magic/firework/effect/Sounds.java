package com.magic.firework.effect;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public final class Sounds {
	private Sounds() {
	}

	public static void play(Level level, Vec3 position, ResourceLocation soundId, float volume, float pitch) {
		if (soundId == null) {
			return;
		}
		SoundEvent event = BuiltInRegistries.SOUND_EVENT.get(soundId);
		if (event == null) {
			return;
		}
		level.playSound(null, position.x, position.y, position.z, event, SoundSource.NEUTRAL, volume, pitch);
	}
}
