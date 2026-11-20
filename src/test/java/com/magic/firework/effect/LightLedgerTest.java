package com.magic.firework.effect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.Level;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class LightLedgerTest {
	private static final BlockPos A = new BlockPos(1, 2, 3);
	private static final BlockPos B = new BlockPos(-4, 5, 6);

	@BeforeAll
	static void bootstrapRegistries() {
		// Level.OVERWORLD lives in the dimension registry; no server is needed for that.
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
	}

	@Test
	void roundTripsDimensionsAndPositions() {
		LightLedger ledger = new LightLedger();
		ledger.put(Level.OVERWORLD, A, 100L);
		ledger.put(Level.OVERWORLD, B, 200L);

		LightLedger loaded = LightLedger.fromTag(ledger.save(new CompoundTag()));

		assertEquals(Map.of(A, 100L, B, 200L), loaded.positions(Level.OVERWORLD));
		assertEquals(2, loaded.size());
	}

	@Test
	void removingTheLastPositionDropsTheDimension() {
		LightLedger ledger = new LightLedger();
		ledger.put(Level.OVERWORLD, A, 100L);
		ledger.remove(Level.OVERWORLD, A);

		assertTrue(ledger.isEmpty());
		assertEquals(Map.of(), ledger.positions(Level.OVERWORLD));
	}

	@Test
	void aHandEditedSaveCannotStopTheLoad() {
		CompoundTag dimensions = new CompoundTag();
		CompoundTag positions = new CompoundTag();
		positions.putLong("not-a-number", 5L);
		dimensions.put("not a dimension", positions);
		CompoundTag tag = new CompoundTag();
		tag.put("dimensions", dimensions);

		assertTrue(LightLedger.fromTag(tag).isEmpty());
		assertTrue(LightLedger.fromTag(null).isEmpty());
	}
}
