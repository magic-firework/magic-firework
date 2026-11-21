package com.magic.firework.craft;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import com.magic.firework.attribute.AttributeBag;
import com.magic.firework.attribute.Attributes;
import com.magic.firework.config.FireworkLimits;
import com.magic.firework.content.FireworkProfile;
import com.magic.firework.rocket.RocketSpec;

import net.minecraft.resources.ResourceLocation;

/**
 * Derives the material bill of one rocket <em>tree</em> from its concrete attribute values, once, when a
 * blueprint is written; the bill goes into the blueprint's NBT, so a written blueprint is a snapshot and
 * changing a data pack never reprices it. A nested rocket costs what its children cost on top of itself,
 * and every number is a clamped attribute or a {@link FireworkLimits} constant, so the total is bounded.
 */
public final class FormulaCost {
	public static final String PAPER = "minecraft:paper";
	public static final String GUNPOWDER = "minecraft:gunpowder";
	public static final String GLOWSTONE = "minecraft:glowstone_dust";

	/** Hard cap per ingredient so one rocket cannot ask for more than a grid can hold. */
	private static final int MAX_GUNPOWDER = 8;

	private FormulaCost() {
	}

	/**
	 * @param profiles resolves an id to the installed profile, or {@code null} when the data pack
	 *                 does not provide it (that rocket still bills from its overlay alone, which
	 *                 keeps a bill computable for content that has since been removed)
	 */
	public static List<Cost> of(Function<ResourceLocation, FireworkProfile> profiles, RocketSpec spec) {
		Map<String, Integer> bill = new LinkedHashMap<>();
		accumulate(profiles, spec, bill);
		List<Cost> costs = new ArrayList<>(bill.size());
		bill.forEach((item, count) -> costs.add(new Cost(item, count)));
		return List.copyOf(costs);
	}

	private static void accumulate(Function<ResourceLocation, FireworkProfile> profiles, RocketSpec spec,
			Map<String, Integer> bill) {
		FireworkProfile profile = profiles.apply(spec.profileId());
		AttributeBag bag = profile == null ? spec.overlay() : profile.defaults().overlay(spec.overlay());
		int gunpowder = Math.max(1, Math.min(MAX_GUNPOWDER,
				1 + FireworkLimits.maxDepth / 4 + FireworkLimits.maxChildren / 16));
		add(bill, PAPER, 1);
		add(bill, GUNPOWDER, gunpowder);
		if (Attributes.TRAIL.get(bag)) {
			add(bill, GLOWSTONE, 1);
		}
		for (RocketSpec child : spec.children()) {
			accumulate(profiles, child, bill);
		}
	}

	private static void add(Map<String, Integer> bill, String item, int count) {
		bill.merge(item, count, Integer::sum);
	}
}
