package com.magic.firework.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.magic.firework.config.FireworkLimits;

import org.junit.jupiter.api.Test;

class PointBudgetTest {
	@Test
	void aTickNeverEmitsMoreThanTheBudget() {
		PointBudget budget = new PointBudget(FireworkLimits.maxClientPointsPerTick);
		budget.beginTick();

		int emitted = 0;
		for (int i = 0; i < 10_000; i++) {
			if (budget.claim()) {
				emitted++;
			}
		}

		assertEquals(FireworkLimits.maxClientPointsPerTick, emitted);
		assertEquals(10_000 - emitted, budget.droppedThisTick());
		assertEquals(0, budget.remaining());
	}

	@Test
	void theNextTickRefillsAndResetsTheDropCount() {
		PointBudget budget = new PointBudget(2);
		budget.beginTick();
		assertTrue(budget.claim());
		assertTrue(budget.claim());
		assertFalse(budget.claim());
		assertEquals(1, budget.droppedThisTick());

		budget.beginTick();

		assertEquals(2, budget.remaining());
		assertEquals(0, budget.droppedThisTick());
		assertTrue(budget.claim());
	}

	@Test
	void aNonPositiveBudgetDropsEverything() {
		PointBudget budget = new PointBudget(-1);
		budget.beginTick();

		assertFalse(budget.claim());
		assertEquals(1, budget.droppedThisTick());
	}
}
