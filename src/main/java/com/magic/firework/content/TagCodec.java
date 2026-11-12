package com.magic.firework.content;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;

import com.magic.firework.core.ContentLimits;
import com.magic.firework.core.Id;
import com.magic.firework.core.Transform;
import com.magic.firework.core.Vec3;
import com.magic.firework.core.path.Path;
import com.magic.firework.core.shape.Form;
import com.magic.firework.core.shape.Ink;
import com.magic.firework.core.show.EventSpec;
import com.magic.firework.core.show.Show;
import com.magic.firework.core.show.Stamp;

import net.minecraft.nbt.ByteArrayTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.nbt.NumericTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;

/**
 * The content schema, read from NBT.
 *
 * <p>NBT is the canonical representation: an item's {@code show} value, a data pack file and a
 * library entry are the same thing after {@link com.magic.firework.content.JsonToNbt} has run,
 * so one parser serves all three. Unknown keys are ignored (forward compatibility); unknown
 * {@code type} values and dangling ids are hard errors, because silently skipping a node
 * would draw a different figure than the author wrote.
 */
public final class TagCodec {
	private TagCodec() {
	}

	// ── ink ─────────────────────────────────────────────────────────────────

	public static Ink parseInk(Id id, Tag json) throws CodecException {
		CompoundTag object = object(json, "ink " + id);
		List<Vec3> points = new ArrayList<>();
		if (object.contains("points")) {
			ListTag array = array(object.get("points"), "ink " + id + ".points");
			for (int i = 0; i < array.size(); i++) {
				ListTag pair = array(array.get(i), "ink " + id + " point #" + i);
				if (pair.size() != 2 && pair.size() != 3) {
					throw new CodecException("ink " + id + " point #" + i + " must be [u, v] or [u, v, w]");
				}
				points.add(new Vec3(number(pair.get(0)), number(pair.get(1)),
						pair.size() == 3 ? number(pair.get(2)) : 0.0));
			}
		} else if (object.contains("uv")) {
			ListTag flat = array(object.get("uv"), "ink " + id + ".uv");
			if (flat.size() % 2 != 0) {
				throw new CodecException("ink " + id + ".uv must have an even length");
			}
			for (int i = 0; i < flat.size(); i += 2) {
				points.add(new Vec3(number(flat.get(i)), number(flat.get(i + 1)), 0.0));
			}
		}
		if (points.isEmpty()) {
			throw new CodecException("ink " + id + " needs \"points\" or \"uv\"");
		}
		if (points.size() > ContentLimits.MAX_INK_POINTS) {
			throw new CodecException("ink " + id + " has more than " + ContentLimits.MAX_INK_POINTS + " points");
		}
		int[] colors = ints(object, "colors");
		double[] sizes = doubles(object, "sizes");
		if (sizes.length == 0 && object.contains("size")) {
			double size = number(object.get("size"));
			sizes = new double[points.size()];
			java.util.Arrays.fill(sizes, size);
		}
		try {
			return Ink.of(id, points.toArray(new Vec3[0]), colors, sizes);
		} catch (IllegalArgumentException exception) {
			throw new CodecException(exception.getMessage());
		}
	}

	// ── show ────────────────────────────────────────────────────────────────

	public static Show parseShow(Id id, Tag json, Function<Id, Ink> inks) throws CodecException {
		CompoundTag object = object(json, "show " + id);
		int[] palette = ints(object, "palette");
		if (palette.length == 0) {
			palette = new int[] { Show.DEFAULT_COLOR };
		}
		if (palette.length > ContentLimits.MAX_PALETTE) {
			throw new CodecException("show " + id + " has more than " + ContentLimits.MAX_PALETTE + " palette entries");
		}
		ListTag array = array(object.get("stamps"), "show " + id + ".stamps");
		if (array.size() > ContentLimits.MAX_STAMPS) {
			throw new CodecException("show " + id + " has more than " + ContentLimits.MAX_STAMPS + " stamps");
		}
		List<Stamp> stamps = new ArrayList<>(array.size());
		int[] nodes = { 0 };
		for (int i = 0; i < array.size(); i++) {
			stamps.add(stamp(array.get(i), id, "stamps[" + i + "]", inks, nodes));
		}
		return new Show(id, palette, stamps);
	}

