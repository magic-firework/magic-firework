package com.magic.firework.platform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;

import com.google.gson.JsonParser;
import com.magic.firework.content.ContentLibrary;
import com.magic.firework.content.ContentRef;
import com.magic.firework.content.ContentSnapshot;
import com.magic.firework.content.JsonToNbt;
import com.magic.firework.core.Id;
import com.magic.firework.core.shape.Ink;
import com.magic.firework.core.show.Show;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import net.minecraft.nbt.CompoundTag;

/** Library shows are cached, and a saved document invalidates the cached copy. */
class ContentServiceTest {
	private static final Id INK = new Id("test", "dot");

	private static ContentSnapshot snapshot() {
		return new ContentSnapshot(Map.of(INK, Ink.dot(INK, 1.0)), Map.of());
	}

	private static CompoundTag document(int stamps) {
		StringBuilder json = new StringBuilder("{\"palette\":[16777215],\"stamps\":[");
		for (int i = 0; i < stamps; i++) {
			if (i > 0) {
				json.append(',');
			}
			json.append("{\"shape\":\"test:dot\"}");
		}
		json.append("]}");
		return (CompoundTag) JsonToNbt.convert(JsonParser.parseString(json.toString()));
	}

	@Test
	void savedLibraryDocumentReplacesTheCachedShow(@TempDir Path directory) throws Exception {
		ContentLibrary library = new ContentLibrary(directory);
		library.load();
		ContentService service = new ContentService();
		service.accept(snapshot());
		service.bindLibrary(library);

		Id id = new Id(ContentLibrary.NAMESPACE, "cached");
		UUID author = UUID.randomUUID();
		library.save(id, document(1), author);

		Show first = service.show(new ContentRef.ById(id));
		assertNotNull(first);
		assertEquals(1, first.stampCount());

		library.save(id, document(2), author);

		Show second = service.show(new ContentRef.ById(id));
		assertNotNull(second);
		assertEquals(2, second.stampCount(), "a new library version must not serve the old parse");
	}
}
