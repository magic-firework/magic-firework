package com.magic.firework.craft;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

/**
 * One ingredient of a written blueprint: an item id plus how many the recipe consumes.
 *
 * <p>Stored as {@code {item: "minecraft:paper", count: 1}} so a blueprint outlives a
 * registry change and stays readable without the game running. Items are resolved (and
 * therefore validated) only at craft time.
 */
public record Cost(String item, int count) {
	/** A 3x3 grid can hold at most this many of one item across its slots. */
	public static final int MAX_COUNT = 64 * 9;

	public Cost {
		Objects.requireNonNull(item, "an ingredient needs an item id");
		count = Math.max(0, Math.min(count, MAX_COUNT));
	}

	public CompoundTag save() {
		CompoundTag tag = new CompoundTag();
		tag.putString("item", item);
		tag.putInt("count", count);
		return tag;
	}

	/** @return the cost a tag denotes, or {@code null} when the tag is malformed */
	public static Cost load(Tag tag) {
		if (!(tag instanceof CompoundTag compound)) {
			return null;
		}
		String item = compound.getString("item").trim();
		int count = compound.getInt("count");
		if (item.isEmpty() || count <= 0) {
			return null;
		}
		return new Cost(item, count);
	}

	public static ListTag saveAll(List<Cost> costs) {
		ListTag list = new ListTag();
		if (costs != null) {
			for (Cost cost : costs) {
				if (cost != null && cost.count() > 0) {
					list.add(cost.save());
				}
			}
		}
		return list;
	}

	/** Unknown or malformed entries are dropped, so a hand-edited tag cannot break a craft. */
	public static List<Cost> loadAll(Tag tag) {
		if (!(tag instanceof ListTag list)) {
			return List.of();
		}
		List<Cost> costs = new ArrayList<>(list.size());
		for (Tag element : list) {
			Cost cost = load(element);
			if (cost != null) {
				costs.add(cost);
			}
		}
		return List.copyOf(costs);
	}
}
