package com.magic.firework.program;

import java.util.ArrayList;
import java.util.List;

import com.magic.firework.attribute.AttributeBag;
import com.magic.firework.attribute.Attributes;
import com.magic.firework.config.FireworkLimits;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

/**
 * Runs one tick of a program.
 *
 * <p>Rules, in order:
 * <ol>
 *   <li>execute ops until the top frame is not due yet ({@link Instruction#at()} on the
 *       frame's own clock) or an {@link Instruction#when()} guard still fails;</li>
 *   <li>a frame whose program counter leaves the end is popped (implicit {@code end});</li>
 *   <li>at most {@link FireworkLimits#maxOpsPerTick} ops run per tick, so a pathological
 *       program cannot stall the server;</li>
 *   <li>ops only mutate {@link ProgramState} or append an {@link Effect}.</li>
 * </ol>
 *
 * <p>There is no wait op: time is a property of every instruction, and the program never
 * blocks on anything except its own schedule.
 */
public final class Interpreter {
	private Interpreter() {
	}

	public static Effect.TickResult tick(ProgramState state, InterpreterContext ctx) {
		if (state == null || !state.initialized() || state.stackSize() == 0) {
			return new Effect.TickResult(List.of(), true);
		}
		List<Effect> effects = new ArrayList<>();
		int guard = 0;
		while (!state.done() && guard++ < FireworkLimits.maxOpsPerTick) {
			Frame frame = state.current();
			if (frame == null) {
				break;
			}
			if (frame.pc() >= frame.program().size()) {
				state.pop();
				continue;
			}
			Instruction instruction = frame.program().instruction(frame.pc());
			int dueAt = frame.startAge() + instruction.resolveAt(state.bag());
			if (state.age() < dueAt) {
				// The frame's next entry is in the future: the whole rocket idles until then,
				// still flying. That is what "await = v=0 plus time" turned into.
				break;
			}
			if (instruction.guarded() && !instruction.when().satisfied(ctx.position(), ctx.collided())
					&& !instruction.when().expired(state.age() - dueAt)) {
				break;
			}
			frame.advance();
			apply(instruction.op(), state, ctx, effects);
		}
		return new Effect.TickResult(List.copyOf(effects), state.done());
	}

	// Java 17 target: an instanceof chain instead of a pattern switch.
	private static void apply(Op op, ProgramState state, InterpreterContext ctx, List<Effect> out) {
		AttributeBag bag = state.bag();
		if (op instanceof Op.Launch launch) {
			Op.Direction mode = launch.direction();
			Vec3 vector = launch.vector();
			// Attribute overrides win over the op: this is how a parent rocket aims
			// its split children without rewriting their program.
			if (bag.has(Attributes.DIRECTION_MODE.key())) {
				mode = Op.Direction.byName(Attributes.DIRECTION_MODE.get(bag));
				vector = new Vec3(
						Attributes.DIRECTION_X.get(bag),
						Attributes.DIRECTION_Y.get(bag),
						Attributes.DIRECTION_Z.get(bag));
			}
			Vec3 direction = switch (mode) {
				case UP -> new Vec3(0.0, 1.0, 0.0);
				case LOOK -> ctx.lookDirection();
				case VECTOR -> vector;
				case RELATIVE -> relative(bag, ctx.lookDirection());
			};
			if (direction.lengthSqr() < 1.0E-6) {
				direction = new Vec3(0.0, 1.0, 0.0);
			}
			// Attribute override wins when present, so an item overlay can change speed
			// without rewriting the profile's launch op.
			float speed = bag.has(Attributes.SPEED.key())
					? Attributes.SPEED.get(bag)
					: Attributes.SPEED.clamp(launch.speed());
			// Do not write the resolved speed back into the bag: the next launch op may
			// want its own speed, and flight.speed is an override input, not an output.
			out.add(new Effect.SetVelocity(direction.normalize().scale(speed)));
		} else if (op instanceof Op.Set set) {
			state.mergeLocal(set.attributes());
		} else if (op instanceof Op.Burst) {
			out.add(new Effect.Burst());
		} else if (op instanceof Op.Call call) {
			state.push(ctx.programs().find(call.program()));
		} else if (op instanceof Op.Spawn spawn) {
			for (int i = 0; i < spawn.count(); i++) {
				if (!state.tryConsumeChild()) {
					break;
				}
				out.add(new Effect.SpawnChild(spawn.program(), spawn.spread(), spawn.delayTicks()));
			}
		} else if (op instanceof Op.Sound sound) {
			ResourceLocation id = sound.id();
			if (bag.has(Attributes.SOUND_ID)) {
				ResourceLocation parsed = ResourceLocation.tryParse(bag.raw().getString(Attributes.SOUND_ID));
				if (parsed != null) {
					id = parsed;
				}
			}
			float volume = bag.has(Attributes.SOUND_VOLUME.key())
					? Attributes.SOUND_VOLUME.get(bag) : sound.volume();
			float pitch = bag.has(Attributes.SOUND_PITCH.key())
					? Attributes.SOUND_PITCH.get(bag) : sound.pitch();
			out.add(new Effect.PlaySound(id, volume, pitch));
		} else if (op instanceof Op.Light light) {
			int ticks = bag.has(Attributes.LIGHT_TICKS.key())
					? Attributes.LIGHT_TICKS.get(bag) : light.ticks();
			out.add(new Effect.Light(ticks));
		} else if (op instanceof Op.End) {
			state.pop();
		}
	}

	/**
	 * The direction relative to whoever launched the rocket: horizontal 0° is straight ahead and
	 * grows toward their right; the vertical dial is the polar angle from world up (0° = up).
	 */
	private static Vec3 relative(AttributeBag bag, Vec3 look) {
		double yaw = Math.toRadians(Attributes.DIRECTION_YAW.get(bag));
		double polar = Math.toRadians(Attributes.DIRECTION_PITCH.get(bag));

		double length = Math.hypot(look.x, look.z);
		double forwardX = 0.0;
		double forwardZ = 1.0;
		if (length > 1.0E-4) {
			// Looking straight up or down leaves no heading; fall back to +Z.
			forwardX = look.x / length;
			forwardZ = look.z / length;
		}
		double rightX = -forwardZ;
		double rightZ = forwardX;

		double horizontal = Math.sin(polar);
		return new Vec3(
				horizontal * (Math.cos(yaw) * forwardX + Math.sin(yaw) * rightX),
				Math.cos(polar),
				horizontal * (Math.cos(yaw) * forwardZ + Math.sin(yaw) * rightZ));
	}
}
