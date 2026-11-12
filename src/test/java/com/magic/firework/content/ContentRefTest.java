package com.magic.firework.content;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.magic.firework.core.Id;

import org.junit.jupiter.api.Test;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.StringTag;

class ContentRefTest {
	@Test
	void parsesIdsAndInlineDocuments() {
		assertEquals(new ContentRef.ById(new Id("magic-firework", "ring")),
				ContentRef.parse(StringTag.valueOf("magic-firework:ring")));
		assertInstanceOf(ContentRef.Inline.class, ContentRef.parse(new CompoundTag()));
		assertNull(ContentRef.parse(IntTag.valueOf(1)));
		assertNull(ContentRef.parse(StringTag.valueOf("not an id")));
	}

	@Test
	void inlineFingerprintIgnoresKeyOrder() {
		CompoundTag first = new CompoundTag();
		first.putString("b", "x");
		first.putInt("a", 1);

		CompoundTag second = new CompoundTag();
		second.putInt("a", 1);
		second.putString("b", "x");

		assertEquals(new ContentRef.Inline(first).fingerprint(), new ContentRef.Inline(second).fingerprint());
	}

	@Test
	void inlineDocumentIsDefensive() {
		CompoundTag document = new CompoundTag();
		document.putInt("a", 1);
		ContentRef.Inline ref = new ContentRef.Inline(document);

		document.putInt("a", 2);

		assertEquals(1, ref.document().getInt("a"));
	}

	@Test
	void inlineRefsWithTheSameContentAreEqual() {
		CompoundTag document = new CompoundTag();
		document.putInt("a", 1);

		assertEquals(new ContentRef.Inline(document), new ContentRef.Inline(document.copy()));
	}
}
