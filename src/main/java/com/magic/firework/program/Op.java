package com.magic.firework.program;

import com.magic.firework.attribute.AttributeBag;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

/**
 * One instruction. A firework is a list of these; see {@link Program}.
 *
 * <p>Ops never touch the world. They either mutate {@link ProgramState} (the program
 * counter, the local attribute overlay) or emit an {@link Effect} that the host applies.
 * That is what keeps the interpreter testable without a running game.
 */
public sealed interface Op {
	/** Sets the velocity. Direction {@code up}/{@code look}/{@code vector}. */
	record Launch(Direction direction, Vec3 vector, float speed) implements Op {
	}

	/** Blocks until the condition holds. */
	record Await(Cond cond) implements Op {
	}

	/** Merges attributes into the local overlay (hover = speed 0, gravity 0). */
	record Set(AttributeBag attributes) implements Op {
	}

	/** Fires the burst immediately. Parameters are read from the attributes. */
	record Burst() implements Op {
	}

	/** Runs another program in place: nested release without a new entity. */
	record Call(ResourceLocation program) implements Op {
	}

	/** Spawns independent child rockets that run {@code program}. */
	record Spawn(ResourceLocation program, int count, float spread, int delayTicks) implements Op {
	}

	record Sound(ResourceLocation id, float volume, float pitch) implements Op {
	}

	record Light(int ticks) implements Op {
	}

	/** Ends the current program; the entity dies when the outermost program ends. */
	record End() implements Op {
	}

	enum Direction {
		UP,
		LOOK,
		VECTOR;

		public static Direction byName(String name) {
			if (name == null) {
				return UP;
			}
			return switch (name.toLowerCase(java.util.Locale.ROOT)) {
				case "look" -> LOOK;
				case "vector" -> VECTOR;
				default -> UP;
			};
		}
	}
}
