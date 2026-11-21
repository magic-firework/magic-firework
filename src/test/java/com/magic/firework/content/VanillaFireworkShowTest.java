package com.magic.firework.content;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import com.google.gson.JsonParser;
import com.magic.firework.core.Budget;
import com.magic.firework.core.Id;
import com.magic.firework.core.Seed;
import com.magic.firework.core.eval.Evaluate;
import com.magic.firework.core.eval.FrameStream;
import com.magic.firework.core.eval.Plan;
import com.magic.firework.core.shape.Ink;
import com.magic.firework.core.show.Show;
import com.magic.firework.core.show.Spark;

import org.junit.jupiter.api.Test;

/**
 * Bakes the shipped vanilla-shaped displays through the same loader and evaluator a data pack
 * uses, and pins the facts that make them read as vanilla fireworks: the shell size, the radial
 * speed, the random lifetime range, the flash and the deterministic bake.
 */
class VanillaFireworkShowTest {
	private static final Path DATA = Path.of("src/main/resources/data/magic-firework");

	@Test
	void smallBallUsesTheVanillaShellAndSparkModel() throws Exception {
		Show show = show("vanilla_ball");
		assertEquals(1, show.stampCount());
		Spark spark = show.stamps().get(0).spark();
		assertNotNull(spark);
		assertEquals(0.25, spark.speed(), 0.0);
		assertEquals(48, spark.lifetime());
		assertEquals(12, spark.lifetimeSpread());
		assertTrue(spark.flash(), "a vanilla explosion always opens with a flash");

		Plan plan = Evaluate.evaluate(show, new Seed(42L), Budget.of(2048));
		assertFalse(plan.isEmpty());
		assertEquals(98, plan.stream().size(), "vanilla small ball is the 5x5x5 cube shell");
		assertEquals(1, plan.stream().flashCount(), "one flash for one explosion");

		for (FrameStream.Point point : plan.stream().points()) {
			assertEquals(0.0, point.offset().length(), 1.0E-9, "vanilla sparks start at the burst");
			assertTrue(point.hasMotion(), "every point is a spark, not a static puff");
			assertTrue(point.lifetime() >= 48 && point.lifetime() < 60,
					"vanilla lifetime is 48 + rand(12)");
			assertEquals(0.25, point.velocity().length(), 0.2,
					"a ball spark keeps one radial speed");
			assertFalse(point.trail());
			assertFalse(point.flicker());
		}
		assertEquals(0.0F, plan.stream().flash(0).time(), 0.0F);
		assertEquals(0xFFFFFFFF, plan.stream().flash(0).argb());
	}

	@Test
	void largeBallIsTheNineCubeShellAtTwiceTheSpeed() throws Exception {
		Show show = show("vanilla_large_ball");
		Plan plan = Evaluate.evaluate(show, new Seed(7L), Budget.of(4096));

		assertEquals(386, plan.stream().size(), "vanilla large ball is the 9x9x9 cube shell");
		assertTrue(plan.stream().points().stream()
				.allMatch(point -> Math.abs(point.velocity().length() - 0.5) < 0.25));
	}

	@Test
	void bakingAVanillaShowIsDeterministic() throws Exception {
		Show show = show("vanilla_ball");
		Plan first = Evaluate.evaluate(show, new Seed(11L), Budget.of(2048));
		Plan second = Evaluate.evaluate(show, new Seed(11L), Budget.of(2048));

		assertEquals(first.stream().size(), second.stream().size());
		for (int i = 0; i < first.stream().size(); i++) {
			assertEquals(first.stream().point(i), second.stream().point(i));
		}
	}

	private static Show show(String name) throws Exception {
		Map<Id, Ink> inks = inks();
		Path file = DATA.resolve("shows/" + name + ".json");
		return TagCodec.parseShow(new Id("magic-firework", name),
				JsonToNbt.convert(JsonParser.parseString(Files.readString(file))), inks::get);
	}

	private static Map<Id, Ink> inks() throws Exception {
		Map<Id, Ink> inks = new HashMap<>();
		try (var files = Files.list(DATA.resolve("inks"))) {
			for (Path file : files.filter(path -> path.toString().endsWith(".json")).toList()) {
				Id id = new Id("magic-firework", file.getFileName().toString().replace(".json", ""));
				inks.put(id, TagCodec.parseInk(id,
						JsonToNbt.convert(JsonParser.parseString(Files.readString(file)))));
			}
		}
		return inks;
	}
}
