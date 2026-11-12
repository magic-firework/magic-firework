package com.magic.firework.content;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import com.magic.firework.core.ContentLimits;
import com.magic.firework.core.Id;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;

/**
 * Server-side store for authored shows.
 *
 * <p>One NBT file per entry under the world's {@code magic_firework/library} directory. A
 * library entry is an inline document plus its author, so content can outlive the item that
 * carried it and still be resolved by id. Names are restricted to a safe alphabet, kept
 * apart from the namespace data packs use, and counted per author so a player cannot fill
 * the disk.
 */
public final class ContentLibrary {
	public static final String NAMESPACE = "magic-firework";
	public static final int DEFAULT_MAX_PER_AUTHOR = 32;

	private static final Pattern NAME = Pattern.compile("[a-z0-9_\\-]{1,32}");

	private final Path root;
	private final int maxPerAuthor;
	private final Map<Id, Entry> entries = new LinkedHashMap<>();
	/** Bumped on every load/save/delete so caches can invalidate by cheap comparison. */
	private volatile long version;

	public ContentLibrary(Path root) {
		this(root, DEFAULT_MAX_PER_AUTHOR);
	}

	public ContentLibrary(Path root, int maxPerAuthor) {
		this.root = root;
		this.maxPerAuthor = Math.max(1, maxPerAuthor);
	}

	public record Entry(Id id, UUID author, long created, CompoundTag document) {
		public Entry {
			document = document.copy();
		}
	}

	/** Read every entry from disk, replacing the in-memory view. */
	public void load() throws IOException {
		entries.clear();
		if (!Files.isDirectory(root)) {
			return;
		}
			try (Stream<Path> files = Files.list(root)) {
			for (Path file : files.toList()) {
				String name = file.getFileName().toString();
				if (!name.endsWith(".nbt")) {
					continue;
				}
				Id id = id(name.substring(0, name.length() - ".nbt".length()));
				if (id == null) {
					continue;
				}
				if (Files.size(file) > ContentLimits.MAX_INLINE_BYTES) {
					continue;
				}
				CompoundTag tag = NbtIo.read(file.toFile());
				if (tag == null || !tag.contains("document")) {
					continue;
				}
				CompoundTag document = tag.getCompound("document");
				if (document.sizeInBytes() > ContentLimits.MAX_INLINE_BYTES) {
					continue;
				}
				entries.put(id, new Entry(id, tag.getUUID("author"), tag.getLong("created"), document));
			}
		}
		version++;
	}

	/** Bumped whenever the set of documents changes; caches compare it instead of listening. */
	public long version() {
		return version;
	}

	public Set<Id> ids() {
		return Set.copyOf(entries.keySet());
	}

	public Optional<Entry> get(Id id) {
		return Optional.ofNullable(id == null ? null : entries.get(id));
	}

	public int size() {
		return entries.size();
	}

	public int count(UUID author) {
		int count = 0;
		for (Entry entry : entries.values()) {
			if (entry.author().equals(author)) {
				count++;
			}
		}
		return count;
	}

	/**
	 * Create or replace an entry. Replacing is only allowed for the author, and a new entry
	 * is refused once the author's quota is spent.
	 */
	public Entry save(Id id, CompoundTag document, UUID author) throws IOException {
		validate(id, document);
		if (author == null) {
			throw new IllegalArgumentException("a library entry needs an author");
		}
		Entry existing = entries.get(id);
		if (existing != null && !existing.author().equals(author)) {
			throw new IllegalStateException(id + " belongs to another author");
		}
		if (existing == null && count(author) >= maxPerAuthor) {
			throw new IllegalStateException("library quota of " + maxPerAuthor + " entries per author is spent");
		}
		Entry entry = new Entry(id, author, existing == null ? System.currentTimeMillis() : existing.created(),
				document);
		write(entry);
		entries.put(id, entry);
		version++;
		return entry;
	}

	/** @return true when an entry was removed */
	public boolean delete(Id id, UUID actor, boolean force) throws IOException {
		Entry existing = entries.get(id);
		if (existing == null) {
			return false;
		}
		if (!force && !existing.author().equals(actor)) {
			return false;
		}
		Files.deleteIfExists(file(id));
		entries.remove(id);
		version++;
		return true;
	}

	private void validate(Id id, CompoundTag document) {
		if (id == null || !NAMESPACE.equals(id.namespace()) || !NAME.matcher(id.path()).matches()) {
			throw new IllegalArgumentException(
					"library names are lower-case letters, digits, '_' and '-' (1-32 characters)");
		}
		if (document == null || document.isEmpty()) {
			throw new IllegalArgumentException("a library entry needs a document");
		}
		if (document.sizeInBytes() > ContentLimits.MAX_INLINE_BYTES) {
			throw new IllegalArgumentException(
					"library entries may not exceed " + ContentLimits.MAX_INLINE_BYTES + " bytes");
		}
	}

	private void write(Entry entry) throws IOException {
		Files.createDirectories(root);
		CompoundTag tag = new CompoundTag();
		tag.putUUID("author", entry.author());
		tag.putLong("created", entry.created());
		tag.put("document", entry.document());
		NbtIo.write(tag, file(entry.id()).toFile());
	}

	private Path file(Id id) {
		return root.resolve(id.path() + ".nbt");
	}

	private static Id id(String name) {
		return NAME.matcher(name).matches() ? new Id(NAMESPACE, name) : null;
	}
}
