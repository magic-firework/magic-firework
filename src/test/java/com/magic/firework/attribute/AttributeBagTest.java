package com.magic.firework.attribute;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;

class AttributeBagTest {
	@Test
	void unknownKeysSurviveRoundTrip() {
		AttributeBag bag = AttributeBag.EMPTY
				.withInt("future.unknown", 42)
				.withString("another.mod:flag", "yes");

		AttributeBag reloaded = AttributeBag.load(bag.save());

		assertEquals(42, reloaded.raw().getInt("future.unknown"));
		assertEquals("yes", reloaded.raw().getString("another.mod:flag"));
	}

	@Test
	void overlayWinsButKeepsOtherKeys() {
		AttributeBag base = AttributeBag.EMPTY.withFloat("flight.speed", 0.1F).withInt("keep.me", 7);
		AttributeBag overlay = AttributeBag.EMPTY.withFloat("flight.speed", 0.9F);

		AttributeBag merged = base.overlay(overlay);

		assertEquals(0.9F, merged.raw().getFloat("flight.speed"), 1.0E-6);
		assertEquals(7, merged.raw().getInt("keep.me"));
	}

	@Test
	void specsClampAndDefault() {
		AttributeBag outOfRange = AttributeBag.EMPTY.withFloat(Attributes.SPEED.key(), 99.0F);
		assertEquals(4.0F, Attributes.SPEED.get(outOfRange), 1.0E-6);
		assertEquals(0.1F, Attributes.SPEED.get(AttributeBag.EMPTY), 1.0E-6);
	}

	@Test
	void bagIsImmutable() {
		AttributeBag original = AttributeBag.EMPTY.withInt("a", 1);
		AttributeBag changed = original.withInt("a", 2);

		assertEquals(1, original.raw().getInt("a"));
		assertEquals(2, changed.raw().getInt("a"));
	}

	@Test
	void jsonDirectionIsPreserved() {
		CompoundTag source = new CompoundTag();
		source.put("trail.colors", new net.minecraft.nbt.IntArrayTag(new int[] {1, 2, 3}));

		AttributeBag bag = AttributeBag.load(source);
		AttributeBag copy = AttributeBag.load(bag.save());

		assertTrue(copy.raw().contains("trail.colors"));
		assertEquals(3, copy.raw().getIntArray("trail.colors").length);
	}

	@Test
	void missingKeyIsAbsent() {
		assertFalse(AttributeBag.EMPTY.has(Attributes.SPEED.key()));
		assertEquals(AttributeBag.EMPTY, AttributeBag.load(new CompoundTag()));
		AttributeBag bag = AttributeBag.EMPTY.with("x", IntTag.valueOf(1));
		assertEquals(1, bag.raw().getInt("x"));
	}
}
