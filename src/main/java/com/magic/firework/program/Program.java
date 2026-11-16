package com.magic.firework.program;

import java.util.List;

import net.minecraft.resources.ResourceLocation;

/**
 * An immutable, straight-line, time-ordered instruction list.
 *
 * <p>There is no jump instruction, no phase enum and no wait op: time is
 * {@link Instruction#at()}, nesting is {@link Op.Call} (push a frame) and {@link Op.End} (pop).
 * Branches are unnecessary because every firework is a one-shot sequence, which is exactly why
 * this stays small.
 */
public record Program(ResourceLocation id, List<Instruction> instructions) {
	public Program {
		instructions = List.copyOf(instructions);
	}

	public int size() {
		return instructions.size();
	}

	public Instruction instruction(int index) {
		return instructions.get(index);
	}

	/** Convenience for callers that only care which op sits at an index. */
	public Op op(int index) {
		return instructions.get(index).op();
	}

	@Override
	public String toString() {
		return "Program[" + id + ", " + instructions.size() + " ops]";
	}
}
