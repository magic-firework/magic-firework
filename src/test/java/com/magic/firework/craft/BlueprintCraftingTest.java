package com.magic.firework.craft;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class BlueprintCraftingTest {
	private static final List<Cost> BILL = List.of(
			new Cost(FormulaCost.PAPER, 1),
			new Cost(FormulaCost.GUNPOWDER, 3));

	@Test
	void aFullBillIsDeductedAndLeavesTheExtras() {
		Map<String, Integer> before = Map.of(
				FormulaCost.PAPER, 2,
				FormulaCost.GUNPOWDER, 4,
				"minecraft:glowstone_dust", 1);

		Map<String, Integer> after = BlueprintCrafting.pay(BILL, before);

		assertEquals(Map.of(FormulaCost.PAPER, 1, FormulaCost.GUNPOWDER, 1,
				"minecraft:glowstone_dust", 1), after);
		assertEquals(2, before.get(FormulaCost.PAPER), "the input map is not mutated");
	}

	@Test
	void aMissingIngredientRejectsTheCraft() {
		assertNull(BlueprintCrafting.pay(BILL, Map.of(FormulaCost.PAPER, 1, FormulaCost.GUNPOWDER, 2)));
	}
}
