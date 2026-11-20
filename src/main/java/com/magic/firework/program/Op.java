package com.magic.firework.program;

import java.util.List;

import net.minecraft.resources.ResourceLocation;

/**
 * One instruction. A firework is a list of these; see {@link Program}. Ops never touch the world: they
 * mutate {@link ProgramState} (counter, call stack) or emit an {@link Effect} the host applies, which is
 * what keeps the interpreter testable without a running game. No op writes an attribute: the bag a
 * rocket runs with is decided before launch, and time is spent by {@link Wait}.
 */
public sealed interface Op {
	/**
	 * Sets the velocity somewhere the attributes decide: {@code flight.direction.yaw} and
	 * {@code flight.direction.pitch} aim the launch from the launcher's heading. There is no direction
	 * parameter because the bag already owns one, so a program can never disagree with the rocket it runs in.
	 */
	record Launch(float speed) implements Op {
	}

	/** Fires the burst immediately. Parameters are read from the attributes. */
	record Burst() implements Op {
	}

	/** Runs another profile's program in place: nested release without a new entity. */
	record Call(ResourceLocation profile) implements Op {
	}

	/**
	 * Spawns independent child rockets, one per {@link SpawnTarget}. A slot target takes the child the
	 * launching item carries at that index, a profile target names content directly; either way the child
	 * starts from its <em>own</em> attributes, so its {@code launch} op and {@code flight.direction.*} decide
	 * the direction. The target list is expanded at parse time, so {@code count} never reaches the interpreter.
	 */
	record Spawn(List<SpawnTarget> targets, int delayTicks) implements Op {
		public Spawn {
			targets = List.copyOf(targets);
			delayTicks = Math.max(0, delayTicks);
		}
	}

	record Sound(ResourceLocation id, float volume, float pitch) implements Op {
	}

	record Light(int ticks) implements Op {
	}

	/** Ends the current program; the entity dies when the outermost program ends. */
	record End() implements Op {
	}

	/**
	 * Consumes ticks: the next op runs later. This is the only way a program spends time. {@link Wait.Flight}
	 * spends the rocket's <b>flight time</b> (the attribute the crafting table edits, so the player owns that
	 * number); {@link Wait.Ticks} spends a literal count for a beat that is not about flight. A wait is per
	 * frame and saved with the program, so a reload resumes it instead of restarting or skipping ahead.
	 */
	sealed interface Wait extends Op {
		/** Spends the rocket's {@code flight.ticks}; read when the wait starts, not at launch. */
		record Flight() implements Wait {
		}

		/** Spends exactly {@code ticks}, independent of any attribute. */
		record Ticks(int ticks) implements Wait {
			public Ticks {
				ticks = Math.max(1, ticks);
			}
		}
	}

}
