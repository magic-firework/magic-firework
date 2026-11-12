package com.magic.firework.content;

import java.util.Map;

import com.magic.firework.core.Id;
import com.magic.firework.core.shape.Ink;
import com.magic.firework.core.show.Show;

/**
 * One immutable view of everything a data pack installed.
 *
 * <p>Reloading builds a whole new snapshot and swaps the reference, so a reader never sees a
 * half-cleared map. The snapshot only covers data packs; inline documents and the writable
 * library are resolved by {@code ContentService}.
 */
public record ContentSnapshot(Map<Id, Ink> inks, Map<Id, Show> shows) {
	public ContentSnapshot {
		inks = inks == null ? Map.of() : Map.copyOf(inks);
		shows = shows == null ? Map.of() : Map.copyOf(shows);
	}

	public static ContentSnapshot empty() {
		return new ContentSnapshot(Map.of(), Map.of());
	}

	public Ink ink(Id id) {
		return id == null ? null : inks.get(id);
	}

	public Show show(Id id) {
		return id == null ? null : shows.get(id);
	}
}
