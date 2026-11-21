package com.magic.firework.content;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import com.magic.firework.core.Id;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import net.minecraft.nbt.CompoundTag;

/**
 * A library write is all-or-nothing: a crash mid-write must not leave a half entry, and the
 * timestamp must come from the injected clock rather than the wall clock.
 */
class ContentLibraryAtomicTest {
	private static final UUID AUTHOR = UUID.fromString("00000000-0000-0000-0000-000000000001");
	private static final Id FLOWER = new Id(ContentLibrary.NAMESPACE, "flower");

	@TempDir
	Path directory;

	private static CompoundTag document(int marker) {
		CompoundTag tag = new CompoundTag();
		tag.putInt("marker", marker);
		return tag;
	}

	@Test
	void createdComesFromTheInjectedClock() throws Exception {
		Instant instant = Instant.parse("2024-01-02T03:04:05.006Z");
		ContentLibrary library = new ContentLibrary(directory, 32, Clock.fixed(instant, ZoneOffset.UTC));

		ContentLibrary.Entry entry = library.save(FLOWER, document(1), AUTHOR);

		assertEquals(instant.toEpochMilli(), entry.created());
	}

	@Test
	void aFailedWriteLeavesTheOldEntryAndNoTempFile() throws Exception {
		ContentLibrary good = new ContentLibrary(directory);
		good.save(FLOWER, document(1), AUTHOR);

		ContentLibrary failing = new ContentLibrary(directory, 32, Clock.systemUTC(), (tag, path) -> {
			Files.writeString(path, "partial");
			throw new IOException("disk full");
		});

		assertThrows(IOException.class, () -> failing.save(FLOWER, document(2), AUTHOR));
		assertFalse(Files.exists(directory.resolve("flower.nbt.tmp")), "the temp file is removed");

		ContentLibrary reopened = new ContentLibrary(directory);
		reopened.load();
		assertEquals(1, reopened.size());
		assertEquals(1, reopened.get(FLOWER).orElseThrow().document().getInt("marker"),
				"the previous document is untouched");
	}

	@Test
	void aStaleTempFileIsIgnoredOnLoad() throws Exception {
		Files.writeString(directory.resolve("flower.nbt.tmp"), "half a tag");

		ContentLibrary library = new ContentLibrary(directory);
		library.load();

		assertEquals(0, library.size());
	}
}
