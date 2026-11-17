package com.magic.firework.program;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

/**
 * One entry of the call stack: a program, its instruction pointer and its pending wait.
 *
 * <p>{@code waitUntil} is an absolute age, not a countdown, so saving mid-wait and loading it
 * back resumes on exactly the tick the author scheduled: a {@code wait 25} pushed at age 7 is
 * satisfied at age 32 before and after a reload, and no tick is lost or repeated.
 */
public final class Frame {
	/** No wait in progress. An absolute age is never negative, so the sentinel is unambiguous. */
	public static final int NO_WAIT = -1;

	private final ResourceLocation programId;
	private final Program program;
	private int pc;
	private int waitUntil = NO_WAIT;

	public Frame(Program program) {
		this(program.id(), program, 0, NO_WAIT);
	}

	private Frame(ResourceLocation programId, Program program, int pc, int waitUntil) {
		this.programId = programId;
		this.program = program;
		this.pc = Math.max(0, pc);
		this.waitUntil = Math.max(NO_WAIT, waitUntil);
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
		return new Frame(id, program, tag.getInt("pc"), tag.getInt("wait"));	}

	public ResourceLocation programId() {
		return programId;
	}

	public Program program() {
		return program;
	}

	public int pc() {
		return pc;
	}

	public void advance() {
		pc++;
	}

	/** @return the age this frame's wait is satisfied at, or {@link #NO_WAIT} when it is not waiting */
	public int waitUntil() {
		return waitUntil;
	}

	public boolean waiting() {
		return waitUntil != NO_WAIT;
	}

	public void waitUntil(int age) {
		this.waitUntil = Math.max(NO_WAIT, age);
	}

	public void clearWait() {
		this.waitUntil = NO_WAIT;
	}

	public CompoundTag save() {
		CompoundTag tag = new CompoundTag();
		tag.putString("id", programId.toString());
		tag.putInt("pc", pc);
		tag.putInt("wait", waitUntil);
		return tag;
	}
}
