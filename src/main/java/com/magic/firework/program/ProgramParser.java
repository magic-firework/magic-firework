package com.magic.firework.program;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import com.magic.firework.attribute.Attributes;
import com.magic.firework.config.FireworkLimits;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NumericTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

/**
 * Reads the op list of a profile.
 *
 * <pre>
 * data/&lt;ns&gt;/fireworks/ring.json
 * {
 *   "attributes": { "show": "magic-firework:ring" },
 *   "program": [
 *     { "op": "launch", "speed": 0.12 },
 *     { "op": "wait", "ticks": 35 },
 *     { "op": "burst" },
 *     { "op": "end" }
 *   ]
 * }
 * </pre>
 *
 * <p>Ops run in list order, starting on the tick the frame is pushed. The only way a program
 * spends time is {@link Op.Wait}: {@code launch}, {@code wait}, {@code burst} is a rocket that
 * rises for that many ticks and then bursts.
 *
 * <p>Every key an op carries is checked against the keys that op accepts, so a typo or a field
 * from an older format is a hard error for that profile instead of being silently ignored. The
 * store logs it and keeps the previous version of the profile.
 */
public final class ProgramParser {
	/** The keys each op accepts; {@code op} is always one of them. Kept in step with the schema. */
	private static final Map<String, Set<String>> KEYS = Map.of(
			"launch", Set.of("op", "speed"),
			"burst", Set.of("op"),
			"call", Set.of("op", "profile"),
			"spawn", Set.of("op", "profile", "children", "count", "delay"),
			"sound", Set.of("op", "id", "volume", "pitch"),
			"light", Set.of("op", "ticks"),
			"wait", Set.of("op", "ticks"),
			"end", Set.of("op"));

	private ProgramParser() {
	}

	public static Program parse(ResourceLocation id, Tag tag) throws ProgramException {
		if (!(tag instanceof ListTag list)) {
			throw new ProgramException("program must be a list of ops");
		}
		List<Op> ops = new ArrayList<>(list.size());
		for (int i = 0; i < list.size(); i++) {
			if (!(list.get(i) instanceof CompoundTag compound)) {
				throw new ProgramException("op #" + i + " is not an object");
			}
			ops.add(parseOp(i, compound));
		}
		return new Program(id, ops);
	}

	private static Op parseOp(int index, CompoundTag tag) throws ProgramException {
		String name = tag.getString("op");
		if (name.isEmpty()) {
			throw new ProgramException("op #" + index + " has no \"op\" key");
		}
		String op = name.toLowerCase(Locale.ROOT);
		checkKeys(index, op, tag);
		return switch (op) {
			case "launch" -> new Op.Launch(number(tag, "speed", Attributes.SPEED.def(), index));
			case "burst" -> new Op.Burst();
			case "call" -> new Op.Call(profile(tag, index));
			case "spawn" -> spawn(tag, index);
			case "sound" -> new Op.Sound(
					id(tag.getString("id"), index, "id"),
					number(tag, "volume", Attributes.SOUND_VOLUME.def(), index),
					number(tag, "pitch", Attributes.SOUND_PITCH.def(), index));
			case "light" -> new Op.Light(tag.contains("ticks")
					? Math.max(0, tag.getInt("ticks"))
					: Attributes.LIGHT_TICKS.def());
			case "wait" -> wait(tag, index);
			case "end" -> new Op.End();
			default -> throw new ProgramException("op #" + index + " has unknown type \"" + name + "\"");
		};
	}

	/** Rejects keys the op does not take, so a stale or misspelled field cannot be ignored. */
	private static void checkKeys(int index, String op, CompoundTag tag) throws ProgramException {
		Set<String> allowed = KEYS.get(op);
		if (allowed == null) {
			return; // unknown op: reported by the switch, with a better message
		}
		for (String key : tag.getAllKeys()) {
			if (!allowed.contains(key)) {
				throw new ProgramException("op #" + index + " (" + op + ") does not take \"" + key
						+ "\"; it takes " + String.join(", ", allowed.stream().sorted().toList()));
			}
		}
	}

	/**
	 * {@code { "op": "wait" }} spends the rocket's flight time, which is the number the crafting
	 * table edits; {@code { "op": "wait", "ticks": n }} spends a literal count instead.
	 *
	 * <p>An explicit length must be positive: {@code "ticks": 0} would be a wait that waits,
	 * which is an authoring mistake, not a value.
	 */
	private static Op.Wait wait(CompoundTag tag, int index) throws ProgramException {
		if (!tag.contains("ticks")) {
			return new Op.Wait.Flight();
		}
		int ticks = tag.getInt("ticks");
		if (ticks < 1) {
			throw new ProgramException("op #" + index + " waits for " + ticks + " ticks; a wait is at least 1");
		}
		if (ticks > FireworkLimits.maxWaitTicks) {
			throw new ProgramException("op #" + index + " waits for " + ticks + " ticks; at most "
					+ FireworkLimits.maxWaitTicks + " are allowed");
		}
		return new Op.Wait.Ticks(ticks);
	}

