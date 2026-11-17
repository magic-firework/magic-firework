package com.magic.firework.craft;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import com.magic.firework.attribute.AttributeBag;
import com.magic.firework.attribute.Attributes;
import com.magic.firework.content.FireworkProfile;
import com.magic.firework.rocket.RocketSpec;

import org.junit.jupiter.api.Test;

import net.minecraft.resources.ResourceLocation;

/** The bill is a pure function of the resolved, clamped attributes of the whole rocket tree. */
class FormulaCostTest {
	private static final ResourceLocation RING = new ResourceLocation("magic-firework", "ring12");
	private static final ResourceLocation CHILD = new ResourceLocation("magic-firework", "ring");

	private static List<Cost> bill(AttributeBag overlay) {
		return FormulaCost.of(id -> null, RocketSpec.of(RING, overlay));
	}

	private static List<Cost> bill(FireworkProfile profile, AttributeBag overlay) {
		return FormulaCost.of(id -> profile, RocketSpec.of(RING, overlay));
	}

	@Test
	void defaultsFollowTheBuiltInAttributes() {
		// max.depth 3 and max.children 32 -> 1 + 0 + 2 gunpowder
		List<Cost> cost = bill(AttributeBag.EMPTY);
		assertEquals(1, count(cost, FormulaCost.PAPER));
		assertEquals(3, count(cost, FormulaCost.GUNPOWDER));
		assertEquals(1, count(cost, FormulaCost.GLOWSTONE));
	}

	@Test
	void trailToggleRemovesGlowstone() {
		AttributeBag bag = Attributes.TRAIL.set(AttributeBag.EMPTY, false);
		assertEquals(0, count(bill(bag), FormulaCost.GLOWSTONE));
	}

	@Test
	void gunpowderFollowsDepthAndChildren() {
		AttributeBag bag = Attributes.MAX_DEPTH.set(AttributeBag.EMPTY, 8);
		bag = Attributes.MAX_CHILDREN.set(bag, 64);
		// 1 + 8/4 + 64/16 = 7
		assertEquals(7, count(bill(bag), FormulaCost.GUNPOWDER));
	}

	@Test
	void gunpowderIsClampedEvenIfTheTagIsHuge() {
		AttributeBag bag = Attributes.MAX_DEPTH.set(AttributeBag.EMPTY, 16);
		bag = Attributes.MAX_CHILDREN.set(bag, 256);
		// 1 + 4 + 16 would be 21; the bill caps at 8 so it still fits a grid.
		assertEquals(8, count(bill(bag), FormulaCost.GUNPOWDER));
	}

	@Test
	void profileDefaultsApplyAndOverlayWins() {
		// The profile only overrides depth; children keeps its built-in default of 32.
		AttributeBag defaults = Attributes.MAX_DEPTH.set(AttributeBag.EMPTY, 4);
		FireworkProfile profile = new FireworkProfile(RING, null, defaults);
		assertEquals(4, count(bill(profile, AttributeBag.EMPTY), FormulaCost.GUNPOWDER));

		AttributeBag overlay = Attributes.MAX_DEPTH.set(AttributeBag.EMPTY, 0);
		overlay = Attributes.MAX_CHILDREN.set(overlay, 0);
		assertEquals(1, count(bill(profile, overlay), FormulaCost.GUNPOWDER));
	}

	@Test
	void childrenArePricedOnTopOfTheShell() {
		RocketSpec tree = new RocketSpec(RING, AttributeBag.EMPTY, List.of(
				RocketSpec.of(CHILD),
				new RocketSpec(CHILD, AttributeBag.EMPTY, List.of(RocketSpec.of(CHILD)))));

		List<Cost> cost = FormulaCost.of(id -> null, tree);

		// Four rockets: the shell, its child, and a second child that brings its own child.
		assertEquals(4, count(cost, FormulaCost.PAPER));
		assertEquals(12, count(cost, FormulaCost.GUNPOWDER));
		assertEquals(4, count(cost, FormulaCost.GLOWSTONE));
	}

	@Test
	void aChildWithoutATrailDoesNotBillGlowstone() {
		RocketSpec child = new RocketSpec(CHILD, Attributes.TRAIL.set(AttributeBag.EMPTY, false), List.of());
		RocketSpec tree = new RocketSpec(RING, AttributeBag.EMPTY, List.of(child));

		assertEquals(1, count(FormulaCost.of(id -> null, tree), FormulaCost.GLOWSTONE));
	}

	private static int count(List<Cost> cost, String item) {
		return cost.stream().filter(entry -> entry.item().equals(item)).mapToInt(Cost::count).sum();
	}
}
