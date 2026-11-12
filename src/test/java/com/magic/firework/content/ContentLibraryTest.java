package com.magic.firework.content;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.UUID;

import com.magic.firework.core.Id;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import net.minecraft.nbt.CompoundTag;

class ContentLibraryTest {
	private static final UUID AUTHOR = UUID.fromString("00000000-0000-0000-0000-000000000001");
	private static final UUID OTHER = UUID.fromString("00000000-0000-0000-0000-000000000002");

	@TempDir
	Path directory;

	private static CompoundTag document(int marker) {
		CompoundTag tag = new CompoundTag();
		tag.putInt("marker", marker);
		tag.put("stamps", new CompoundTag());
		return tag;
	}

	@Test
	void savesAndReloadsFromDisk() throws Exception {
		ContentLibrary library = new ContentLibrary(directory);
		library.save(new Id(ContentLibrary.NAMESPACE, "flower"), document(1), AUTHOR);

		ContentLibrary reopened = new ContentLibrary(directory);
		reopened.load();

		assertEquals(1, reopened.size());
		assertEquals(1, reopened.get(new Id(ContentLibrary.NAMESPACE, "flower")).orElseThrow().document().getInt("marker"));
		assertEquals(AUTHOR, reopened.get(new Id(ContentLibrary.NAMESPACE, "flower")).orElseThrow().author());
	}

	@Test
	void replacingKeepsTheOriginalAuthorAndTimestamp() throws Exception {
		ContentLibrary library = new ContentLibrary(directory);
		ContentLibrary.Entry first = library.save(new Id(ContentLibrary.NAMESPACE, "flower"), document(1), AUTHOR);

		ContentLibrary.Entry second = library.save(new Id(ContentLibrary.NAMESPACE, "flower"), document(2), AUTHOR);

		assertEquals(first.created(), second.created());
		assertEquals(2, second.document().getInt("marker"));
		assertEquals(1, library.size());
	}

	@Test
	void anotherAuthorCannotReplaceAnEntry() throws Exception {
		ContentLibrary library = new ContentLibrary(directory);
		library.save(new Id(ContentLibrary.NAMESPACE, "flower"), document(1), AUTHOR);

		assertThrows(IllegalStateException.class,
				() -> library.save(new Id(ContentLibrary.NAMESPACE, "flower"), document(2), OTHER));
	}

	@Test
	void quotaIsCountedPerAuthor() throws Exception {
		ContentLibrary library = new ContentLibrary(directory, 2);
		library.save(new Id(ContentLibrary.NAMESPACE, "a"), document(1), AUTHOR);
		library.save(new Id(ContentLibrary.NAMESPACE, "b"), document(1), AUTHOR);

		assertThrows(IllegalStateException.class,
				() -> library.save(new Id(ContentLibrary.NAMESPACE, "c"), document(1), AUTHOR));
		library.save(new Id(ContentLibrary.NAMESPACE, "c"), document(1), OTHER);
		assertEquals(3, library.size());
	}

	@Test
	void namesMustBeSafe() throws Exception {
		ContentLibrary library = new ContentLibrary(directory);

		assertThrows(IllegalArgumentException.class,
				() -> library.save(new Id(ContentLibrary.NAMESPACE, "../escape"), document(1), AUTHOR));
		assertThrows(IllegalArgumentException.class,
				() -> library.save(new Id("other", "flower"), document(1), AUTHOR));
		assertThrows(IllegalArgumentException.class,
				() -> library.save(new Id(ContentLibrary.NAMESPACE, "Flower"), document(1), AUTHOR));
	}

	@Test
	void emptyDocumentsAreRefused() throws Exception {
		ContentLibrary library = new ContentLibrary(directory);

		assertThrows(IllegalArgumentException.class,
				() -> library.save(new Id(ContentLibrary.NAMESPACE, "flower"), new CompoundTag(), AUTHOR));
	}

	@Test
	void deleteRespectsOwnershipUnlessForced() throws Exception {
		ContentLibrary library = new ContentLibrary(directory);
		Id id = new Id(ContentLibrary.NAMESPACE, "flower");
		library.save(id, document(1), AUTHOR);

		assertFalse(library.delete(id, OTHER, false));
		assertTrue(library.delete(id, OTHER, true));
		assertEquals(0, library.size());
	}
}
