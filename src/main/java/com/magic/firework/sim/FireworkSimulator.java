package com.magic.firework.sim;

import java.util.ArrayList;
import java.util.List;

import com.magic.firework.attribute.AttributeBag;
import com.magic.firework.program.Effect;
import com.magic.firework.physics.FlightStep;
import com.magic.firework.program.Interpreter;
import com.magic.firework.program.InterpreterContext;
import com.magic.firework.program.Program;
import com.magic.firework.program.ProgramLookup;
import com.magic.firework.program.ProgramState;

import net.minecraft.world.phys.Vec3;

/**
 * Runs a program without a world: same interpreter, same {@link FlightStep} as the
 * entity, no packets and no particles. This is the M0 acceptance test, the
 * {@code /magicfirework sim} command, and the reason the data layer must never import
 * {@code Entity} or {@code Level}.
 */
public final class FireworkSimulator {
	private FireworkSimulator() {
	}

	public record Sample(int age, Vec3 position, Vec3 velocity, List<Effect> effects) {
	}

	public record Result(List<Sample> samples, int bursts, int children, boolean finished, int liveTicks) {
		public Vec3 lastPosition() {
			return samples.isEmpty() ? Vec3.ZERO : samples.get(samples.size() - 1).position();
		}

		public double maxHeight() {
			double max = Double.NEGATIVE_INFINITY;
			for (Sample sample : samples) {
				max = Math.max(max, sample.position().y);
			}
			return max == Double.NEGATIVE_INFINITY ? 0.0 : max;
		}
	}

	/**
	 * @param groundY  position at or below which the rocket is considered to have hit
	 *                 the ground (use {@link Double#NEGATIVE_INFINITY} to disable)
	 */
	public static Result run(Program root, ProgramLookup lookup, AttributeBag base, Vec3 start,
			Vec3 lookDirection, int maxTicks, double groundY) {
		ProgramState state = ProgramState.start(root, base, 0L, 0);
		Vec3 position = start;
		Vec3 velocity = Vec3.ZERO;
		boolean collided = false;
		List<Sample> samples = new ArrayList<>();
		int bursts = 0;
		int children = 0;
		int ticks = 0;

		for (int tick = 0; tick < maxTicks; tick++) {
			state.tickAge();
			ticks++;
			AttributeBag bag = state.bag();
			velocity = FlightStep.step(bag, velocity);

			InterpreterContext ctx = new InterpreterContext(lookup, position, lookDirection, collided);
			Effect.TickResult result = Interpreter.tick(state, ctx);
			for (Effect effect : result.effects()) {
				if (effect instanceof Effect.SetVelocity set) {
					velocity = set.velocity();
				} else if (effect instanceof Effect.Burst) {
					bursts++;
				} else if (effect instanceof Effect.SpawnChild) {
					children++;
				}
			}
			samples.add(new Sample(state.age(), position, velocity, result.effects()));

			position = position.add(velocity);
			if (collided) {
				position = new Vec3(position.x, Math.max(position.y, groundY), position.z);
				velocity = Vec3.ZERO;
			} else if (position.y <= groundY) {
				position = new Vec3(position.x, groundY, position.z);
				velocity = Vec3.ZERO;
				collided = true;
			}

			if (result.finished()) {
				return new Result(List.copyOf(samples), bursts, children, true, ticks);
			}
		}
		return new Result(List.copyOf(samples), bursts, children, false, ticks);
	}
}
