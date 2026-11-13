package com.magic.firework.craft;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;

/** The blueprint tag round-trips and survives hand-edited, malformed input. */
class BlueprintDataTest {
	private static CompoundTag source() {
		CompoundTag source = new CompoundTag();
		source.putString("profile", "magic-firework:ring12");
		return source;
	}

	@Test
	void writtenRoundTripKeepsSourceAndCost() {
		CompoundTag source = source();
		List<Cost> cost = List.of(new Cost("minecraft:paper", 1), new Cost("minecraft:gunpowder", 3));
		CompoundTag root = BlueprintData.written(source, cost);

		assertTrue(BlueprintData.isWritten(root));
		assertEquals(source, BlueprintData.source(root));
		assertEquals(cost, BlueprintData.cost(root));
	}

	@Test
	void storedSourceIsADefensiveCopy() {
		CompoundTag source = source();
		CompoundTag root = BlueprintData.written(source, List.of());
		source.putString("profile", "changed");
		assertEquals("magic-firework:ring12", BlueprintData.source(root).getString("profile"));
	}

	@Test
	void aBlankRootIsNotWritten() {
		assertFalse(BlueprintData.isWritten(new CompoundTag()));
		assertFalse(BlueprintData.isWritten(null));
		assertTrue(BlueprintData.cost(new CompoundTag()).isEmpty());
	}

	@Test
	void malformedCostEntriesAreDropped() {
		CompoundTag root = new CompoundTag();
		ListTag list = new ListTag();
		list.add(new CompoundTag());
		list.add(new Cost("minecraft:paper", 1).save());
		list.add(new Cost("minecraft:gunpowder", 0).save());
		root.put(BlueprintData.COST, list);

		assertEquals(List.of(new Cost("minecraft:paper", 1)), BlueprintData.cost(root));
	}

	@Test
	void zeroCountsAreNotSaved() {
		assertEquals(0, Cost.saveAll(List.of(new Cost("minecraft:paper", 0))).size());
		assertNull(Cost.load(new CompoundTag()));
		assertNull(Cost.load(new Cost("minecraft:paper", 0).save()));
	}
}
