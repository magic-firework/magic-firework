package com.magic.firework.program;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

/**
 * One entry of the call stack: a program, the tick its clock started and its instruction
 * pointer.
 *
 * <p>{@code startAge} is absolute so that save/load keeps a nested frame's schedule intact:
 * every {@link Instruction#at()} is measured from it, which is what makes a {@code call}
 * shift a whole program in time without rewriting any op.
 */
public final class Frame {
	private final ResourceLocation programId;
	private final Program program;
	private final int startAge;
	private int pc;

	public Frame(Program program, int startAge) {
		this.programId = program.id();
		this.program = program;
		this.startAge = Math.max(0, startAge);
	}

	private Frame(ResourceLocation programId, Program program, int startAge, int pc) {
		this.programId = programId;
		this.program = program;
		this.startAge = Math.max(0, startAge);
		this.pc = pc;
	}

	public static Frame load(ProgramLookup lookup, CompoundTag tag) {
		ResourceLocation id = ResourceLocation.tryParse(tag.getString("id"));
		if (id == null) {
			return null;
		}
		Program program = lookup.find(id);
		if (program == null) {
			return null;
		}
		return new Frame(id, program, tag.getInt("start"), Math.max(0, tag.getInt("pc")));
	}

	public ResourceLocation programId() {
		return programId;
	}

	public Program program() {
		return program;
	}

	/** Age of the rocket when this frame was pushed; the frame's timeline starts here. */
	public int startAge() {
		return startAge;
	}

	public int pc() {
		return pc;
	}

	public void advance() {
		pc++;
	}

	public CompoundTag save() {
		CompoundTag tag = new CompoundTag();
		tag.putString("id", programId.toString());
		tag.putInt("start", startAge);
		tag.putInt("pc", pc);
		return tag;
	}
}
