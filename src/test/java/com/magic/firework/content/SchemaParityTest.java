package com.magic.firework.content;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.magic.firework.config.FireworkLimits;
import com.magic.firework.program.Program;
import com.magic.firework.program.ProgramParser;

import org.junit.jupiter.api.Test;

import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

/**
 * The schema and the Java parser are two ends of one contract. This test reads the numbers
 * out of the schema instead of copying them, so changing one side without the other fails CI;
 * the fixture corpus is then parsed by the real parser, and the Python validator is pointed
 * at the same files by the CI step.
 */
class SchemaParityTest {
	private static final Path SCHEMA = Path.of("src/main/resources/schemas/firework.schema.json");
	private static final Path FIXTURES = Path.of("tools/fixtures");

	private static JsonObject schema() throws Exception {
		return JsonParser.parseString(Files.readString(SCHEMA)).getAsJsonObject();
	}

	private static Program parse(String fixture) throws Exception {
		Tag root = JsonToNbt.convert(JsonParser.parseString(Files.readString(FIXTURES.resolve(fixture))));
		if (!(root instanceof net.minecraft.nbt.CompoundTag profile)) {
			throw new IllegalArgumentException(fixture + " is not a profile object");
		}
		return ProgramParser.parse(new ResourceLocation("test", "fixture"), profile.get("program"));
	}

	@Test
	void theSchemaChildrenBoundsMatchTheParserLimits() throws Exception {
		JsonObject op = schema().getAsJsonObject("$defs").getAsJsonObject("op")
				.getAsJsonObject("properties");

		assertEquals(FireworkLimits.maxNestingNodes,
				op.getAsJsonObject("children").get("maxItems").getAsInt());
		assertEquals(FireworkLimits.maxNestingNodes - 1,
				op.getAsJsonObject("children").getAsJsonObject("items").get("maximum").getAsInt());
		assertEquals(FireworkLimits.maxChildrenCeiling,
				op.getAsJsonObject("count").get("maximum").getAsInt());
	}

	@Test
	void theSharedBadFixturesAreRejectedByTheParser() {
		for (String name : List.of("unknown-op-key", "spawn-both", "wait-zero")) {
			assertThrows(ProgramParser.ProgramException.class,
					() -> parse("invalid/fireworks/" + name + ".json"), name);
		}
	}

	@Test
	void theBoundaryFixtureIsAcceptedByTheParser() {
		assertDoesNotThrow(() -> parse("valid/fireworks/spawn-128-children.json"));
	}
}
