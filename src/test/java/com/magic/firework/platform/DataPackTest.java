package com.magic.firework.platform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.magic.firework.core.Budget;
import com.magic.firework.core.Id;
import com.magic.firework.core.Seed;
import com.magic.firework.content.TagCodec;
import com.magic.firework.core.eval.Evaluate;
import com.magic.firework.core.shape.Ink;
import com.magic.firework.core.show.Show;
import com.magic.firework.content.JsonToNbt;
import com.magic.firework.program.Program;
import com.magic.firework.program.ProgramParser;

import org.junit.jupiter.api.Test;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

/** Every shipped JSON must parse, and every firework must declare its tunable op attributes. */
class DataPackTest {
	private static final Path DATA = Path.of("src/main/resources/data/magic-firework");

	private static List<Path> jsonFiles(Path directory) throws Exception {
		try (var files = Files.list(directory)) {
			return files.filter(path -> path.getFileName().toString().endsWith(".json")).sorted().toList();
		}
	}

	private static Id idOf(Path file) {
		String name = file.getFileName().toString();
		return new Id("magic-firework", name.substring(0, name.length() - ".json".length()));
	}

	@Test
	void shippedInksAndShowsParse() throws Exception {
		Map<Id, Ink> inks = new LinkedHashMap<>();
		for (Path file : jsonFiles(DATA.resolve("inks"))) {
			Id id = idOf(file);
			inks.put(id, TagCodec.parseInk(id, JsonToNbt.convert(JsonParser.parseString(Files.readString(file)))));
		}

		Map<Id, Show> shows = new LinkedHashMap<>();
		for (Path file : jsonFiles(DATA.resolve("shows"))) {
			Id id = idOf(file);
			shows.put(id, TagCodec.parseShow(id, JsonToNbt.convert(JsonParser.parseString(Files.readString(file))), inks::get));
		}

		Id flowerId = new Id("magic-firework", "flower");
		Id cubeId = new Id("magic-firework", "cube");
		assertTrue(shows.containsKey(flowerId));
		assertTrue(shows.containsKey(cubeId));

		// 24 trunk marks + 8 petals x (8 ring points + 10 line marks)
		assertEquals(168, Evaluate.evaluate(shows.get(flowerId), new Seed(4242L), Budget.of(2048))
				.stream().size());
		// 1 root mark + 12 edges x 14 samples
		assertEquals(169, Evaluate.evaluate(shows.get(cubeId), new Seed(4242L), Budget.of(2048))
				.stream().size());
	}

	@Test
	void everyFireworkNamesAnExistingShow() throws Exception {
		Set<Id> shows = new HashSet<>();
		for (Path file : jsonFiles(DATA.resolve("shows"))) {
			shows.add(idOf(file));
		}

		for (Path file : jsonFiles(DATA.resolve("fireworks"))) {
			JsonElement json = JsonParser.parseString(Files.readString(file));
			CompoundTag root = (CompoundTag) JsonToNbt.convert(json);
			String show = root.getCompound("attributes").getString("show");

			assertFalse(show.isEmpty(), file + " must declare attributes.show");
			assertTrue(shows.contains(Id.parse(show)), file + " names the unknown show " + show);
		}
	}

	@Test
	void shippedFireworksParse() throws Exception {
		for (Path file : jsonFiles(DATA.resolve("fireworks"))) {
			Id id = idOf(file);
			JsonElement json = JsonParser.parseString(Files.readString(file));
			CompoundTag root = (CompoundTag) JsonToNbt.convert(json);
			Program program = ProgramParser.parse(new ResourceLocation(id.namespace(), id.path()),
					root.get("program"));

			assertTrue(program.size() > 0, file + " has an empty program");
		}
	}
}
