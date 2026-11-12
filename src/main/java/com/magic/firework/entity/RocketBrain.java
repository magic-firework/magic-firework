package com.magic.firework.entity;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import com.magic.firework.attribute.AttributeBag;
import com.magic.firework.attribute.Attributes;
import com.magic.firework.program.Effect;
import com.magic.firework.program.Interpreter;
import com.magic.firework.program.InterpreterContext;
import com.magic.firework.program.Program;
import com.magic.firework.program.ProgramLookup;
import com.magic.firework.program.ProgramState;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

/**
 * The program side of a rocket: age, call stack, flight direction and the delayed-child
 * queue. No {@code Level}, no packets, no particles, so it can be reasoned about and tested
 * without a running game. {@link MagicFireworkEntity} is the thin Minecraft adapter.
 */
public final class RocketBrain {
	private static final double EPSILON = 1.0E-6;

	private ProgramState state = ProgramState.empty();
	private ResourceLocation profileId;
	private Vec3 lookDirection = new Vec3(0.0, 1.0, 0.0);
	private boolean collided;
	private int doneTicks;
	private final List<Child> pendingChildren = new ArrayList<>();

	private RocketBrain() {
	}

	public static RocketBrain launch(ResourceLocation profileId, Program program, AttributeBag base, long seed,
			int depth, Vec3 lookDirection) {
		RocketBrain brain = new RocketBrain();
		brain.profileId = profileId;
		brain.lookDirection = normalized(lookDirection, new Vec3(0.0, 1.0, 0.0));
		brain.state = ProgramState.start(program, base, seed, depth);
		return brain;
	}

	/** Not launched yet: nothing is known, so nothing runs and nothing renders. */
	public static RocketBrain empty() {
		return new RocketBrain();
	}

	/** Client-side: no program is run, only the trail is rendered from the attributes. */
	public static RocketBrain clientView(ResourceLocation profileId, AttributeBag base, long seed, int age,
			Vec3 lookDirection) {
		RocketBrain brain = new RocketBrain();
		brain.profileId = profileId;
		brain.lookDirection = normalized(lookDirection, new Vec3(0.0, 1.0, 0.0));
		brain.state = ProgramState.clientView(base, seed, age);
		return brain;
	}

	// ── state ────────────────────────────────────────────────────────────────

	public boolean initialized() {
		return state.initialized();
	}

	public boolean done() {
		return state.done();
	}

	public long seed() {
		return state.seed();
	}

	public int age() {
		return state.age();
	}

	public int depth() {
		return state.depth();
	}

	public AttributeBag bag() {
		return state.bag();
	}

	public ResourceLocation profileId() {
		return profileId;
	}

	public Vec3 lookDirection() {
		return lookDirection;
	}

	public void advanceAge() {
		if (state.initialized()) {
			state.tickAge();
		}
	}

	/** Run one interpreter tick; the caller applies the returned effects. */
	public Effect.TickResult tick(ProgramLookup programs, Vec3 position) {
		Effect.TickResult result = Interpreter.tick(state,
				new InterpreterContext(programs, position, lookDirection, collided));
		if (state.done()) {
			doneTicks++;
		}
		return result;
	}

	/** True once the program finished and the afterglow has elapsed too. */
	public boolean expired() {
		return state.done() && doneTicks > Attributes.AFTERGLOW.get(bag());
	}

	public void setCollided(boolean collided) {
		this.collided = collided;
	}

	// ── split children ───────────────────────────────────────────────────────

	/**
	 * Queue (or hand back immediately) a child. The caller spawns the returned child; a
	 * delayed one is returned by {@link #dueChildren()} on a later tick.
	 */
	public Child scheduleChild(ResourceLocation programId, float spread, int delayTicks) {
		Child child = new Child(state.age() + Math.max(0, delayTicks), programId, spread, state.nextChildSeed());
		if (delayTicks <= 0) {
			return child;
		}
		pendingChildren.add(child);
		return null;
	}

	/** Remove and return the children whose delay has elapsed. */
	public List<Child> dueChildren() {
		if (pendingChildren.isEmpty()) {
			return List.of();
		}
		List<Child> due = null;
		Iterator<Child> iterator = pendingChildren.iterator();
		while (iterator.hasNext()) {
			Child child = iterator.next();
			if (child.atAge() <= state.age()) {
				if (due == null) {
					due = new ArrayList<>();
				}
				due.add(child);
				iterator.remove();
			}
		}
		return due == null ? List.of() : due;
	}

	public long nextChildSeed() {
		return state.nextChildSeed();
	}

	// ── client / persistence ─────────────────────────────────────────────────

	public void acceptClientView(AttributeBag base, long seed, int age) {
		this.state = ProgramState.clientView(base, seed, age);
	}

	public void save(CompoundTag tag) {
		if (profileId != null) {
			tag.putString("Profile", profileId.toString());
		}
		tag.put("Base", state.base().save());
		tag.put("State", state.save());
		tag.putDouble("LookX", lookDirection.x);
		tag.putDouble("LookY", lookDirection.y);
		tag.putDouble("LookZ", lookDirection.z);
	}

	/** @return the restored brain, or {@code null} when the profile is no longer installed */
	public static RocketBrain load(CompoundTag tag, ProgramLookup lookup) {
		ResourceLocation id = ResourceLocation.tryParse(tag.getString("Profile"));
		AttributeBag base = AttributeBag.load(tag.getCompound("Base"));
		Vec3 look = new Vec3(tag.getDouble("LookX"), tag.getDouble("LookY"), tag.getDouble("LookZ"));
		if (look.lengthSqr() < EPSILON) {
			look = new Vec3(0.0, 1.0, 0.0);
		}

		RocketBrain brain = new RocketBrain();
		brain.profileId = id;
		brain.lookDirection = look;
		CompoundTag stateTag = tag.getCompound("State");
		if (!stateTag.isEmpty()) {
			brain.state = ProgramState.load(stateTag, lookup, base);
			return brain;
		}
		Program program = id == null ? null : lookup.find(id);
		if (program == null) {
			return null;
		}
		brain.state = ProgramState.start(program, base, 0L, 0);
		return brain;
	}

	public record Child(int atAge, ResourceLocation programId, float spread, long seed) {
	}

	private static Vec3 normalized(Vec3 value, Vec3 fallback) {
		if (value == null || value.lengthSqr() < EPSILON) {
			return fallback;
		}
		return value.normalize();
	}
}
