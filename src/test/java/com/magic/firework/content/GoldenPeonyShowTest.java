package com.magic.firework.content;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import com.google.gson.JsonParser;
import com.magic.firework.attribute.AttributeBag;
import com.magic.firework.attribute.Attributes;
import com.magic.firework.core.Budget;
import com.magic.firework.core.Id;
import com.magic.firework.core.Seed;
import com.magic.firework.core.eval.Evaluate;
import com.magic.firework.core.eval.Plan;
import com.magic.firework.core.shape.Ink;
import com.magic.firework.core.show.Show;
import com.magic.firework.program.Program;
import com.magic.firework.program.ProgramParser;

import org.junit.jupiter.api.Test;

/**
 * Bakes the shipped {@code golden_peony} display through the loader and the evaluator.
 *
 * <p>Schema validity is covered by {@code tools/validate_content.py} and
 * {@link ContentSchemaTest}; this test pins the *design* facts a player can see: the
 * shell opens on the burst tick, it stays inside the per-show point budget and the radius
 * it claims, and the bake is byte-for-byte reproducible.
 */
class GoldenPeonyShowTest {
	private static final Path DATA = Path.of("src/main/resources/data/magic-firework");
	private static final Id SHOW = new Id("magic-firework", "golden_peony");
	private static final int POINTS = 1124;

	private static Ink ink(Path file) throws Exception {
		Id id = new Id("magic-firework", file.getFileName().toString().replace(".json", ""));
		return TagCodec.parseInk(id, JsonToNbt.convert(JsonParser.parseString(Files.readString(file))));
	}

	private static Show show(Map<Id, Ink> inks) throws Exception {
		Path file = DATA.resolve("shows/golden_peony.json");
		return TagCodec.parseShow(SHOW, JsonToNbt.convert(JsonParser.parseString(Files.readString(file))),
				inks::get);
	}

	private static Map<Id, Ink> inks() throws Exception {
		Map<Id, Ink> inks = new HashMap<>();
		try (var files = Files.list(DATA.resolve("inks"))) {
			for (Path file : files.filter(path -> path.toString().endsWith(".json")).toList()) {
				Ink parsed = ink(file);
				inks.put(parsed.id(), parsed);
			}
		}
		return inks;
	}

	@Test
	void bakedShellStaysInsideTheLimits() throws Exception {
		Show show = show(inks());
		assertEquals(18, show.stampCount(), "core + cage + 3 petal shells + 12 willow strands + twinkle");

		Plan plan = Evaluate.evaluate(show, new Seed(42L), Budget.of(2048));
		assertFalse(plan.isEmpty());
		// 80 core + 360 cage + 264 petals + 408 willow + 12 twinkle.
		assertEquals(POINTS, plan.stream().size(), "point total drifted from the design");
		assertEquals(0.0F, plan.stream().point(0).time(), 0.0F, "the shell opens on the burst tick");
		assertEquals(52.0F, plan.stream().point(plan.stream().size() - 1).time(), 0.0F);
		assertTrue(plan.stream().isTimeOrdered(), "the client walks the stream with one cursor");
		assertEquals(3, plan.events().size(), "large_blast, light, twinkle");
		for (var point : plan.stream().points()) {
			assertTrue(point.offset().length() < 6.0, "a shell must stay within its claimed radius");
		}
	}

	@Test
	void bakeIsDeterministic() throws Exception {
		Show show = show(inks());
		Plan first = Evaluate.evaluate(show, new Seed(7L), Budget.of(2048));
		Plan second = Evaluate.evaluate(show, new Seed(7L), Budget.of(2048));

		assertEquals(first.stream().size(), second.stream().size());
		for (int i = 0; i < first.stream().size(); i++) {
			assertEquals(first.stream().point(i), second.stream().point(i));
		}
	}

	@Test
	void profileLaunchesAndNamesTheShow() throws Exception {
		Path file = DATA.resolve("fireworks/golden_peony.json");
		var root = (net.minecraft.nbt.CompoundTag) JsonToNbt
				.convert(JsonParser.parseString(Files.readString(file)));
		Program program = ProgramParser.parse(DefinitionIds.GOLDEN_PEONY, root.get("program"));
		AttributeBag defaults = AttributeBag.load(root.getCompound("attributes"));

		assertEquals(5, program.size(), "launch, await, burst, await, end");
		assertEquals(SHOW.toString(), defaults.raw().getString(Attributes.SHOW));
		assertEquals(0.26F, defaults.raw().getFloat("flight.speed"), 1.0E-6F);
	}
}
