package com.magic.firework.attribute;

/**
 * Typed attribute accessors. Each one is "a key, a default, and a range" and knows how
 * to read itself out of an {@link AttributeBag}. Nothing is registered: the constants in
 * {@link Attributes} are the registry.
 */
public final class Attr {
	private Attr() {
	}

	public record FloatAttr(String key, float def, float min, float max) {
		public float get(AttributeBag bag) {
			if (!bag.has(key)) {
				return def;
			}
			return clamp(bag.raw().getFloat(key));
		}

		public AttributeBag set(AttributeBag bag, float value) {
			return bag.withFloat(key, clamp(value));
		}

		public float clamp(float value) {
			return Math.max(min, Math.min(max, value));
		}
	}

	public record IntAttr(String key, int def, int min, int max) {
		public int get(AttributeBag bag) {
			if (!bag.has(key)) {
				return def;
			}
			return clamp(bag.raw().getInt(key));
		}

		public AttributeBag set(AttributeBag bag, int value) {
			return bag.withInt(key, clamp(value));
		}

		public int clamp(int value) {
			return Math.max(min, Math.min(max, value));
		}
	}

	public record BoolAttr(String key, boolean def) {
		public boolean get(AttributeBag bag) {
			return bag.has(key) ? bag.raw().getBoolean(key) : def;
		}

		public AttributeBag set(AttributeBag bag, boolean value) {
			return bag.withBoolean(key, value);
		}
	}

	public record StringAttr(String key, String def) {
		public String get(AttributeBag bag) {
			return bag.has(key) ? bag.raw().getString(key) : def;
		}

		public AttributeBag set(AttributeBag bag, String value) {
			return bag.withString(key, value);
		}
	}
}
