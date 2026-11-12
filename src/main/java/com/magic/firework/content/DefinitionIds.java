package com.magic.firework.content;

import net.minecraft.resources.ResourceLocation;

/** Well-known ids that code itself has to name. Everything else is only referenced by data. */
public final class DefinitionIds {
	/** Fallback for commands that do not name a profile. */
	public static final ResourceLocation BASIC = new ResourceLocation("magic-firework", "basic");
	/** The stack shown in the creative tab. */
	public static final ResourceLocation RING12 = new ResourceLocation("magic-firework", "ring12");
	/** The shipped showcase display; {@code /magicfirework golden_peony} hands it out directly. */
	public static final ResourceLocation GOLDEN_PEONY = new ResourceLocation("magic-firework", "golden_peony");

	private DefinitionIds() {
	}
}