	private static Stamp stamp(Tag json, Id showId, String where, Function<Id, Ink> inks, int[] nodes)
			throws CodecException {
		CompoundTag object = object(json, "show " + showId + ": " + where);
		Form form = form(object.get("shape"), showId, where, inks, nodes, 1);
		int at = integer(object, "at", 0);
		int duration = integer(object, "duration", 0);
		Path path = object.get("path") == null ? new Path.None() : path(object.get("path"), showId, where);
		Transform place = object.get("place") == null ? Transform.IDENTITY : place(object.get("place"), showId, where);
		double size = object.get("size") == null ? 1.0 : number(object.get("size"));
		double thickness = object.get("thickness") == null ? 0.0 : number(object.get("thickness"));
		double jitter = object.get("jitter") == null ? 0.0 : number(object.get("jitter"));
		List<EventSpec> events = events(object.get("events"), showId, where);
		return new Stamp(form, at, duration, path, place, size, thickness, jitter, events);
	}

	// ── form ────────────────────────────────────────────────────────────────

	private static Form form(Tag json, Id showId, String where, Function<Id, Ink> inks, int[] nodes, int depth)
			throws CodecException {
		if (json == null) {
			throw new CodecException("show " + showId + ": " + where + " needs \"shape\"");
		}
		if (++nodes[0] > ContentLimits.MAX_FORM_NODES) {
			throw new CodecException("show " + showId + ": " + where + " has more than "
					+ ContentLimits.MAX_FORM_NODES + " form nodes");
		}
		if (depth > ContentLimits.MAX_FORM_DEPTH) {
			throw new CodecException("show " + showId + ": " + where + " nests forms deeper than "
					+ ContentLimits.MAX_FORM_DEPTH);
		}
		if (json instanceof StringTag string) {
			return Form.leaf(lookup(string.getAsString(), showId, where, inks));
		}

		CompoundTag object = object(json, "show " + showId + ": " + where + ".shape");
		String type = string(object, "type", "shape").toLowerCase(Locale.ROOT);
		Transform place = object.get("place") == null ? Transform.IDENTITY : place(object.get("place"), showId, where);
		int colorOffset = integer(object, "colorOffset", 0);
		double size = object.get("size") == null ? 1.0 : number(object.get("size"));
		return switch (type) {
			case "shape", "ink" -> {
				String key = object.contains("shape") ? "shape" : "ink";
				yield new Form.Leaf(lookup(string(object, key, ""), showId, where, inks), place, colorOffset, size);
			}
			case "path", "plot" -> {
				Path path = path(object.get("path"), showId, where);
				int samples = integer(object, "samples", 16);
				yield new Form.Leaf(Ink.path(inlineId(showId, where), path, samples, 1.0), place, colorOffset, size);
			}
			case "union", "group" -> {
				ListTag array = array(object.get("parts"), "show " + showId + ": " + where + ".shape.parts");
				List<Form> parts = new ArrayList<>(array.size());
				for (int i = 0; i < array.size(); i++) {
					parts.add(form(array.get(i), showId, where + ".parts[" + i + "]", inks, nodes, depth + 1));
				}
				yield new Form.Group(parts, place, colorOffset, size);
			}
			case "ring" -> new Form.Ring(
					form(object.get("shape"), showId, where + ".shape", inks, nodes, depth + 1),
					integer(object, "count", 8),
					number(object.get("radius"), "show " + showId + ": " + where + ".shape.radius"),
					object.get("sweep") == null ? 360.0 : number(object.get("sweep")),
					place, colorOffset, size);
			case "array" -> new Form.Array(
					form(object.get("shape"), showId, where + ".shape", inks, nodes, depth + 1),
					integer(object, "count", 2),
					vec(object.get("step"), showId, where + ".shape.step"),
					place, colorOffset, size);
			case "mirror" -> {
				ListTag axes = array(object.get("axes"), "show " + showId + ": " + where + ".shape.axes");
				boolean x = false;
				boolean y = false;
				boolean z = false;
				for (int i = 0; i < axes.size(); i++) {
					String axis = string(axes.get(i));
					switch (axis.toLowerCase(Locale.ROOT)) {
						case "x" -> x = true;
						case "y" -> y = true;
						case "z" -> z = true;
						default -> throw new CodecException(
								"show " + showId + ": " + where + ".shape.axes[" + i + "] must be x, y or z");
					}
				}
				if (!x && !y && !z) {
					throw new CodecException("show " + showId + ": " + where + ".shape.axes must name at least one axis");
				}
				yield new Form.Mirror(
						form(object.get("shape"), showId, where + ".shape", inks, nodes, depth + 1),
						x, y, z, place, colorOffset, size);
			}
			default -> throw new CodecException(
					"show " + showId + ": " + where + ".shape has unknown type \"" + type + "\"");
		};
	}

