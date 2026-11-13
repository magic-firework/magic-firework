package com.magic.firework.craft;

import java.util.List;

import com.magic.firework.attribute.AttributeBag;
import com.magic.firework.attribute.Attributes;
import com.magic.firework.content.FireworkProfile;

/**
 * Derives the material bill of one firework from its concrete attribute values.
 *
 * <p>This runs once, when a blueprint is written, and its result is stored in the
 * blueprint's NBT. Later crafts read that bill instead of recomputing it, so a written
 * blueprint is a snapshot: changing a data pack never changes how much an already written
 * blueprint costs.
 *
 * <p>Every number comes from a clamped attribute, so a forged {@code max.children} cannot
 * make the bill negative or unbounded.
 */
public final class FormulaCost {
	public static final String PAPER = "minecraft:paper";
	public static final String GUNPOWDER = "minecraft:gunpowder";
	public static final String GLOWSTONE = "minecraft:glowstone_dust";

	/** Hard cap per ingredient so one blueprint cannot ask for more than a grid can hold. */
	private static final int MAX_GUNPOWDER = 8;

	private FormulaCost() {
	}

	/**
	 * @param profile the resolved profile, or {@code null} when the id is not installed
	 *                (the overlay alone still yields a usable, conservative bill)
	 * @param overlay the per-item attribute overlay
	 */
	public static List<Cost> of(FireworkProfile profile, AttributeBag overlay) {
		AttributeBag bag = profile == null
				? (overlay == null ? AttributeBag.EMPTY : overlay)
				: profile.defaults().overlay(overlay);
		int depth = Attributes.MAX_DEPTH.get(bag);
		int children = Attributes.MAX_CHILDREN.get(bag);
		int gunpowder = Math.max(1, Math.min(MAX_GUNPOWDER, 1 + depth / 4 + children / 16));
		int glowstone = Attributes.TRAIL.get(bag) ? 1 : 0;
		return List.of(
				new Cost(PAPER, 1),
				new Cost(GUNPOWDER, gunpowder),
				new Cost(GLOWSTONE, glowstone));
	}
}
