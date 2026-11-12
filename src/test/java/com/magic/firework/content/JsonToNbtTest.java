package com.magic.firework.content;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;

import org.junit.jupiter.api.Test;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

class JsonToNbtTest {
	private static CompoundTag convert(String json) {
		return (CompoundTag) JsonToNbt.convert(JsonParser.parseString(json));
	}

	@Test
	void keepsIntegersAndDoublesApart() {
		CompoundTag tag = convert("""
				{ "count": 3, "scale": 1.5, "name": "x" }
				""");

		assertEquals(Tag.TAG_INT, tag.get("count").getId());
		assertEquals(Tag.TAG_DOUBLE, tag.get("scale").getId());
		assertEquals(Tag.TAG_STRING, tag.get("name").getId());
	}

	@Test
	void widensMixedNumericArraysInsteadOfDroppingElements() {
		CompoundTag tag = convert("""
				{ "values": [1, 2.5, 3] }
				""");

		ListTag values = (ListTag) tag.get("values");

		assertEquals(3, values.size(), "no element may be silently dropped");
		assertTrue(values.stream().allMatch(element -> element.getId() == Tag.TAG_DOUBLE));
		assertEquals(2.5, values.getDouble(1), 0.0);
	}

	@Test
	void arraysOfArraysStayNested() {
		CompoundTag tag = convert("""
				{ "points": [[0.0, 0.0], [1.0, 0.5, 2.0]] }
				""");

		ListTag points = (ListTag) tag.get("points");

		assertEquals(2, points.size());
		assertEquals(Tag.TAG_LIST, points.get(1).getId());
		assertEquals(3, points.getList(1).size());
	}

	@Test
	void arraysThatCannotBeHomogeneousAreRejected() {
		assertThrows(RuntimeException.class, () -> convert("""
				{ "mixed": [1, "text"] }
				"""));
	}
}