	private static Ink lookup(String raw, Id showId, String where, Function<Id, Ink> inks) throws CodecException {
		Id id = Id.parse(raw);
		Ink ink = id == null ? null : inks.apply(id);
		if (ink == null) {
			throw new CodecException("show " + showId + ": " + where + " uses unknown shape \"" + raw + "\"");
		}
		return ink;
	}

	private static Id inlineId(Id showId, String where) {
		return new Id(showId.namespace(), showId.path() + "/" + where);
	}

	private static Path path(Tag json, Id showId, String where) throws CodecException {
		CompoundTag object = object(json, "show " + showId + ": " + where + ".path");
		String type = string(object, "type", "none").toLowerCase(Locale.ROOT);
		return switch (type) {
			case "none" -> new Path.None();
			case "line" -> new Path.Line(number(object.get("length"), "show " + showId + ": " + where + ".path.length"));
			case "arc" -> new Path.Arc(
					number(object.get("radius"), "show " + showId + ": " + where + ".path.radius"),
					number(object.get("degrees"), "show " + showId + ": " + where + ".path.degrees"));
			default -> throw new CodecException(
					"show " + showId + ": " + where + ".path has unknown type \"" + type + "\"");
		};
	}

	private static Transform place(Tag json, Id showId, String where) throws CodecException {
		CompoundTag object = object(json, "show " + showId + ": " + where + ".place");
		Vec3 pos = object.get("pos") == null ? Vec3.ZERO : vec(object.get("pos"), showId, where);
		Vec3 rot = object.get("rot") == null ? Vec3.ZERO : vec(object.get("rot"), showId, where);
		double scale = object.get("scale") == null ? 1.0 : number(object.get("scale"));
		return Transform.of(pos, Math.toRadians(rot.x), Math.toRadians(rot.y), Math.toRadians(rot.z), scale);
	}

	private static List<EventSpec> events(Tag json, Id showId, String where) throws CodecException {
		if (json == null) {
			return List.of();
		}
		ListTag array = array(json, "show " + showId + ": " + where + ".events");
		List<EventSpec> events = new ArrayList<>();
		for (int i = 0; i < array.size(); i++) {
			CompoundTag object = object(array.get(i), "show " + showId + ": " + where + ".events[" + i + "]");
			String kind = string(object, "kind", "sound");
			int at = integer(object, "at", 0);
			if (kind.equalsIgnoreCase("sound")) {
				events.add(EventSpec.sound(at, id(object, "id", showId, where),
						object.get("volume") == null ? 1.0 : number(object.get("volume")),
						object.get("pitch") == null ? 1.0 : number(object.get("pitch"))));
			} else if (kind.equalsIgnoreCase("light")) {
				events.add(EventSpec.light(at, integer(object, "ticks", 20)));
			} else {
				throw new CodecException(
						"show " + showId + ": " + where + ".events[" + i + "] unknown kind \"" + kind + "\"");
			}
		}
		return events;
	}

	// ── helpers ─────────────────────────────────────────────────────────────

	private static CompoundTag object(Tag tag, String what) throws CodecException {
		if (!(tag instanceof CompoundTag compound)) {
			throw new CodecException(what + " must be a compound");
		}
		return compound;
	}

