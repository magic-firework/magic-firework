package com.magic.firework.program;

import java.util.List;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

/**
 * Something the host must do. The interpreter only produces these; the entity (server)
 * or the simulator applies them. Adding a capability means adding an effect record and
 * one case in the host, never touching the program format.
 */
public sealed interface Effect {
	record SetVelocity(Vec3 velocity) implements Effect {
	}

	record Burst() implements Effect {
	}

	record SpawnChild(ResourceLocation program, float spread, int delayTicks) implements Effect {
	}

	record PlaySound(ResourceLocation id, float volume, float pitch) implements Effect {
	}

	record Light(int ticks) implements Effect {
	}

	/** Result of one interpreter step: ordered effects plus "the program is over". */
	record TickResult(List<Effect> effects, boolean finished) {
	}
}
