package com.magic.firework.content;

import java.util.Objects;

import com.magic.firework.core.ContentLimits;
import com.magic.firework.core.Id;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;

/**
 * Where a piece of content comes from.
 *
 * <p>A {@code show} attribute may hold either a plain id (content installed by a data pack)
 * or a compound (content carried by the item itself). One type covers both, so every
 * consumer — launcher, library, exporter — resolves content the same way.
 */
public sealed interface ContentRef {

	/** Content installed under an id, loaded from a data pack or the library. */
	record ById(Id id) implements ContentRef {
		public ById {
			Objects.requireNonNull(id, "content id is required");
		}
	}

	/** Content embedded in the value that carries it, e.g. an item's NBT. */
	final class Inline implements ContentRef {
		private final CompoundTag document;
		private final long fingerprintHigh;
		private final long fingerprintLow;

		public Inline(CompoundTag document) {
			if (document == null) {
				throw new IllegalArgumentException("inline content needs a document");
			}
			CompoundTag copy = document.copy();
			if (copy.sizeInBytes() > ContentLimits.MAX_INLINE_BYTES) {
				throw new IllegalArgumentException(
						"inline content is larger than " + ContentLimits.MAX_INLINE_BYTES + " bytes");
			}
			this.document = copy;
			long[] digest = NbtTree.fingerprint128(copy);
			this.fingerprintHigh = digest[0];
			this.fingerprintLow = digest[1];
		}

		/** A defensive copy: the fingerprint and the document must never diverge. */
		public CompoundTag document() {
			return document.copy();
		}

		/** First 64 bits of the 128-bit digest, kept for existing callers and logs. */
		public long fingerprint() {
			return fingerprintHigh;
		}

		/** Both halves as hex, used in the cache-facing inline id. */
		public String fingerprintHex() {
			return Long.toHexString(fingerprintHigh) + Long.toHexString(fingerprintLow);
		}

		@Override
		public boolean equals(Object other) {
			return other instanceof Inline inline
					&& inline.fingerprintHigh == fingerprintHigh
					&& inline.fingerprintLow == fingerprintLow
					&& inline.document.equals(document);
		}

		@Override
		public int hashCode() {
			return 31 * Long.hashCode(fingerprintHigh) + Long.hashCode(fingerprintLow);
		}

		@Override
		public String toString() {
			return "Inline[" + fingerprintHex() + "]";
		}
	}

	/** @return the ref a {@code show} tag denotes, or {@code null} when it denotes nothing */
	static ContentRef parse(Tag tag) {
		if (tag instanceof StringTag string) {
			Id id = Id.parse(string.getAsString());
			return id == null ? null : new ById(id);
		}
		if (tag instanceof CompoundTag compound) {
			return new Inline(compound);
		}
		return null;
	}

}
