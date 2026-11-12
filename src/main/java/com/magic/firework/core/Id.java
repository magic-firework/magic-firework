package com.magic.firework.core;

/**
 * Namespaced content id ({@code namespace:path}).
 *
 * <p>Core has its own id type so it does not have to import {@code ResourceLocation}; the
 * platform layer converts at the boundary.
 */
public record Id(String namespace, String path) {
	public Id {
		if (namespace == null || namespace.isEmpty() || path == null || path.isEmpty()) {
			throw new IllegalArgumentException("id needs a namespace and a path");
		}
	}

	public static Id parse(String text) {
		if (text == null) {
			return null;
		}
		int split = text.indexOf(':');
		if (split <= 0 || split == text.length() - 1) {
			return null;
		}
		return new Id(text.substring(0, split), text.substring(split + 1));
	}

	@Override
	public String toString() {
		return namespace + ":" + path;
	}
}
