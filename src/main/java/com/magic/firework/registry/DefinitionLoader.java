package com.magic.firework.registry;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.magic.firework.MagicFirework;
import com.magic.firework.attribute.AttributeBag;
import com.magic.firework.content.FireworkProfile;
import com.magic.firework.content.JsonToNbt;
import com.magic.firework.program.Program;
import com.magic.firework.program.ProgramParser;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

/**
 * Pure loader: reads {@code fireworks/*.json} and returns a fresh immutable registry.
 *
 * <p>A reload replaces the whole registry. A file that cannot be read or parsed is dropped
 * and logged with its id; the previous version is deliberately not kept, because keeping it
 * would need mutable merge state across reloads and would let a broken pack serve stale
 * content indefinitely.
 */
public final class DefinitionLoader {
	private DefinitionLoader() {
	}

	public static Definitions load(ResourceManager manager) {
		Map<ResourceLocation, Tag> documents = new LinkedHashMap<>();
		Map<ResourceLocation, Resource> resources = manager.listResources(
				"fireworks", path -> path.getPath().endsWith(".json"));
		for (Map.Entry<ResourceLocation, Resource> entry : resources.entrySet()) {
			try (InputStream stream = entry.getValue().open()) {
				JsonElement json = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
				documents.put(entry.getKey(), JsonToNbt.convert(json));
			} catch (Exception exception) {
				MagicFirework.LOGGER.warn("failed to read firework profile {}", entry.getKey(), exception);
			}
		}
		return load(documents);
	}

	/**
	 * The reload decision, separated from resource IO so it can be tested without a
	 * {@link ResourceManager}. Keys are file paths such as {@code fireworks/ring.json}.
	 */
	static Definitions load(Map<ResourceLocation, Tag> documents) {
		Map<ResourceLocation, FireworkProfile> loaded = new LinkedHashMap<>();
		for (Map.Entry<ResourceLocation, Tag> entry : documents.entrySet()) {
			ResourceLocation file = entry.getKey();
			ResourceLocation id = toId(file);
			if (id == null) {
				continue;
			}
			try {
				FireworkProfile profile = parse(id, entry.getValue());
				if (profile != null) {
					loaded.put(id, profile);
				}
			} catch (Exception exception) {
				MagicFirework.LOGGER.warn("failed to load firework profile {} ({})", file, id, exception);
			}
		}
		MagicFirework.LOGGER.info("loaded {} firework profiles", loaded.size());
		return new Definitions(loaded);
	}

	private static FireworkProfile parse(ResourceLocation id, Tag root) {
		if (!(root instanceof CompoundTag compound)) {
			MagicFirework.LOGGER.warn("firework profile {} is not a JSON object", id);
			return null;
		}
		Program program;
		try {
			program = ProgramParser.parse(id, compound.get("program"));
		} catch (ProgramParser.ProgramException exception) {
			MagicFirework.LOGGER.warn("invalid program in {}: {}", id, exception.getMessage());
			return null;
		}
		AttributeBag defaults = AttributeBag.load(compound.getCompound("attributes"));
		return new FireworkProfile(id, program, defaults);
	}

	private static ResourceLocation toId(ResourceLocation file) {
		String path = file.getPath();
		String prefix = "fireworks/";
		if (!path.startsWith(prefix) || !path.endsWith(".json")) {
			return null;
		}
		String name = path.substring(prefix.length(), path.length() - ".json".length());
		return ResourceLocation.tryBuild(file.getNamespace(), name);
	}
}
