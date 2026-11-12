package com.magic.firework.content;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.junit.jupiter.api.Test;

/**
 * Keeps the JSON Schemas and the shipped content in step.
 *
 * <p>This is the cheap half of the contract: every data file must name the schema for its
 * directory, and every schema must be a well-formed JSON document with an {@code $id} so
 * relative {@code $ref}s resolve. Full instance validation lives in
 * {@code tools/validate_content.py} because it needs a JSON Schema implementation.
 */
class ContentSchemaTest {
	private static final Path DATA = Path.of("src/main/resources/data");
	private static final Path SCHEMAS = Path.of("src/main/resources/schemas");
	private static final Map<String, String> SCHEMA_FOR = Map.of(
			"inks", "ink.schema.json",
			"shows", "show.schema.json",
			"fireworks", "firework.schema.json");

	@Test
	void schemasAreWellFormed() throws Exception {
		try (var files = Files.list(SCHEMAS)) {
			List<Path> schemas = files
					.filter(path -> path.getFileName().toString().endsWith(".schema.json"))
					.sorted()
					.toList();
			assertEquals(4, schemas.size(), "common + one per content kind");
			for (Path file : schemas) {
				String text = Files.readString(file);
				JsonObject schema = JsonParser.parseString(text).getAsJsonObject();
				assertTrue(schema.get("$schema").getAsString().contains("2020-12"),
						file + " should declare draft 2020-12");
				// An absolute $id makes editors fetch a URL that does not exist. Keep every
				// schema local so $refs resolve against the files on disk instead.
				assertFalse(schema.has("$id"), file + " must not declare a remote $id");
				assertFalse(text.contains("magic-firework.dev"),
						file + " must not point an editor at the network");
			}
		}
	}

	@Test
	void everyShippedFileDeclaresItsSchema() throws Exception {
		try (var namespaces = Files.list(DATA)) {
			for (Path namespace : namespaces.filter(Files::isDirectory).toList()) {
				for (Map.Entry<String, String> kind : SCHEMA_FOR.entrySet()) {
					Path directory = namespace.resolve(kind.getKey());
					if (!Files.isDirectory(directory)) {
						continue;
					}
					try (var files = Files.list(directory)) {
						for (Path file : files
								.filter(path -> path.getFileName().toString().endsWith(".json"))
								.toList()) {
							assertDeclares(file, kind.getValue());
						}
					}
				}
			}
		}
	}

	private static void assertDeclares(Path file, String expected) throws Exception {
		JsonObject root = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
		assertTrue(root.has("$schema"), file + " must declare $schema");
		Path resolved = file.getParent().resolve(root.get("$schema").getAsString()).normalize();
		assertEquals(SCHEMAS.resolve(expected).normalize(), resolved,
				file + " points at the wrong schema");
	}
}
