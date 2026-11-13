package com.magic.firework.craft;

import java.util.List;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

/**
 * The {@code MagicFireworkBlueprint} tag layout.
 *
 * <pre>
 * MagicFireworkBlueprint: {
 *   v: 1,
 *   source: { profile: "magic-firework:ring12", overlay: {...} },   // a MagicFirework tag
 *   cost: [ { item: "minecraft:paper", count: 1 }, ... ]            // frozen at write time
 * }
 * </pre>
 *
 * <p>A blueprint is "written" exactly when it carries a {@code source}. The writing recipe
 * only accepts a blank blueprint, so a written one can never be edited again.
 */
public final class BlueprintData {
	public static final String ROOT = "MagicFireworkBlueprint";
	public static final String VERSION = "v";
	public static final String SOURCE = "source";
	public static final String COST = "cost";
	public static final int CURRENT_VERSION = 1;

	private BlueprintData() {
	}

	/** @return a fresh root tag holding a defensive copy of {@code source} and the bill */
	public static CompoundTag written(CompoundTag source, List<Cost> cost) {
		CompoundTag root = new CompoundTag();
		root.putInt(VERSION, CURRENT_VERSION);
		root.put(SOURCE, source == null ? new CompoundTag() : source.copy());
		root.put(COST, Cost.saveAll(cost));
		return root;
	}

	public static boolean isWritten(Tag root) {
		return root instanceof CompoundTag compound && compound.contains(SOURCE);
	}

	/** @return a defensive copy of the stored firework tag, empty when there is none */
	public static CompoundTag source(Tag root) {
		if (root instanceof CompoundTag compound && compound.get(SOURCE) instanceof CompoundTag source) {
			return source.copy();
		}
		return new CompoundTag();
	}

	public static List<Cost> cost(Tag root) {
		return root instanceof CompoundTag compound ? Cost.loadAll(compound.get(COST)) : List.of();
	}
}
