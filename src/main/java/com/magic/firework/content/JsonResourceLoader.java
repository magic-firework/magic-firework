package com.magic.firework.content;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

import com.google.gson.JsonParser;
import com.magic.firework.MagicFirework;
import com.magic.firework.core.Id;

import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

/**
 * The one resource-tree reader shared by {@link ContentLoader} and
 * {@code com.magic.firework.registry.DefinitionLoader}: list a directory, trim the
 * {@code <directory>/<name>.json} prefix, convert JSON to NBT, and drop a file that cannot be
 * read with a warning instead of failing the whole reload.
 */
public final class JsonResourceLoader {
	private JsonResourceLoader() {
	}

	/** A parser may reject a file with a checked exception; the loader turns that into a warning. */
	@FunctionalInterface
	public interface DocumentParser<T> {
		T parse(Id id, Tag tag) throws Exception;
	}

	/** @return file path -> parsed NBT, in resource-manager order */
	public static Map<ResourceLocation, Tag> load(ResourceManager manager, String directory) {
		Map<ResourceLocation, Tag> documents = new LinkedHashMap<>();
		Map<ResourceLocation, Resource> resources = manager.listResources(
				directory, path -> path.getPath().endsWith(".json"));
		for (Map.Entry<ResourceLocation, Resource> entry : resources.entrySet()) {
			try (InputStream stream = entry.getValue().open()) {
				documents.put(entry.getKey(), JsonToNbt.convert(
						JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8))));
			} catch (Exception exception) {
				MagicFirework.LOGGER.warn("failed to read {} file {}", directory, entry.getKey(), exception);
			}
		}
		return documents;
	}

	/** Parse every document, dropping a bad file with a warning instead of failing the reload. */
	public static <T> Map<Id, T> parse(Map<ResourceLocation, Tag> documents, String directory,
			DocumentParser<T> parser, String label) {
		Map<Id, T> parsed = new LinkedHashMap<>();
		for (Map.Entry<ResourceLocation, Tag> entry : documents.entrySet()) {
			ResourceLocation id = id(entry.getKey(), directory);
			if (id == null) {
				continue;
			}
			Id contentId = new Id(id.getNamespace(), id.getPath());
			try {
				parsed.put(contentId, parser.parse(contentId, entry.getValue()));
			} catch (Exception exception) {
				MagicFirework.LOGGER.warn("failed to load {} {}", label, entry.getKey(), exception);
			}
		}
		return parsed;
	}

	/** @return the content id a file denotes, or {@code null} when the path is not in the directory */
	public static ResourceLocation id(ResourceLocation file, String directory) {
		String prefix = directory + "/";
		String path = file.getPath();
		if (!path.startsWith(prefix) || !path.endsWith(".json")) {
			return null;
		}
		String name = path.substring(prefix.length(), path.length() - ".json".length());
		return ResourceLocation.tryBuild(file.getNamespace(), name);
	}
}
