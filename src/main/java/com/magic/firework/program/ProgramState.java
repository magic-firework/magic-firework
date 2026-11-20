package com.magic.firework.program;

import java.util.ArrayList;
import java.util.List;

import com.magic.firework.attribute.AttributeBag;
import com.magic.firework.config.FireworkLimits;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;

/**
 * Everything a running firework knows: the call stack, the tick counter, the attribute bag and the seed.
 * Pure data, no world access, so it can be saved, replayed and asserted without a running server. The bag
 * is fixed for the entity's whole life (profile defaults overlaid at launch); no op changes it, so
 * {@code bag()} is simply that snapshot.
 */
public final class ProgramState {
	private final List<Frame> stack = new ArrayList<>();
	private AttributeBag base = AttributeBag.EMPTY;
	private long seed;
	private int age;
	private int depth;
	private int childCount;
	private long childSeedMix;
	private boolean initialized;

	/** A root launch: every rocket uses the nesting budget from {@link FireworkLimits}. */
	public static ProgramState start(Program root, AttributeBag base, long seed, int depth) {
		ProgramState state = new ProgramState();
		state.base = base == null ? AttributeBag.EMPTY : base;
		state.seed = seed;
		state.depth = depth;
		state.stack.add(new Frame(root));
		state.initialized = true;
		return state;
	}

	/** Not launched yet: nothing is known, so nothing runs and nothing renders. */
	public static ProgramState empty() {
		return new ProgramState();
	}

	/** Client-side: no program is run, only the trail is rendered from the attributes. */
	public static ProgramState clientView(AttributeBag base, long seed, int age) {
		ProgramState state = new ProgramState();
		state.base = base == null ? AttributeBag.EMPTY : base;
		state.seed = seed;
		state.age = Math.max(0, age);
		state.initialized = true;
		return state;
	}

	public boolean initialized() {
		return initialized;
	}

	public boolean done() {
		return stack.isEmpty();
	}

	public Frame current() {
		return stack.isEmpty() ? null : stack.get(stack.size() - 1);
	}

	public int stackSize() {
		return stack.size();
	}

	public long seed() {
		return seed;
	}

	public int age() {
		return age;
	}

	public int depth() {
		return depth;
	}

	/** Deepest generation this rocket's descendants may reach; fixed by {@link FireworkLimits}. */
	public int depthLimit() {
		return FireworkLimits.maxDepth;
	}

	public int childCount() {
		return childCount;
	}

	public AttributeBag base() {
		return base;
	}

	public AttributeBag bag() {
		return base;
	}

	public void tickAge() {
		age++;
	}

	/**
	 * A collision ends any wait in progress: the flight time is over the moment the rocket hits,
	 * so the op after the wait runs on the next tick instead of later.
	 */
	public void satisfyWaits() {
		for (Frame frame : stack) {
			if (frame.waiting()) {
				frame.waitUntil(age);
			}
		}
	}

	public void push(Program program) {
		if (program == null) {
			return;
		}
		if (stack.size() >= FireworkLimits.maxStackDepth) {
			return;
		}
		for (Frame frame : stack) {
			if (frame.programId().equals(program.id())) {
				// A program that (indirectly) calls itself would never end. Refuse.
				return;
			}
		}
		stack.add(new Frame(program));
	}

	public void pop() {
		if (!stack.isEmpty()) {
			stack.remove(stack.size() - 1);
		}
	}

	public boolean tryConsumeChild() {
		if (childCount >= FireworkLimits.maxChildren) {
			return false;
		}
		childCount++;
		return true;
	}

	/** Deterministic seed for the n-th child, see {@code SeedDerivation}. */
	public long nextChildSeed() {
		childSeedMix = childSeedMix * 0x9E3779B97F4A7C15L + 0x2545F4914F6CDD1DL;
		long mixed = seed ^ childSeedMix;
		mixed ^= mixed >>> 33;
		mixed *= 0xFF51AFD7ED558CCDL;
		mixed ^= mixed >>> 33;
		return mixed;
	}

	public CompoundTag save() {
		CompoundTag tag = new CompoundTag();
		tag.putLong("Seed", seed);
		tag.putInt("Age", age);
		tag.putInt("Depth", depth);
		tag.putInt("Children", childCount);
		ListTag frames = new ListTag();
		for (Frame frame : stack) {
			frames.add(frame.save());
		}
		tag.put("Frames", frames);
		return tag;
	}

	public static ProgramState load(CompoundTag tag, ProgramLookup lookup, AttributeBag base) {
		ProgramState state = new ProgramState();
		state.base = base == null ? AttributeBag.EMPTY : base;
		state.seed = tag.getLong("Seed");
		state.age = Math.max(0, tag.getInt("Age"));
		state.depth = Math.max(0, tag.getInt("Depth"));
		state.childCount = Math.max(0, tag.getInt("Children"));
		ListTag frames = tag.getList("Frames", CompoundTag.TAG_COMPOUND);
		for (int i = 0; i < frames.size(); i++) {
			Frame frame = Frame.load(lookup, frames.getCompound(i));
			if (frame != null) {
				state.stack.add(frame);
			}
		}
		if (state.stack.isEmpty()) {
			// A finished program (or a profile that disappeared) resumes as "done":
			// the entity dies after its afterglow instead of restarting the show.
			state.initialized = true;
			return state;
		}
		state.initialized = true;
		return state;
	}

}
