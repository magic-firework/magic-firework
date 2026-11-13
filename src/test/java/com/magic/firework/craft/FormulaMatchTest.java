package com.magic.firework.craft;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

/** Grid matching is exact: no extra, no missing, no free craft. */
class FormulaMatchTest {
	@Test
	void exactBillMatches() {
		assertTrue(FormulaMatch.matches(List.of(new Cost("minecraft:paper", 1),
				new Cost("minecraft:gunpowder", 3)), Map.of("minecraft:paper", 1, "minecraft:gunpowder", 3)));
	}

	@Test
	void missingOrExtraMaterialsDoNotMatch() {
		List<Cost> cost = List.of(new Cost("minecraft:paper", 1), new Cost("minecraft:gunpowder", 3));
		assertFalse(FormulaMatch.matches(cost, Map.of("minecraft:paper", 1, "minecraft:gunpowder", 2)));
		assertFalse(FormulaMatch.matches(cost, Map.of("minecraft:paper", 1, "minecraft:gunpowder", 4)));
		assertFalse(FormulaMatch.matches(cost, Map.of("minecraft:paper", 1, "minecraft:gunpowder", 3,
				"minecraft:glowstone_dust", 1)));
	}

	@Test
	void anEmptyBillNeverMatches() {
		assertFalse(FormulaMatch.matches(List.of(), Map.of()));
		assertFalse(FormulaMatch.matches(List.of(new Cost("minecraft:paper", 0)), Map.of()));
	}

	@Test
	void duplicateIdsInTheBillAreSummed() {
		List<Cost> cost = List.of(new Cost("minecraft:paper", 1), new Cost("minecraft:paper", 2));
		assertTrue(FormulaMatch.matches(cost, Map.of("minecraft:paper", 3)));
	}
}
