package com.magic.firework.program;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

/**
 * One entry of the call stack: a program plus its instruction pointer.
 *
 * <p>{@code waitStart} is the age at which the current {@link Op.Await} began blocking,
 * stored as an absolute tick so that save/load keeps the remaining wait intact.
 */
public final class Frame {
	private final ResourceLocation programId;
	private final Program program;
	private int pc;
	private int waitStart = -1;

	public Frame(Program program) {
		this.programId = program.id();
		this.program = program;
	}

	private Frame(ResourceLocation programId, Program program, int pc, int waitStart) {
		this.programId = programId;
		this.program = program;
		this.pc = pc;
		this.waitStart = waitStart;
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
		return new Frame(id, program, Math.max(0, tag.getInt("pc")), tag.contains("wait") ? tag.getInt("wait") : -1);
	}

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

	public int waitStart() {
		return waitStart;
	}

	public void markWait(int age) {
		if (waitStart < 0) {
			waitStart = age;
		}
	}

	public void clearWait() {
		waitStart = -1;
	}

	public CompoundTag save() {
		CompoundTag tag = new CompoundTag();
		tag.putString("id", programId.toString());
		tag.putInt("pc", pc);
		if (waitStart >= 0) {
			tag.putInt("wait", waitStart);
		}
		return tag;
	}
}
