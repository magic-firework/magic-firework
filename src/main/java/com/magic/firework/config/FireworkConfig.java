package com.magic.firework.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.magic.firework.MagicFirework;

/**
 * Optional server policy for the limits that operators actually need to tune.
 *
 * <p>{@code config/magic-firework.json} is read once at mod initialisation. A missing file,
 * a missing key or an unparsable file keeps the default for that value, so an empty config is
 * exactly the behaviour of the previous release. Values are clamped to the same hard bounds
 * the code already assumes; a config cannot turn off a structural content limit.
 */
public record FireworkConfig(
		int maxChildren,
		int maxDepth,
		double maxSpeed,
		boolean allowLightBlocks,
		int maxPointsPerShow,
		int maxPointsPerTick) {
	private static final int MAX_POINTS_CEILING = 65_536;
	private static final int MAX_POINTS_PER_TICK_CEILING = 1_000_000;

	public static final FireworkConfig DEFAULTS = new FireworkConfig(32, 3, 4.0, true, 2048, 8192);

	public FireworkConfig {
		maxChildren = clamp(maxChildren, 0, FireworkLimits.maxChildrenCeiling);
		maxDepth = clamp(maxDepth, 0, FireworkLimits.maxNestingNodes);
		maxSpeed = Double.isFinite(maxSpeed) && maxSpeed > 0.0 ? Math.min(maxSpeed, 16.0) : 4.0;
		maxPointsPerShow = clamp(maxPointsPerShow, 1, MAX_POINTS_CEILING);
		maxPointsPerTick = clamp(maxPointsPerTick, 1, MAX_POINTS_PER_TICK_CEILING);
	}

	public static FireworkConfig load(Path path) {
		if (path == null || !Files.isRegularFile(path)) {
			return DEFAULTS;
		}
		try {
			JsonObject json = JsonParser.parseString(Files.readString(path)).getAsJsonObject();
			return new FireworkConfig(
					intOr(json, "maxChildren", DEFAULTS.maxChildren),
					intOr(json, "maxDepth", DEFAULTS.maxDepth),
					doubleOr(json, "maxSpeed", DEFAULTS.maxSpeed),
					boolOr(json, "allowLightBlocks", DEFAULTS.allowLightBlocks),
					intOr(json, "maxPointsPerShow", DEFAULTS.maxPointsPerShow),
					intOr(json, "maxPointsPerTick", DEFAULTS.maxPointsPerTick));
		} catch (IOException | RuntimeException exception) {
			MagicFirework.LOGGER.warn("failed to read {}; keeping the default limits", path, exception);
			return DEFAULTS;
		}
	}

	/** Copy these values into the static limits read by the runtime. Call once, before play. */
	public void apply() {
		FireworkLimits.maxChildren = maxChildren;
		FireworkLimits.maxDepth = maxDepth;
		FireworkLimits.maxSpeed = maxSpeed;
		FireworkLimits.allowLightBlocks = allowLightBlocks;
		FireworkLimits.maxPointsPerShow = maxPointsPerShow;
		FireworkLimits.maxPointsPerTick = maxPointsPerTick;
	}

	private static int intOr(JsonObject json, String key, int fallback) {
		if (json.has(key) && json.get(key).isJsonPrimitive() && json.getAsJsonPrimitive(key).isNumber()) {
			return json.get(key).getAsInt();
		}
		return fallback;
	}

	private static double doubleOr(JsonObject json, String key, double fallback) {
		if (json.has(key) && json.get(key).isJsonPrimitive() && json.getAsJsonPrimitive(key).isNumber()) {
			return json.get(key).getAsDouble();
		}
		return fallback;
	}

	private static boolean boolOr(JsonObject json, String key, boolean fallback) {
		if (json.has(key) && json.get(key).isJsonPrimitive() && json.getAsJsonPrimitive(key).isBoolean()) {
			return json.get(key).getAsBoolean();
		}
		return fallback;
	}

	private static int clamp(int value, int min, int max) {
		return Math.max(min, Math.min(value, max));
	}
}
