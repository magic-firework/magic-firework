package com.magic.firework.attribute;

import java.util.Set;

import net.minecraft.nbt.ByteArrayTag;
import net.minecraft.nbt.ByteTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NumericTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;

/**
 * An immutable, flat attribute map backed by a {@link CompoundTag}: dotted keys such as
 * {@code flight.speed}, plain NBT values. Because the map <em>is</em> NBT, keys written by a newer
 * version survive save/load, overlay and network round-trips without registration. Layering is a
 * flat merge, upper layer wins; {@link #raw()} is read-only by convention, never mutate it.
 */
public final class AttributeBag {
	public static final AttributeBag EMPTY = new AttributeBag(new CompoundTag());
	private static final Set<String> BOOLEAN_KEYS = Set.of(
			Attributes.DECORATIVE.key(), Attributes.TRAIL.key(), Attributes.FLICKER.key());

	private final CompoundTag tag;

	private AttributeBag(CompoundTag tag) {
		this.tag = tag;
	}

	public static AttributeBag of(CompoundTag tag) {
		if (tag == null || tag.isEmpty()) {
			return EMPTY;
		}
		CompoundTag copy = tag.copy();
		for (String key : BOOLEAN_KEYS) {
			Tag value = copy.get(key);
			if (value instanceof NumericTag numeric && !(value instanceof ByteTag)) {
				copy.putBoolean(key, numeric.getAsInt() != 0);
			}
		}
		return new AttributeBag(copy);
	}

	public static AttributeBag load(Tag tag) {
		return tag instanceof CompoundTag compound ? of(compound) : EMPTY;
	}


	/**
	 * Colour / id arrays accept both the NBT int-array form (saved data) and a JSON
	 * list of numbers (data pack files), so a profile reads the same either way.
	 */
	public int[] intArray(String key) {
		Tag value = tag.get(key);
		if (value instanceof IntArrayTag array) {
			return array.getAsIntArray();
		}
		if (value instanceof ByteArrayTag array) {
			byte[] bytes = array.getAsByteArray();
			int[] result = new int[bytes.length];
			for (int i = 0; i < bytes.length; i++) {
				result[i] = bytes[i] & 0xFF;
			}
			return result;
		}
		if (value instanceof ListTag list) {
			int[] result = new int[list.size()];
			for (int i = 0; i < result.length; i++) {
				result[i] = (int) Math.round(list.getDouble(i));
			}
			return result;
		}
		return new int[0];
	}

	/** Read-only view. Do not mutate. */
	public CompoundTag raw() {
		return tag;
	}

	public CompoundTag copy() {
		return tag.copy();
	}

	/** Snapshot for persistence and networking. */
	public CompoundTag save() {
		return tag.copy();
	}

	public boolean has(String key) {
		return key != null && tag.contains(key);
	}

	public boolean isEmpty() {
		return tag.isEmpty();
	}

	public Set<String> keys() {
		return tag.getAllKeys();
	}

	public AttributeBag with(String key, Tag value) {
		if (key == null || value == null) {
			return this;
		}
		CompoundTag next = tag.copy();
		next.put(key, value.copy());
		return new AttributeBag(next);
	}

	public AttributeBag withFloat(String key, float value) {
		return with(key, FloatTag.valueOf(value));
	}

	public AttributeBag withInt(String key, int value) {
		return with(key, IntTag.valueOf(value));
	}

	public AttributeBag withBoolean(String key, boolean value) {
		CompoundTag next = tag.copy();
		next.putBoolean(key, value);
		return new AttributeBag(next);
	}

	public AttributeBag withString(String key, String value) {
		return with(key, StringTag.valueOf(value));
	}

	public AttributeBag without(String key) {
		if (!has(key)) {
			return this;
		}
		CompoundTag next = tag.copy();
		next.remove(key);
		return new AttributeBag(next);
	}

	/** Flat merge: {@code upper} wins, keys only present in this bag stay. */
	public AttributeBag overlay(AttributeBag upper) {
		if (upper == null || upper.isEmpty()) {
			return this;
		}
		CompoundTag next = tag.copy();
		for (String key : upper.tag.getAllKeys()) {
			next.put(key, upper.tag.get(key).copy());
		}
		return new AttributeBag(next);
	}

	@Override
	public boolean equals(Object other) {
		return this == other || (other instanceof AttributeBag bag && tag.equals(bag.tag));
	}

	@Override
	public int hashCode() {
		return tag.hashCode();
	}

	@Override
	public String toString() {
		return "AttributeBag" + tag;
	}
}
