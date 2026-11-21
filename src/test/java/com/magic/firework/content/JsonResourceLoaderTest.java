package com.magic.firework.content;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.LinkedHashMap;
import java.util.Map;

import com.magic.firework.core.Id;

import org.junit.jupiter.api.Test;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

class JsonResourceLoaderTest {
	@Test
	void theDirectoryAndExtensionAreTrimmed() {
		assertEquals(new ResourceLocation("test", "ring"),
				JsonResourceLoader.id(new ResourceLocation("test", "inks/ring.json"), "inks"));
	}

	@Test
	void aFileOutsideTheDirectoryIsNotAnEntry() {
		assertNull(JsonResourceLoader.id(new ResourceLocation("test", "shows/ring.json"), "inks"));
		assertNull(JsonResourceLoader.id(new ResourceLocation("test", "inks/ring.txt"), "inks"));
	}

	@Test
	void aRejectedFileIsDroppedNotPropagated() {
		Map<ResourceLocation, Tag> documents = new LinkedHashMap<>();
		documents.put(new ResourceLocation("test", "inks/good.json"), new CompoundTag());
		documents.put(new ResourceLocation("test", "inks/bad.json"), new CompoundTag());

		Map<Id, String> parsed = JsonResourceLoader.parse(documents, "inks", (id, tag) -> {
			if (id.path().equals("bad")) {
				throw new Exception("broken ink");
			}
			return "ok";
		}, "ink");

		assertEquals(1, parsed.size());
		assertNotNull(parsed.get(new Id("test", "good")));
		assertNull(parsed.get(new Id("test", "bad")));
	}
}
