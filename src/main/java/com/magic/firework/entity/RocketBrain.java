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
import com.magic.firework.rocket.RocketSpec;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

/**
 * The program side of a rocket: age, call stack, flight direction, the child rockets this rocket may
 * spawn (its own item's tree) and the delayed children already queued. No {@code Level}, no packets, no
 * particles, so it is testable without a running game; {@link MagicFireworkEntity} is the thin adapter.
 * Age, stack, budget, tree and queue all survive save/load, so a reload resumes the same show.
 */
public final class RocketBrain {
	private static final double EPSILON = 1.0E-6;

	private ProgramState state = ProgramState.empty();
	private ResourceLocation profileId;
	/** The launching item's tree; the root's own overlay is already folded into the bag. */
	private List<RocketSpec> children = List.of();
	private Vec3 lookDirection = new Vec3(0.0, 1.0, 0.0);
	private int doneTicks;
	private boolean collided;
	private final List<Child> pendingChildren = new ArrayList<>();

	private RocketBrain() {
	}

	public static RocketBrain launch(RocketSpec spec, Program program, AttributeBag base, long seed,
			int depth, Vec3 lookDirection) {
		RocketBrain brain = new RocketBrain();
		brain.profileId = spec.profileId();
		brain.children = spec.children();
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

	/** Deepest generation this rocket's children may reach; fixed by {@code FireworkLimits}. */
	public int depthLimit() {
		return state.depthLimit();
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
	public Effect.TickResult tick(ProgramLookup programs) {
		if (collided) {
			// Hitting something ends the flight: whatever the program was waiting for is due now.
			state.satisfyWaits();
		}
		Effect.TickResult result = Interpreter.tick(state, new InterpreterContext(programs, lookDirection));
		if (state.done()) {
			doneTicks++;
		}
		return result;
	}

	/** True once the program finished and the afterglow has elapsed too. */
	/**
	 * A collision ends any wait in progress, so a rocket that hits a block bursts on the spot
	 * instead of hanging there until its flight time runs out.
	 */
	public void setCollided(boolean collided) {
		this.collided = collided;
	}

	public boolean expired() {
		return state.done() && doneTicks > Attributes.AFTERGLOW.get(bag());
	}


	/** @return the child this rocket's item carries at {@code index}, or {@code null} for an empty slot */
	public RocketSpec child(int index) {
		return index < 0 || index >= children.size() ? null : children.get(index);
	}

	/**
	 * Queue (or hand back immediately) a child. The caller spawns the returned child; a
	 * delayed one is returned by {@link #dueChildren()} on a later tick.
	 */
	public Child scheduleChild(RocketSpec target, int delayTicks) {
		Child child = new Child(state.age() + Math.max(0, delayTicks), target, state.nextChildSeed());
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
		if (!children.isEmpty()) {
			ListTag tree = new ListTag();
			for (RocketSpec child : children) {
				tree.add(child.save());
			}
			tag.put("Children", tree);
		}
		if (!pendingChildren.isEmpty()) {
			ListTag pending = new ListTag();
			for (Child child : pendingChildren) {
				pending.add(child.save());
			}
			tag.put("Pending", pending);
		}
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
		brain.children = loadChildren(tag);
		brain.pendingChildren.addAll(loadPending(tag));
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

	private static List<RocketSpec> loadChildren(CompoundTag tag) {
		ListTag tree = tag.getList("Children", CompoundTag.TAG_COMPOUND);
		if (tree.isEmpty()) {
			return List.of();
		}
		List<RocketSpec> loaded = new ArrayList<>(tree.size());
		for (int i = 0; i < tree.size(); i++) {
			RocketSpec child = RocketSpec.load(tree.get(i));
			if (child != null) {
				loaded.add(child);
			}
		}
		return List.copyOf(loaded);
	}

	private static List<Child> loadPending(CompoundTag tag) {
		ListTag pending = tag.getList("Pending", CompoundTag.TAG_COMPOUND);
		if (pending.isEmpty()) {
			return List.of();
		}
		List<Child> loaded = new ArrayList<>(pending.size());
		for (int i = 0; i < pending.size(); i++) {
			Child child = Child.load(pending.getCompound(i));
			if (child != null) {
				loaded.add(child);
			}
		}
		return loaded;
	}

	/** A child rocket still to appear: what, when, and the seed that keeps it reproducible. */
	public record Child(int atAge, RocketSpec spec, long seed) {
		public CompoundTag save() {
			CompoundTag tag = new CompoundTag();
			tag.putInt("Age", atAge);
			tag.putLong("Seed", seed);
			tag.put("Spec", spec.save());
			return tag;
		}

		/** @return the child, or {@code null} when its spec no longer parses */
		public static Child load(CompoundTag tag) {
			RocketSpec spec = RocketSpec.load(tag.get("Spec"));
			if (spec == null) {
				return null;
			}
			return new Child(Math.max(0, tag.getInt("Age")), spec, tag.getLong("Seed"));
		}
	}

	private static Vec3 normalized(Vec3 value, Vec3 fallback) {
		if (value == null || value.lengthSqr() < EPSILON) {
			return fallback;
		}
		return value.normalize();
	}
}
