package com.magic.firework.content;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import net.minecraft.nbt.ByteArrayTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.nbt.NumericTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;

/**
 * The one canonical view of an NBT value.
 *
 * <p>NBT compounds are backed by a {@link java.util.HashMap}, so two tags that mean the same
 * thing may serialise in different orders. Everything that needs identity (fingerprints,
 * exports, diffing) goes through this class, which sorts compound keys and preserves list
 * order. NBT is the canonical representation of content; JSON is only an import/export
 * format, precisely so {@code float} vs {@code double} survives a round trip.
 */
public final class NbtTree {
	private NbtTree() {
	}

	/** Stable 64-bit FNV-1a fingerprint of the canonical bytes of {@code tag}. */
	public static long fingerprint(Tag tag) {
		ByteArrayOutputStream bytes = new ByteArrayOutputStream(256);
		try (DataOutputStream out = new DataOutputStream(bytes)) {
			write(tag, out);
		} catch (IOException exception) {
			// A ByteArrayOutputStream cannot fail; surface the impossible case loudly.
			throw new IllegalStateException("cannot serialise NBT", exception);
		}
		long hash = 0xCBF29CE484222325L;
		for (byte value : bytes.toByteArray()) {
			hash ^= value & 0xFFL;
			hash *= 0x100000001B3L;
		}
		return hash;
	}

	/**
	 * Convert to JSON for export. Compounds are key-sorted. JSON has no integer width, so
	 * {@code byte}/{@code short}/{@code float} widen to {@code int}/{@code double}: export is a
	 * human- and data-pack-readable snapshot, not a byte-exact round trip. The content schema
	 * only uses string, int, long, double, list, compound and int arrays, which do round-trip.
	 */
	public static JsonElement toJson(Tag tag) {
		if (tag == null) {
			return JsonNull.INSTANCE;
		}
		return switch (tag.getId()) {
			case Tag.TAG_BYTE, Tag.TAG_SHORT, Tag.TAG_INT, Tag.TAG_LONG ->
					new JsonPrimitive(((NumericTag) tag).getAsLong());
			case Tag.TAG_FLOAT -> new JsonPrimitive(((NumericTag) tag).getAsFloat());
			case Tag.TAG_DOUBLE -> new JsonPrimitive(((NumericTag) tag).getAsDouble());
			case Tag.TAG_STRING -> new JsonPrimitive(((StringTag) tag).getAsString());
			case Tag.TAG_BYTE_ARRAY -> arrayOf(((ByteArrayTag) tag).getAsByteArray());
			case Tag.TAG_INT_ARRAY -> arrayOf(((IntArrayTag) tag).getAsIntArray());
			case Tag.TAG_LONG_ARRAY -> arrayOf(((LongArrayTag) tag).getAsLongArray());
			case Tag.TAG_LIST -> {
				JsonArray array = new JsonArray();
				for (Tag element : (ListTag) tag) {
					array.add(toJson(element));
				}
				yield array;
			}
			case Tag.TAG_COMPOUND -> {
				CompoundTag compound = (CompoundTag) tag;
				JsonObject object = new JsonObject();
				for (String key : sortedKeys(compound)) {
					object.add(key, toJson(compound.get(key)));
				}
				yield object;
			}
			default -> JsonNull.INSTANCE;
		};
	}

	public static List<String> sortedKeys(CompoundTag tag) {
		List<String> keys = new ArrayList<>(tag.getAllKeys());
		keys.sort(String::compareTo);
		return keys;
	}

	private static JsonArray arrayOf(byte[] values) {
		JsonArray array = new JsonArray(values.length);
		for (byte value : values) {
			array.add(value);
		}
		return array;
	}

	private static JsonArray arrayOf(int[] values) {
		JsonArray array = new JsonArray(values.length);
		for (int value : values) {
			array.add(value);
		}
		return array;
	}

	private static JsonArray arrayOf(long[] values) {
		JsonArray array = new JsonArray(values.length);
		for (long value : values) {
			array.add(value);
		}
		return array;
	}

	private static void write(Tag tag, DataOutputStream out) throws IOException {
		out.writeByte(tag.getId());
		switch (tag.getId()) {
			case Tag.TAG_BYTE -> out.writeByte(((NumericTag) tag).getAsByte());
			case Tag.TAG_SHORT -> out.writeShort(((NumericTag) tag).getAsShort());
			case Tag.TAG_INT -> out.writeInt(((NumericTag) tag).getAsInt());
			case Tag.TAG_LONG -> out.writeLong(((NumericTag) tag).getAsLong());
			case Tag.TAG_FLOAT -> out.writeFloat(((NumericTag) tag).getAsFloat());
			case Tag.TAG_DOUBLE -> out.writeDouble(((NumericTag) tag).getAsDouble());
			case Tag.TAG_STRING -> out.writeUTF(((StringTag) tag).getAsString());
			case Tag.TAG_BYTE_ARRAY -> {
				byte[] values = ((ByteArrayTag) tag).getAsByteArray();
				out.writeInt(values.length);
				out.write(values);
			}
			case Tag.TAG_INT_ARRAY -> {
				int[] values = ((IntArrayTag) tag).getAsIntArray();
				out.writeInt(values.length);
				for (int value : values) {
					out.writeInt(value);
				}
			}
			case Tag.TAG_LONG_ARRAY -> {
				long[] values = ((LongArrayTag) tag).getAsLongArray();
				out.writeInt(values.length);
				for (long value : values) {
					out.writeLong(value);
				}
			}
			case Tag.TAG_LIST -> {
				ListTag list = (ListTag) tag;
				out.writeInt(list.size());
				for (Tag element : list) {
					write(element, out);
				}
			}
			case Tag.TAG_COMPOUND -> {
				CompoundTag compound = (CompoundTag) tag;
				List<String> keys = sortedKeys(compound);
				out.writeInt(keys.size());
				for (String key : keys) {
					out.writeUTF(key);
					write(compound.get(key), out);
				}
			}
			default -> {
				// TAG_END carries nothing; every other id is handled above.
			}
		}
	}
}
