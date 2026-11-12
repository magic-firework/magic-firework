package com.magic.firework.core;

/**
 * Structural limits of the content format itself.
 *
 * <p>These are part of the schema: exceeding one means the content is invalid. Server policy
 * (points baked per tick, split depth, spawn budget) lives in
 * {@link com.magic.firework.config.FireworkLimits} instead. Keeping the two apart is what
 * stops a data pack from raising a server limit and a config from redefining the format.
 */
public final class ContentLimits {
	/** Points a single ink may contain. */
	public static final int MAX_INK_POINTS = 8192;
	/** Stamps a single show may contain. */
	public static final int MAX_STAMPS = 4096;
	/** Palette entries a show may contain. */
	public static final int MAX_PALETTE = 256;
	/** Copies a single ring, array or mirror node may expand to. */
	public static final int MAX_REPEAT = 256;
	/** Nesting depth of form nodes. */
	public static final int MAX_FORM_DEPTH = 8;
	/** Form nodes a single shape may contain. */
	public static final int MAX_FORM_NODES = 4096;
	/** Bytes an inline document carried by an item may occupy. */
	public static final int MAX_INLINE_BYTES = 64 * 1024;
	/** Parsed inline documents kept in memory. */
	public static final int MAX_INLINE_CACHE = 256;
	/** Steps a single show may try to emit, across all stamps and durations. */
	public static final int MAX_STEPS_PER_SHOW = 65_536;

	private ContentLimits() {
	}
}
