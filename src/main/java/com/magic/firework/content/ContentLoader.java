package com.magic.firework.content;

import java.util.Map;

import com.magic.firework.MagicFirework;
import com.magic.firework.core.Id;
import com.magic.firework.core.shape.Ink;
import com.magic.firework.core.show.Show;

import net.minecraft.server.packs.resources.ResourceManager;

/**
 * Pure loader: reads {@code inks/} and {@code shows/} and returns a fresh snapshot. It touches
 * no shared state, so the reload listener can build a snapshot off to the side and the runtime
 * swaps it in with one reference write. The per-file read and drop policy lives in
 * {@link JsonResourceLoader}.
 */
public final class ContentLoader {
	private ContentLoader() {
	}

	public static ContentSnapshot load(ResourceManager manager) {
		Map<Id, Ink> inks = JsonResourceLoader.parse(
				JsonResourceLoader.load(manager, "inks"), "inks", TagCodec::parseInk, "ink");
		Map<Id, Show> shows = JsonResourceLoader.parse(
				JsonResourceLoader.load(manager, "shows"), "shows",
				(id, tag) -> TagCodec.parseShow(id, tag, inks::get), "show");
		MagicFirework.LOGGER.info("loaded {} inks and {} shows", inks.size(), shows.size());
		return new ContentSnapshot(inks, shows);
	}
}
