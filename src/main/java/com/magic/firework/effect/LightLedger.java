package com.magic.firework.effect;

import java.util.LinkedHashMap;
import java.util.Map;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Persistent record of the light blocks the {@code light} op placed. {@link LightSources} keeps the same
 * map in memory for ticking, but a crash would lose it; this ledger is saved with the world so the next
 * start can remove every block that is still a {@code minecraft:light}.
 * Stored as {@code dimensions: { "<dimension id>": { "<pos as long>": untilTick } }}.
 */
public final class LightLedger extends SavedData {
	public static final String DATA_NAME = "magic_firework_lights";
	private static final String DIMENSIONS = "dimensions";

	private final Map<ResourceKey<Level>, Map<BlockPos, Long>> placed = new LinkedHashMap<>();

	public static LightLedger get(MinecraftServer server) {
		return server.overworld().getDataStorage()
				.computeIfAbsent(LightLedger::fromTag, LightLedger::new, DATA_NAME);
	}

	/** Read the on-disk form; unknown dimensions are skipped, never guessed. */
	public static LightLedger fromTag(CompoundTag tag) {
		LightLedger ledger = new LightLedger();
		if (tag == null) {
			return ledger;
		}
		CompoundTag dimensions = tag.getCompound(DIMENSIONS);
		for (String dimension : dimensions.getAllKeys()) {
			ResourceLocation id = ResourceLocation.tryParse(dimension);
			if (id == null) {
				continue;
			}
			ResourceKey<Level> key = ResourceKey.create(Registries.DIMENSION, id);
			CompoundTag positions = dimensions.getCompound(dimension);
			for (String position : positions.getAllKeys()) {
				try {
					ledger.put(key, BlockPos.of(Long.parseLong(position)), positions.getLong(position));
				} catch (NumberFormatException ignored) {
					// A hand-edited save must not stop the server from starting.
				}
			}
		}
		ledger.setDirty(false);
		return ledger;
	}

	@Override
	public CompoundTag save(CompoundTag tag) {
		CompoundTag dimensions = new CompoundTag();
		for (Map.Entry<ResourceKey<Level>, Map<BlockPos, Long>> entry : placed.entrySet()) {
			CompoundTag positions = new CompoundTag();
			for (Map.Entry<BlockPos, Long> position : entry.getValue().entrySet()) {
				positions.putLong(Long.toString(position.getKey().asLong()), position.getValue());
			}
			dimensions.put(entry.getKey().location().toString(), positions);
		}
		tag.put(DIMENSIONS, dimensions);
		return tag;
	}

	public void put(ResourceKey<Level> dimension, BlockPos position, long until) {
		placed.computeIfAbsent(dimension, key -> new LinkedHashMap<>()).put(position.immutable(), until);
		setDirty();
	}

	public void remove(ResourceKey<Level> dimension, BlockPos position) {
		Map<BlockPos, Long> positions = placed.get(dimension);
		if (positions != null && positions.remove(position) != null && positions.isEmpty()) {
			placed.remove(dimension);
		}
		setDirty();
	}

	public Map<BlockPos, Long> positions(ResourceKey<Level> dimension) {
		Map<BlockPos, Long> positions = placed.get(dimension);
		return positions == null ? Map.of() : Map.copyOf(positions);
	}

	/** A copy, so a recovery loop cannot mutate the ledger while iterating it. */
	public Map<ResourceKey<Level>, Map<BlockPos, Long>> entries() {
		Map<ResourceKey<Level>, Map<BlockPos, Long>> copy = new LinkedHashMap<>();
		placed.forEach((dimension, positions) -> copy.put(dimension, Map.copyOf(positions)));
		return copy;
	}

	public boolean isEmpty() {
		return placed.isEmpty();
	}

	public void clear() {
		if (!placed.isEmpty()) {
			placed.clear();
			setDirty();
		}
	}

	public int size() {
		int size = 0;
		for (Map<BlockPos, Long> positions : placed.values()) {
			size += positions.size();
		}
		return size;
	}
}
