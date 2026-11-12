package com.magic.firework.content;

import com.magic.firework.attribute.AttributeBag;
import com.magic.firework.program.Program;

import net.minecraft.resources.ResourceLocation;

/**
 * What a data pack file (or the built-in content) provides: an id, a program and the
 * profile-level attribute defaults. Item NBT only stores the id plus per-instance
 * overrides, so updating a data pack updates existing items.
 */
public record FireworkProfile(ResourceLocation id, Program program, AttributeBag defaults) {
	public FireworkProfile {
		defaults = defaults == null ? AttributeBag.EMPTY : defaults;
	}

}
