package com.magic.firework.content;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

import com.google.gson.JsonParser;
import com.magic.firework.MagicFirework;
import com.magic.firework.core.Id;
import com.magic.firework.core.shape.Ink;
import com.magic.firework.core.show.Show;

import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

/**
 * Pure loader: reads {@code inks/} and {@code shows/} and returns a fresh snapshot.
 *
 * <p>It touches no shared state, so the reload listener can build a snapshot off to the side
 * and the runtime can swap it in one reference write.
 */
public final class ContentLoader {
	private ContentLoader() {
	}

	public static ContentSnapshot load(ResourceManager manager) {
		Map<Id, Ink> inks = new LinkedHashMap<>();
		for (Map.Entry<ResourceLocation, Resource> entry : manager
				.listResources("inks", path -> path.getPath().endsWith(".json")).entrySet()) {
			Id id = toId(entry.getKey(), "inks/");
			if (id == null) {
				continue;
			}
			try (InputStream stream = entry.getValue().open()) {
				inks.put(id, TagCodec.parseInk(id, read(stream)));
			} catch (Exception exception) {
				MagicFirework.LOGGER.warn("failed to load ink {}", entry.getKey(), exception);
			}
		}

		Map<Id, Show> shows = new LinkedHashMap<>();
		for (Map.Entry<ResourceLocation, Resource> entry : manager
				.listResources("shows", path -> path.getPath().endsWith(".json")).entrySet()) {
			Id id = toId(entry.getKey(), "shows/");
			if (id == null) {
				continue;
			}
			try (InputStream stream = entry.getValue().open()) {
				shows.put(id, TagCodec.parseShow(id, read(stream), inks::get));
			} catch (Exception exception) {
				MagicFirework.LOGGER.warn("failed to load show {}", entry.getKey(), exception);
			}
		}

		MagicFirework.LOGGER.info("loaded {} inks and {} shows", inks.size(), shows.size());
		return new ContentSnapshot(inks, shows);
	}

	private static Tag read(InputStream stream) {
		return JsonToNbt.convert(JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)));
	}

	private static Id toId(ResourceLocation file, String prefix) {
		String path = file.getPath();
		if (!path.startsWith(prefix) || !path.endsWith(".json")) {
			return null;
		}
		return new Id(file.getNamespace(), path.substring(prefix.length(), path.length() - ".json".length()));
	}
}
