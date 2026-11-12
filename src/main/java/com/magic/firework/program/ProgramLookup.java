package com.magic.firework.program;

import net.minecraft.resources.ResourceLocation;

/** Resolves a program id to its program; backed by the data pack store. */
@FunctionalInterface
public interface ProgramLookup {
	Program find(ResourceLocation id);

	static ProgramLookup of(java.util.Map<ResourceLocation, Program> programs) {
		return programs::get;
	}
}
