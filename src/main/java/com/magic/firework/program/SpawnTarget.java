package com.magic.firework.program;

import java.util.Objects;

import net.minecraft.resources.ResourceLocation;

/**
 * One thing a {@code spawn} op asks for, in exactly one of two ways: a <b>slot</b> ({@link #slot(int)}),
 * the child the launching item carries at that index, so the item decides what appears and the data pack
 * only when; or a <b>profile</b> ({@link #of(ResourceLocation)}), content used as-is, how a pack ships a
 * fixed show. Mutually exclusive by construction, so item and pack never fight over the same child.
 */
public record SpawnTarget(int childIndex, ResourceLocation profile) {
	/** Sentinel child index for a target that names a profile instead of an item slot. */
	public static final int NO_SLOT = -1;

	public SpawnTarget {
		if (childIndex < NO_SLOT) {
			throw new IllegalArgumentException("a child slot cannot be negative");
		}
		boolean slot = childIndex != NO_SLOT;
		boolean named = profile != null;
		if (slot == named) {
			throw new IllegalArgumentException(
					"a spawn target needs either a child slot or a profile, not both and not neither");
		}
	}

	/** The child slot {@code index} of the launching item. */
	public static SpawnTarget slot(int index) {
		return new SpawnTarget(index, null);
	}

	/** The content profile {@code id}, spawned as-is with no item involvement. */
	public static SpawnTarget of(ResourceLocation id) {
		return new SpawnTarget(NO_SLOT, Objects.requireNonNull(id, "a profile target needs an id"));
	}

	public boolean isSlot() {
		return childIndex != NO_SLOT;
	}
}
