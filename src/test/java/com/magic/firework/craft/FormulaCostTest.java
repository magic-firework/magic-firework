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
	void defaultsFollowTheBuiltInLimits() {
		// FireworkLimits.maxDepth 3 and maxChildren 32 -> 1 + 0 + 2 gunpowder
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
	void gunpowderIsFixedByTheConfigLimits() {
		// Depth and children are safety constants now, so an attribute cannot buy more gunpowder.
		AttributeBag bag = Attributes.TRAIL.set(AttributeBag.EMPTY, false);
		assertEquals(3, count(bill(bag), FormulaCost.GUNPOWDER));
	}

	@Test
	void profileDefaultsApplyAndOverlayWins() {
		// The profile switches the trail off; the item overlay still wins for glowstone.
		AttributeBag defaults = Attributes.TRAIL.set(AttributeBag.EMPTY, false);
		FireworkProfile profile = new FireworkProfile(RING, null, defaults);
		assertEquals(0, count(bill(profile, AttributeBag.EMPTY), FormulaCost.GLOWSTONE));
		assertEquals(3, count(bill(profile, AttributeBag.EMPTY), FormulaCost.GUNPOWDER));

		AttributeBag overlay = Attributes.TRAIL.set(AttributeBag.EMPTY, true);
		assertEquals(1, count(bill(profile, overlay), FormulaCost.GLOWSTONE));
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