	/**
	 * A spawn op names what it creates in exactly one of two ways, and the parser is the place
	 * that decides which: {@code children} lists item slots, {@code profile} names content.
	 *
	 * <p>{@code count} expands here rather than in the interpreter, so the op the runtime sees is
	 * already a flat list of concrete requests and the two forms cannot drift apart later.
	 */
	private static Op.Spawn spawn(CompoundTag tag, int index) throws ProgramException {
		int delay = Math.max(0, tag.getInt("delay"));
		boolean slots = tag.contains("children");
		boolean named = tag.contains("profile");
		if (slots && named) {
			throw new ProgramException("op #" + index
					+ " has both \"profile\" and \"children\": a spawn names content or item slots, not both");
		}
		if (slots) {
			return new Op.Spawn(childSlots(tag, index), delay);
		}
		if (named) {
			ResourceLocation profile = profile(tag, index);
			int count = Math.max(1, Math.min(tag.getInt("count"), FireworkLimits.maxChildrenCeiling));
			List<SpawnTarget> targets = new ArrayList<>(count);
			for (int i = 0; i < count; i++) {
				targets.add(SpawnTarget.of(profile));
			}
			return new Op.Spawn(targets, delay);
		}
		throw new ProgramException("op #" + index
				+ " is a spawn with neither \"profile\" (content) nor \"children\" (item slots)");
	}

	private static List<SpawnTarget> childSlots(CompoundTag tag, int index) throws ProgramException {
		if (tag.contains("count")) {
			throw new ProgramException("op #" + index
					+ " combines \"count\" with \"children\"; repeat the slot in the list instead");
		}
		Tag raw = tag.get("children");
		if (!(raw instanceof ListTag list)) {
			throw new ProgramException("op #" + index + " \"children\" must be a list of slot indices");
		}
		if (list.isEmpty()) {
			throw new ProgramException("op #" + index + " has an empty \"children\" list");
		}
		if (list.size() > FireworkLimits.maxNestingNodes) {
			throw new ProgramException("op #" + index + " names " + list.size() + " children, at most "
					+ FireworkLimits.maxNestingNodes + " are allowed");
		}
		List<SpawnTarget> targets = new ArrayList<>(list.size());
		for (int i = 0; i < list.size(); i++) {
			if (!(list.get(i) instanceof NumericTag)) {
				throw new ProgramException("op #" + index + " children[" + i + "] is not an integer slot");
			}
			int slot = list.getInt(i);
			if (slot < 0 || slot >= FireworkLimits.maxNestingNodes) {
				throw new ProgramException("op #" + index + " children[" + i + "] = " + slot
						+ ", slots run 0.." + (FireworkLimits.maxNestingNodes - 1));
			}
			targets.add(SpawnTarget.slot(slot));
		}
		return targets;
	}

	private static ResourceLocation profile(CompoundTag tag, int index) throws ProgramException {
		return id(tag.getString("profile"), index, "profile");
	}

	private static ResourceLocation id(String value, int index, String key) throws ProgramException {
		ResourceLocation id = ResourceLocation.tryParse(value);
		if (id == null || value.isEmpty()) {
			throw new ProgramException("op #" + index + " has an invalid \"" + key + "\": \"" + value + "\"");
		}
		return id;
	}

	private static float number(CompoundTag tag, String key, float def, int index) throws ProgramException {
		if (!tag.contains(key)) {
			return def;
		}
		return number(tag.get(key), key, index);
	}

	private static float number(Tag tag, String key, int index) throws ProgramException {
		if (!(tag instanceof NumericTag numeric)) {
			throw new ProgramException("op #" + index + " \"" + key + "\" must be a number");
		}
		double value = numeric.getAsDouble();
		if (Double.isNaN(value) || Double.isInfinite(value)) {
			throw new ProgramException("op #" + index + " has a non-finite \"" + key + "\"");
		}
		return (float) value;
	}

	/** One rejected program: the caller logs it and keeps the previously loaded version. */
	public static class ProgramException extends Exception {
		private static final long serialVersionUID = 1L;

		public ProgramException(String message) {
			super(message);
		}
	}
}
