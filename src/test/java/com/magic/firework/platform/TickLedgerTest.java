package com.magic.firework.platform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.magic.firework.config.FireworkLimits;

import org.junit.jupiter.api.Test;

/** The per-tick allowance both the entity and the show service draw from. */
class TickLedgerTest {
	@Test
	void spawnsStopAtThePerTickCap() {
		TickLedger ledger = new TickLedger();

		for (int i = 0; i < FireworkLimits.maxSpawnsPerTick; i++) {
			assertTrue(ledger.trySpawn());
		}

		assertFalse(ledger.trySpawn());
		assertEquals(FireworkLimits.maxSpawnsPerTick, ledger.spawnsThisTick());
	}

	@Test
	void pointsAreGrantedUpToTheRemainingAllowance() {
		TickLedger ledger = new TickLedger();

		assertEquals(1000, ledger.claimPoints(1000));
		assertEquals(FireworkLimits.maxPointsPerTick - 1000,
				ledger.claimPoints(FireworkLimits.maxPointsPerTick * 2));
		assertEquals(0, ledger.claimPoints(1), "an exhausted ledger grants nothing");
		assertEquals(FireworkLimits.maxPointsPerTick, ledger.pointsThisTick());
	}

	@Test
	void negativeRequestsNeverRefundPoints() {
		TickLedger ledger = new TickLedger();

		assertEquals(0, ledger.claimPoints(-50));
		assertEquals(0, ledger.pointsThisTick());
	}

	@Test
	void resetStartsANewTick() {
		TickLedger ledger = new TickLedger();
		ledger.trySpawn();
		ledger.claimPoints(10);

		ledger.reset();

		assertEquals(0, ledger.spawnsThisTick());
		assertEquals(0, ledger.pointsThisTick());
		assertTrue(ledger.trySpawn());
	}
}
