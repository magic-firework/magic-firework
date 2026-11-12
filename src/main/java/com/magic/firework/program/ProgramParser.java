package com.magic.firework.program;

import java.util.ArrayList;
import java.util.List;

import com.magic.firework.attribute.AttributeBag;
import com.magic.firework.attribute.Attributes;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NumericTag;
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
 *     { "op": "launch", "direction": "up", "speed": 0.12 },
 *     { "op": "await", "ticks": 50 },
 *     { "op": "burst" },
 *     { "op": "spawn", "program": "magic-firework:ring", "count": 4,
 *       "spread": 0.12, "delay": 5 },
 *     { "op": "await", "ticks": 20 },
 *     { "op": "end" }
 *   ]
 * }
 * </pre>
 *
 * An unknown op is a hard error for that profile: silently skipping it would run a
 * different show than the author wrote. The store logs and keeps the previous version.
 */
public final class ProgramParser {
	private ProgramParser() {
	}

	public static Program parse(ResourceLocation id, Tag tag) throws ProgramException {
		if (!(tag instanceof ListTag list)) {
			throw new ProgramException("program must be a list of ops");
		}
		List<Op> ops = new ArrayList<>(list.size());
		for (int i = 0; i < list.size(); i++) {
			Tag element = list.get(i);
			if (!(element instanceof CompoundTag compound)) {
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
		return switch (name.toLowerCase(java.util.Locale.ROOT)) {
			case "launch" -> new Op.Launch(
					Op.Direction.byName(tag.getString("direction")),
					vector(tag, index),
					number(tag, "speed", Attributes.SPEED.def(), index));
			case "await" -> new Op.Await(condition(tag, index));
			case "set" -> new Op.Set(AttributeBag.load(tag.getCompound("attributes")));
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

	private static Cond condition(CompoundTag tag, int index) throws ProgramException {
		Cond primary = null;
		if (tag.contains("ticks")) {
			primary = new Cond.Ticks(tag.getInt("ticks"));
		} else if (tag.contains("height")) {
			primary = new Cond.HeightAbove(tag.getDouble("height"));
		} else if (tag.contains("below")) {
			primary = new Cond.HeightBelow(tag.getDouble("below"));
		} else if (tag.getBoolean("collision")) {
			primary = new Cond.Collision();
		}
		if (primary == null) {
			// Code-side default: an await with no condition waits for the fuse.
			primary = new Cond.Ticks(Attributes.FUSE_TICKS.def());
		}
		if (tag.contains("timeout")) {
			return new Cond.Or(primary, new Cond.Ticks(tag.getInt("timeout")));
		}
		return primary;
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
