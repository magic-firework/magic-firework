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
 *   <li>execute ops in order until the top frame is waiting on an {@link Op.Wait};</li>
 *   <li>a frame whose program counter leaves the end is popped (implicit {@code end});</li>
 *   <li>at most {@link FireworkLimits#maxOpsPerTick} ops run per tick, so a pathological
 *       program cannot stall the server;</li>
 *   <li>ops only mutate {@link ProgramState} or append an {@link Effect}.</li>
 * </ol>
 *
 * <p>Time is spent by {@link Op.Wait} and by nothing else: the interpreter either runs the op
 * under the pointer or reports that this frame is still waiting, so no op ever blocks on
 * anything but its own schedule.
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
			Op op = frame.program().op(frame.pc());
			if (op instanceof Op.Wait wait) {
				if (!frame.waiting()) {
					frame.waitUntil(state.age() + waitLength(wait, state.bag()));
				}
				if (state.age() < frame.waitUntil()) {
					// Still waiting: the rocket keeps flying, the program does nothing.
					break;
				}
				frame.clearWait();
			}
			frame.advance();
			apply(op, state, ctx, effects);
		}
		return new Effect.TickResult(List.copyOf(effects), state.done());
	}

	/** {@code wait} reads the flight time the player set; {@code wait ticks} is literal. */
	private static int waitLength(Op.Wait wait, AttributeBag bag) {
		return wait instanceof Op.Wait.Ticks ticks ? ticks.ticks() : Attributes.FLIGHT_TICKS.get(bag);
	}

	// Java 17 target: an instanceof chain instead of a pattern switch.
	private static void apply(Op op, ProgramState state, InterpreterContext ctx, List<Effect> out) {
		AttributeBag bag = state.bag();
		if (op instanceof Op.Launch launch) {
			Vec3 direction = launchDirection(bag, ctx.lookDirection());
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
		} else if (op instanceof Op.Burst) {
			out.add(new Effect.Burst());
		} else if (op instanceof Op.Call call) {
			state.push(ctx.programs().find(call.profile()));
		} else if (op instanceof Op.Spawn spawn) {
			for (SpawnTarget target : spawn.targets()) {
				if (!state.tryConsumeChild()) {
					break;
				}
				out.add(new Effect.SpawnChild(target, spawn.delayTicks()));
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
	 * The bag is the only source of direction: {@code relative} aims from the launcher's heading,
	 * anything else (including the default {@code up}) goes straight up. The editor writes
	 * {@code relative} when a dial is turned, so an aimed rocket and a straight one run the same
	 * program.
	 */
	private static Vec3 launchDirection(AttributeBag bag, Vec3 look) {
		return "relative".equalsIgnoreCase(Attributes.DIRECTION_MODE.get(bag))
				? relative(bag, look)
				: new Vec3(0.0, 1.0, 0.0);
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
