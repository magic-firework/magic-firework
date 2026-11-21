package com.magic.firework.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FireworkConfigTest {
	@TempDir
	Path directory;

	@AfterEach
	void resetLimits() {
		FireworkConfig.DEFAULTS.apply();
	}

	@Test
	void aMissingFileIsTheDefaults() {
		assertEquals(FireworkConfig.DEFAULTS, FireworkConfig.load(directory.resolve("missing.json")));
	}

	@Test
	void valuesAreReadAndApplied() throws Exception {
		Path file = directory.resolve("magic-firework.json");
		Files.writeString(file, "{\"maxChildren\":1,\"maxDepth\":1,\"maxSpeed\":2.5,"
				+ "\"allowLightBlocks\":false,\"maxPointsPerShow\":10,\"maxPointsPerTick\":20}");

		FireworkConfig config = FireworkConfig.load(file);

		assertEquals(1, config.maxChildren());
		assertFalse(config.allowLightBlocks());
		config.apply();
		assertEquals(1, FireworkLimits.maxChildren);
		assertEquals(1, FireworkLimits.maxDepth);
		assertEquals(2.5, FireworkLimits.maxSpeed);
		assertFalse(FireworkLimits.allowLightBlocks);
		assertEquals(10, FireworkLimits.maxPointsPerShow);
		assertEquals(20, FireworkLimits.maxPointsPerTick);
	}

	@Test
	void outOfRangeValuesAreClamped() throws Exception {
		Path file = directory.resolve("magic-firework.json");
		Files.writeString(file, "{\"maxChildren\":-1,\"maxDepth\":999,\"maxPointsPerShow\":0,"
				+ "\"maxPointsPerTick\":0}");

		FireworkConfig config = FireworkConfig.load(file);

		assertEquals(0, config.maxChildren());
		assertEquals(FireworkLimits.maxNestingNodes, config.maxDepth());
		assertEquals(1, config.maxPointsPerShow());
		assertEquals(1, config.maxPointsPerTick());
		assertTrue(config.allowLightBlocks(), "a missing key keeps the default");
	}

	@Test
	void badJsonKeepsTheDefaults() throws Exception {
		Path file = directory.resolve("magic-firework.json");
		Files.writeString(file, "{not json");

		assertEquals(FireworkConfig.DEFAULTS, FireworkConfig.load(file));
	}
}
