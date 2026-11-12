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
 *   <li>execute ops until the top frame blocks on an unsatisfied {@link Op.Await};</li>
 *   <li>a frame whose program counter leaves the end is popped (implicit {@code end});</li>
 *   <li>at most {@link FireworkLimits#maxOpsPerTick} ops run per tick, so a pathological
 *       program cannot stall the server;</li>
 *   <li>ops only mutate {@link ProgramState} or append an {@link Effect}.</li>
 * </ol>
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
			if (op instanceof Op.Await await) {
				int elapsed = frame.waitStart() < 0 ? 0 : state.age() - frame.waitStart();
				Cond cond = resolveAwait(await.cond(), frame.program(), frame.pc(), state.bag());
				if (!cond.satisfied(ctx.position(), ctx.collided(), elapsed)) {
					frame.markWait(state.age());
					break;
				}
				frame.clearWait();
				frame.advance();
				continue;
			}
			frame.advance();
			apply(op, state, ctx, effects);
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
		} else if (op instanceof Op.Await) {
			// handled in tick()
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
	 * Only the await immediately before a burst reads {@code fuse.ticks}; the timeout
	 * fallback and every other await stay structural.
	 */
	private static Cond resolveAwait(Cond cond, Program program, int pc, AttributeBag bag) {
		if (cond instanceof Cond.Or or) {
			return new Cond.Or(resolveAwait(or.primary(), program, pc, bag), or.fallback());
		}
		if (cond instanceof Cond.Ticks && bag.has(Attributes.FUSE_TICKS.key()) && isBeforeBurst(program, pc)) {
			return new Cond.Ticks(Attributes.FUSE_TICKS.get(bag));
		}
		return cond;
	}

	private static boolean isBeforeBurst(Program program, int pc) {
		return pc + 1 < program.size() && program.op(pc + 1) instanceof Op.Burst;
	}
}
