package com.magic.firework.content;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.nbt.NumericTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;

/**
 * JSON -> NBT for data pack files.
 *
 * <p>Deliberately not {@code JsonOps.INSTANCE.convertTo(NbtOps.INSTANCE, json)}: DFU
 * packs JSON number arrays into {@code ByteArrayTag} (so {@code [0.0, 1.0, 0.0]} becomes
 * {@code 0B,1B,0B}), which silently destroys every float in a program. This converter
 * keeps the distinction: integer literals become int/long tags, everything else becomes
 * a double tag, arrays always become a {@link ListTag} of the element's own type.
 */
public final class JsonToNbt {
	private JsonToNbt() {
	}

	public static Tag convert(JsonElement json) {
		if (json == null || json.isJsonNull()) {
			return new CompoundTag();
		}
		if (json.isJsonObject()) {
			JsonObject object = json.getAsJsonObject();
			CompoundTag tag = new CompoundTag();
			for (String key : object.keySet()) {
				tag.put(key, convert(object.get(key)));
			}
			return tag;
		}
		if (json.isJsonArray()) {
			return convertArray(json.getAsJsonArray());
		}
		return primitive(json.getAsJsonPrimitive());
	}

	/**
	 * Arrays keep the widest numeric type they contain, so {@code [1, 1.5]} becomes a list of
	 * doubles instead of silently losing the second element: a {@link ListTag} is homogeneous
	 * and refuses an element of a different type. Anything else that does not fit is an error
	 * rather than a dropped value.
	 */
	private static ListTag convertArray(JsonArray array) {
		List<Tag> elements = new ArrayList<>(array.size());
		boolean numeric = true;
		boolean floating = false;
		for (JsonElement element : array) {
			Tag tag = convert(element);
			elements.add(tag);
			numeric &= tag instanceof NumericTag;
			floating |= tag instanceof FloatTag || tag instanceof DoubleTag;
		}
		ListTag list = new ListTag();
		for (Tag tag : elements) {
			Tag value = numeric && floating ? DoubleTag.valueOf(((NumericTag) tag).getAsDouble()) : tag;
			if (!list.addTag(list.size(), value)) {
				throw new IllegalArgumentException("JSON array mixes incompatible element types");
			}
		}
		return list;
	}

	private static Tag primitive(JsonPrimitive primitive) {
		if (primitive.isBoolean()) {
			return IntTag.valueOf(primitive.getAsBoolean() ? 1 : 0);
		}
		if (primitive.isString()) {
			return StringTag.valueOf(primitive.getAsString());
		}
		String literal = primitive.getAsString();
		boolean integral = literal.indexOf('.') < 0
				&& literal.indexOf('e') < 0
				&& literal.indexOf('E') < 0;
		if (integral) {
			try {
				long value = Long.parseLong(literal);
				if (value >= Integer.MIN_VALUE && value <= Integer.MAX_VALUE) {
					return IntTag.valueOf((int) value);
				}
				return LongTag.valueOf(value);
			} catch (NumberFormatException ignored) {
				// Fall through to double: JSON allows 1e3 style integers.
			}
		}
		return DoubleTag.valueOf(primitive.getAsDouble());
	}
}
