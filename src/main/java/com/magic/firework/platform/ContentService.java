package com.magic.firework.platform;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import com.magic.firework.MagicFirework;
import com.magic.firework.content.ContentLibrary;
import com.magic.firework.content.ContentRef;
import com.magic.firework.content.ContentSnapshot;
import com.magic.firework.content.TagCodec;
import com.magic.firework.core.ContentLimits;
import com.magic.firework.core.Id;
import com.magic.firework.core.shape.Ink;
import com.magic.firework.core.show.Show;

/**
 * Resolves a {@code show} reference against the current data-pack snapshot, the inline cache
 * and the writable library.
 *
 * <p>One instance per server, owned by {@link FireworkRuntime}. Swapping a snapshot is a
 * single reference write; the caches are cleared at the same time, so no reader can see a
 * half-reloaded world. Library lookups are cached by id and invalidated by
 * {@link ContentLibrary#version()}, so a burst no longer copies the NBT document every time.
 */
public final class ContentService {
	private volatile ContentSnapshot snapshot = ContentSnapshot.empty();
	private volatile ContentLibrary library;
	private volatile long libraryVersion = -1;

	/** Parsed inline shows, least-recently-used first. */
	private final Map<Long, Optional<Show>> inlineCache = new LinkedHashMap<>(16, 0.75F, true) {
		@Override
		protected boolean removeEldestEntry(Map.Entry<Long, Optional<Show>> eldest) {
			return size() > ContentLimits.MAX_INLINE_CACHE;
		}
	};
	private final Map<Id, Optional<Show>> libraryCache = new HashMap<>();

	public void accept(ContentSnapshot next) {
		snapshot = next == null ? ContentSnapshot.empty() : next;
		inlineCache.clear();
		libraryCache.clear();
		libraryVersion = -1;
	}

	public void bindLibrary(ContentLibrary next) {
		library = next;
		libraryCache.clear();
		libraryVersion = next == null ? -1 : next.version();
	}

	/** Drop cached library shows; the next lookup re-parses the current document. */
	public void invalidateLibrary() {
		libraryCache.clear();
		libraryVersion = -1;
	}

	public ContentSnapshot snapshot() {
		return snapshot;
	}

	public Ink ink(Id id) {
		return snapshot.ink(id);
	}

	/** @return the show a ref denotes, or {@code null} when nothing is installed under it */
	public Show show(ContentRef ref) {
		if (ref == null) {
			return null;
		}
		if (ref instanceof ContentRef.ById byId) {
			Show installed = snapshot.show(byId.id());
			return installed != null ? installed : libraryShow(byId.id()).orElse(null);
		}
		ContentRef.Inline inline = (ContentRef.Inline) ref;
		return inlineCache.computeIfAbsent(inline.fingerprint(), key -> parseInline(inline)).orElse(null);
	}

	private Optional<Show> libraryShow(Id id) {
		ContentLibrary current = library;
		if (current == null) {
			return Optional.empty();
		}
		long version = current.version();
		if (version != libraryVersion) {
			libraryCache.clear();
			libraryVersion = version;
		}
		return libraryCache.computeIfAbsent(id, key -> current.get(key).flatMap(this::parseLibrary));
	}

	private Optional<Show> parseLibrary(ContentLibrary.Entry entry) {
		try {
			return Optional.of(TagCodec.parseShow(entry.id(), entry.document(), this::ink));
		} catch (TagCodec.CodecException | RuntimeException exception) {
			MagicFirework.LOGGER.warn("failed to parse library show {}", entry.id(), exception);
			return Optional.empty();
		}
	}

	private Optional<Show> parseInline(ContentRef.Inline inline) {
		Id id = new Id(ContentLibrary.NAMESPACE, "inline/" + Long.toHexString(inline.fingerprint()));
		try {
			return Optional.of(TagCodec.parseShow(id, inline.document(), this::ink));
		} catch (TagCodec.CodecException | RuntimeException exception) {
			MagicFirework.LOGGER.warn("failed to parse inline show {}", id, exception);
			return Optional.empty();
		}
	}
}
