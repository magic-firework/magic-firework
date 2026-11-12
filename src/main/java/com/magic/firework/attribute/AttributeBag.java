package com.magic.firework.attribute;

import java.util.Set;

import net.minecraft.nbt.ByteArrayTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;

/**
 * An immutable, flat attribute map backed directly by a {@link CompoundTag}.
 *
 * <p>Keys are dotted strings such as {@code flight.speed}; values are plain NBT tags.
 * This is the whole "component system" of the mod: because the map <em>is</em> NBT,
 * keys written by a newer version (or by another mod) survive save/load, overlay and
 * network round-trips without any registration. Adding an attribute is one constant
 * in {@link Attributes} plus a read at the place that uses it.
 *
 * <p>Layering is a flat merge, upper layer wins. Immutability is by convention:
 * {@link #raw()} exposes the tag for reading, never mutate it.
 */
public final class AttributeBag {
	public static final AttributeBag EMPTY = new AttributeBag(new CompoundTag());

	private final CompoundTag tag;

	private AttributeBag(CompoundTag tag) {
		this.tag = tag;
	}

	public static AttributeBag of(CompoundTag tag) {
		return tag == null || tag.isEmpty() ? EMPTY : new AttributeBag(tag.copy());
	}

	public static AttributeBag load(Tag tag) {
		return tag instanceof CompoundTag compound ? of(compound) : EMPTY;
	}

	// ── typed reads ───────────────────────────────────────────────────────────

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
	public String toString() {
		return "AttributeBag" + tag;
	}
}
