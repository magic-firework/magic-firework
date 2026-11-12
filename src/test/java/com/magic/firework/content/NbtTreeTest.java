package com.magic.firework.content;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;

import org.junit.jupiter.api.Test;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.StringTag;

class NbtTreeTest {
	private static CompoundTag sample(boolean reversed) {
		CompoundTag tag = new CompoundTag();
		if (reversed) {
			tag.putString("z", "last");
			tag.putInt("a", 1);
		} else {
			tag.putInt("a", 1);
			tag.putString("z", "last");
		}
		tag.put("colors", new IntArrayTag(new int[] { 1, 2, 3 }));
		return tag;
	}

	@Test
	void fingerprintIgnoresCompoundKeyOrder() {
		assertEquals(NbtTree.fingerprint(sample(false)), NbtTree.fingerprint(sample(true)));
	}

	@Test
	void fingerprintDistinguishesValues() {
		CompoundTag other = sample(false);
		other.putInt("a", 2);

		assertNotEquals(NbtTree.fingerprint(sample(false)), NbtTree.fingerprint(other));
	}

	@Test
	void fingerprintKeepsListOrder() {
		CompoundTag first = new CompoundTag();
		first.put("values", new IntArrayTag(new int[] { 1, 2 }));

		CompoundTag second = new CompoundTag();
		second.put("values", new IntArrayTag(new int[] { 2, 1 }));

		assertNotEquals(NbtTree.fingerprint(first), NbtTree.fingerprint(second));
	}

	@Test
	void toJsonSortsKeysAndKeepsIntegerWidth() {
		JsonObject json = NbtTree.toJson(sample(true)).getAsJsonObject();

		assertEquals("{\"a\":1,\"colors\":[1,2,3],\"z\":\"last\"}", json.toString());
		assertTrue(json.get("a").getAsJsonPrimitive().isNumber());
	}

	@Test
	void toJsonKeepsStringsAndNestedCompounds() {
		CompoundTag nested = new CompoundTag();
		nested.putString("name", "leaf");
		nested.put("count", IntTag.valueOf(2));

		JsonObject json = NbtTree.toJson(nested).getAsJsonObject();

		assertEquals("leaf", json.get("name").getAsString());
		assertEquals(2, json.get("count").getAsInt());
	}

	@Test
	void tagParseKeepsTypesApart() {
		assertEquals(7, NbtTree.toJson(IntTag.valueOf(7)).getAsInt());
		assertEquals("x", NbtTree.toJson(StringTag.valueOf("x")).getAsString());
	}
}
