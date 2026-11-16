package com.magic.firework.program;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

/**
 * One instruction. A firework is a list of these; see {@link Program}.
 *
 * <p>Ops never touch the world. They either mutate {@link ProgramState} (the program
 * counter, the call stack) or emit an {@link Effect} that the host applies. That is what
 * keeps the interpreter testable without a running game.
 *
 * <p>No op writes an attribute: the bag a rocket runs with is decided before launch (profile
 * defaults plus the item overlay), and time is {@link Instruction#at()}. A program describes
 * what happens when, never what it is made of.
 */
public sealed interface Op {
	/** Blocks nothing: time, not an await, is what orders a program. */
	record Launch(Direction direction, Vec3 vector, float speed) implements Op {
	}

	/** Fires the burst immediately. Parameters are read from the attributes. */
	record Burst() implements Op {
	}

	/** Runs another program in place: nested release without a new entity. */
	record Call(ResourceLocation program) implements Op {
	}

	/**
	 * Spawns independent child rockets that run {@code program}.
	 *
	 * <p>A child starts from its <em>own</em> profile attributes: the parent does not aim it
	 * and does not pass any attribute override, so its {@code launch} op and its
	 * {@code flight.direction.*} attributes decide the direction. {@code spread} is kept for
	 * data pack compatibility and no longer affects anything.
	 */
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
		VECTOR,
		/** Relative to the launcher: horizontal 0° is straight ahead, read from the angle attributes. */
		RELATIVE;

		public static Direction byName(String name) {
			if (name == null) {
				return UP;
			}
			return switch (name.toLowerCase(java.util.Locale.ROOT)) {
				case "look" -> LOOK;
				case "vector" -> VECTOR;
				case "relative" -> RELATIVE;
				default -> UP;
			};
		}
	}
}
