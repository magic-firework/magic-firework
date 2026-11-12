package com.magic.firework.program;

import java.util.List;

import net.minecraft.resources.ResourceLocation;

/**
 * An immutable, straight-line instruction list.
 *
 * <p>There is no jump instruction and no phase enum: time is {@link Op.Await}, nesting
 * is {@link Op.Call} (push a frame) and {@link Op.End} (pop). Branches are unnecessary
 * because every firework is a one-shot sequence, which is exactly why this stays small.
 */
public record Program(ResourceLocation id, List<Op> ops) {
	public Program {
		ops = List.copyOf(ops);
	}

	public int size() {
		return ops.size();
	}

	public Op op(int index) {
		return ops.get(index);
	}

	@Override
	public String toString() {
		return "Program[" + id + ", " + ops.size() + " ops]";
	}
}
