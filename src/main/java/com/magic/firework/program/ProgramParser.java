package com.magic.firework.program;

import java.util.ArrayList;
import java.util.List;

import com.magic.firework.attribute.Attributes;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NumericTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

/**
 * Reads the instruction list of a profile.
 *
 * <pre>
 * data/&lt;ns&gt;/fireworks/ring.json
 * {
 *   "attributes": { "show": "magic-firework:ring", "fuse.ticks": 35 },
 *   "program": [
 *     { "op": "launch" },
 *     { "op": "burst", "at": "fuse" },
 *     { "op": "end" }
 *   ]
 * }
 * </pre>
 *
 * Every op may carry {@code at} (ticks after the frame starts, or the literal {@code "fuse"}
 * for {@code fuse.ticks}) and {@code when} (a physics guard). Omitting {@code at} runs the op
 * on the same tick as the previous one, so a plain sequence is still read top to bottom.
 *
 * <p>An unknown op is a hard error for that profile: silently skipping it would run a
 * different show than the author wrote. The store logs and keeps the previous version.
 */
public final class ProgramParser {
	private ProgramParser() {
	}

	public static Program parse(ResourceLocation id, Tag tag) throws ProgramException {
		if (!(tag instanceof ListTag list)) {
			throw new ProgramException("program must be a list of ops");
		}
		List<Instruction> instructions = new ArrayList<>(list.size());
		int previousAt = 0;
		for (int i = 0; i < list.size(); i++) {
			Tag element = list.get(i);
			if (!(element instanceof CompoundTag compound)) {
				throw new ProgramException("op #" + i + " is not an object");
			}
			int at = at(compound, i, previousAt);
			if (at >= 0 && previousAt >= 0 && at < previousAt) {
				throw new ProgramException("op #" + i + " has \"at\": " + at
						+ ", which is before the previous op's " + previousAt);
			}
			instructions.add(new Instruction(parseOp(i, compound), at, when(compound, i)));
			previousAt = at;
		}
		return new Program(id, instructions);
	}

	private static Op parseOp(int index, CompoundTag tag) throws ProgramException {
		String name = tag.getString("op");
		if (name.isEmpty()) {
			throw new ProgramException("op #" + index + " has no \"op\" key");
		}
		return switch (name.toLowerCase(java.util.Locale.ROOT)) {
			case "launch" -> new Op.Launch(
					Op.Direction.byName(tag.getString("direction")),
					vector(tag, index),
					number(tag, "speed", Attributes.SPEED.def(), index));
			case "burst" -> new Op.Burst();
			case "call" -> new Op.Call(program(tag, index));
			case "spawn" -> new Op.Spawn(
					program(tag, index),
					Math.max(1, tag.getInt("count")),
					number(tag, "spread", 0.0F, index),
					Math.max(0, tag.getInt("delay")));
			case "sound" -> new Op.Sound(
					id(tag.getString("id"), index, "id"),
					number(tag, "volume", Attributes.SOUND_VOLUME.def(), index),
					number(tag, "pitch", Attributes.SOUND_PITCH.def(), index));
			case "light" -> new Op.Light(tag.contains("ticks")
					? Math.max(0, tag.getInt("ticks"))
					: Attributes.LIGHT_TICKS.def());
			case "end" -> new Op.End();
			default -> throw new ProgramException("op #" + index + " has unknown type \"" + name + "\"");
		};
	}

	/**
	 * @param previousAt the offset of the previous instruction, inherited when {@code at} is
	 *                   omitted so a list reads as a sequence of same-tick ops unless stated
	 */
	private static int at(CompoundTag tag, int index, int previousAt) throws ProgramException {
		Tag raw = tag.get("at");
		if (raw == null) {
			return previousAt;
		}
		if (raw instanceof StringTag literal) {
			if ("fuse".equalsIgnoreCase(literal.getAsString())) {
				return Instruction.FUSE_AT;
			}
			throw new ProgramException("op #" + index + " has an invalid \"at\": \""
					+ literal.getAsString() + "\" (expected a tick or \"fuse\")");
		}
		if (!(raw instanceof NumericTag)) {
			throw new ProgramException("op #" + index + " \"at\" must be a tick or \"fuse\"");
		}
		int value = tag.getInt("at");
		if (value < 0) {
			throw new ProgramException("op #" + index + " has a negative \"at\": " + value);
		}
		return value;
	}

	private static When when(CompoundTag tag, int index) throws ProgramException {
		Tag raw = tag.get("when");
		if (raw == null) {
			return When.NONE;
		}
		if (!(raw instanceof CompoundTag guard)) {
			throw new ProgramException("op #" + index + " \"when\" must be an object");
		}
		Double heightAbove = guard.contains("height") ? guard.getDouble("height") : null;
		Double heightBelow = guard.contains("below") ? guard.getDouble("below") : null;
		boolean collision = guard.getBoolean("collision");
		int timeout = guard.contains("timeout") ? Math.max(0, guard.getInt("timeout")) : -1;
		When when = new When(heightAbove, heightBelow, collision, timeout);
		if (when.isEmpty()) {
			throw new ProgramException("op #" + index + " \"when\" needs \"height\", \"below\" or \"collision\"");
		}
		return when;
	}

	private static ResourceLocation program(CompoundTag tag, int index) throws ProgramException {
		return id(tag.getString("program"), index, "program");
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
		double value = tag.getDouble(key);
		if (Double.isNaN(value) || Double.isInfinite(value)) {
			throw new ProgramException("op #" + index + " has a non-finite \"" + key + "\"");
		}
		return (float) value;
	}

	private static Vec3 vector(CompoundTag tag, int index) throws ProgramException {
		Tag raw = tag.get("vector");
		if (raw == null) {
			return new Vec3(0.0, 1.0, 0.0);
		}
		// Read element-wise instead of getList(key, TAG_DOUBLE): JSON number decoding
		// may produce int tags, and a type mismatch would silently yield an empty list.
		if (!(raw instanceof ListTag list) || list.size() != 3) {
			throw new ProgramException("op #" + index + " \"vector\" must be [x, y, z]");
		}
		for (int i = 0; i < 3; i++) {
			if (!(list.get(i) instanceof NumericTag)) {
				throw new ProgramException("op #" + index + " \"vector\" must contain numbers");
			}
		}
		return new Vec3(list.getDouble(0), list.getDouble(1), list.getDouble(2));
	}

	public static final class ProgramException extends Exception {
		public ProgramException(String message) {
			super(message);
		}
	}
}