	private static ListTag array(Tag tag, String what) throws CodecException {
		if (!(tag instanceof ListTag list)) {
			throw new CodecException(what + " must be a list");
		}
		return list;
	}

	private static String string(CompoundTag object, String key, String fallback) {
		Tag value = object.get(key);
		return value instanceof StringTag text ? text.getAsString() : fallback;
	}

	private static String string(Tag tag) throws CodecException {
		if (!(tag instanceof StringTag text)) {
			throw new CodecException("expected a string, got " + tag);
		}
		return text.getAsString();
	}

	private static int integer(CompoundTag object, String key, int fallback) {
		Tag value = object.get(key);
		return value instanceof NumericTag number ? number.getAsInt() : fallback;
	}

	private static double number(Tag tag) throws CodecException {
		if (!(tag instanceof NumericTag number)) {
			throw new CodecException("expected a number, got " + tag);
		}
		double value = number.getAsDouble();
		if (!Double.isFinite(value)) {
			throw new CodecException("expected a finite number, got " + value);
		}
		return value;
	}

	private static double number(Tag tag, String what) throws CodecException {
		if (tag == null) {
			throw new CodecException(what + " is required");
		}
		return number(tag);
	}

	private static Vec3 vec(Tag tag, Id showId, String where) throws CodecException {
		if (tag == null) {
			throw new CodecException("show " + showId + ": " + where + " needs a vector");
		}
		ListTag array = array(tag, "show " + showId + ": " + where + " vector");
		if (array.size() != 3) {
			throw new CodecException("show " + showId + ": " + where + " vector must be [x, y, z]");
		}
		return new Vec3(number(array.get(0)), number(array.get(1)), number(array.get(2)));
	}

	private static Id id(CompoundTag object, String key, Id showId, String where) throws CodecException {
		if (!object.contains(key)) {
			throw new CodecException("show " + showId + ": " + where + " needs \"" + key + "\"");
		}
		Id id = Id.parse(string(object, key, ""));
		if (id == null) {
			throw new CodecException("show " + showId + ": " + where + "." + key + " is not a valid id");
		}
		return id;
	}

	private static int[] ints(CompoundTag object, String key) throws CodecException {
		Tag value = object.get(key);
		if (value == null) {
			return new int[0];
		}
		if (value instanceof IntArrayTag array) {
			return array.getAsIntArray();
		}
		if (value instanceof LongArrayTag array) {
			long[] values = array.getAsLongArray();
			int[] result = new int[values.length];
			for (int i = 0; i < values.length; i++) {
				result[i] = (int) values[i];
			}
			return result;
		}
		if (value instanceof ByteArrayTag array) {
			byte[] values = array.getAsByteArray();
			int[] result = new int[values.length];
			for (int i = 0; i < values.length; i++) {
				result[i] = values[i] & 0xFF;
			}
			return result;
		}
		if (value instanceof ListTag list) {
			int[] result = new int[list.size()];
			for (int i = 0; i < result.length; i++) {
				result[i] = (int) Math.round(number(list.get(i)));
			}
			return result;
		}
		throw new CodecException(key + " must be an array of numbers");
	}

	private static double[] doubles(CompoundTag object, String key) throws CodecException {
		Tag value = object.get(key);
		if (value == null) {
			return new double[0];
		}
		if (value instanceof LongArrayTag array) {
			long[] values = array.getAsLongArray();
			double[] result = new double[values.length];
			for (int i = 0; i < values.length; i++) {
				result[i] = values[i];
			}
			return result;
		}
		if (value instanceof IntArrayTag array) {
			int[] values = array.getAsIntArray();
			double[] result = new double[values.length];
			for (int i = 0; i < values.length; i++) {
				result[i] = values[i];
			}
			return result;
		}
		if (value instanceof ListTag list) {
			double[] result = new double[list.size()];
			for (int i = 0; i < result.length; i++) {
				result[i] = number(list.get(i));
			}
			return result;
		}
		throw new CodecException(key + " must be an array of numbers");
	}

	public static final class CodecException extends Exception {
		public CodecException(String message) {
			super(message);
		}
	}
}
