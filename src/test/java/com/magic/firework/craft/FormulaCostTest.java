package com.magic.firework.craft;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import com.magic.firework.attribute.AttributeBag;
import com.magic.firework.attribute.Attributes;
import com.magic.firework.content.FireworkProfile;

import org.junit.jupiter.api.Test;

import net.minecraft.resources.ResourceLocation;

/** The bill is a pure function of the resolved, clamped attributes. */
class FormulaCostTest {
	private static final ResourceLocation RING = new ResourceLocation("magic-firework", "ring12");

	@Test
	void defaultsFollowTheBuiltInAttributes() {
		// max.depth 3 and max.children 32 -> 1 + 0 + 2 gunpowder
		List<Cost> cost = FormulaCost.of(null, AttributeBag.EMPTY);
		assertEquals(1, count(cost, FormulaCost.PAPER));
		assertEquals(3, count(cost, FormulaCost.GUNPOWDER));
		assertEquals(1, count(cost, FormulaCost.GLOWSTONE));
	}

	@Test
	void trailToggleRemovesGlowstone() {
		AttributeBag bag = Attributes.TRAIL.set(AttributeBag.EMPTY, false);
		assertEquals(0, count(FormulaCost.of(null, bag), FormulaCost.GLOWSTONE));
	}

	@Test
	void gunpowderFollowsDepthAndChildren() {
		AttributeBag bag = Attributes.MAX_DEPTH.set(AttributeBag.EMPTY, 8);
		bag = Attributes.MAX_CHILDREN.set(bag, 64);
		// 1 + 8/4 + 64/16 = 7
		assertEquals(7, count(FormulaCost.of(null, bag), FormulaCost.GUNPOWDER));
	}

	@Test
	void gunpowderIsClampedEvenIfTheTagIsHuge() {
		AttributeBag bag = Attributes.MAX_DEPTH.set(AttributeBag.EMPTY, 16);
		bag = Attributes.MAX_CHILDREN.set(bag, 256);
		// 1 + 4 + 16 would be 21; the bill caps at 8 so it still fits a grid.
		assertEquals(8, count(FormulaCost.of(null, bag), FormulaCost.GUNPOWDER));
	}

	@Test
	void profileDefaultsApplyAndOverlayWins() {
		// The profile only overrides depth; children keeps its built-in default of 32.
		AttributeBag defaults = Attributes.MAX_DEPTH.set(AttributeBag.EMPTY, 4);
		FireworkProfile profile = new FireworkProfile(RING, null, defaults);
		assertEquals(4, count(FormulaCost.of(profile, AttributeBag.EMPTY), FormulaCost.GUNPOWDER));

		AttributeBag overlay = Attributes.MAX_DEPTH.set(AttributeBag.EMPTY, 0);
		overlay = Attributes.MAX_CHILDREN.set(overlay, 0);
		assertEquals(1, count(FormulaCost.of(profile, overlay), FormulaCost.GUNPOWDER));
	}

	private static int count(List<Cost> cost, String item) {
		return cost.stream().filter(entry -> entry.item().equals(item)).mapToInt(Cost::count).sum();
	}
}
