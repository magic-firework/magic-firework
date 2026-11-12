package com.magic.firework.core.show;

import com.magic.firework.core.Id;

/**
 * A non-visual side effect of a node (sound, temporary light), scheduled relative to the
 * node's start tick at the node's frame position.
 *
 * <p>This is the track that lets a baked show keep its world effects; the platform decides
 * how to play them, the core only says what and when.
 */
public record EventSpec(Kind kind, int tick, Id target, double a, double b) {
	public EventSpec {
		kind = kind == null ? Kind.SOUND : kind;
		tick = Math.max(0, tick);
	}

	public static EventSpec sound(int tick, Id sound, double volume, double pitch) {
		return new EventSpec(Kind.SOUND, tick, sound, volume, pitch);
	}

	public static EventSpec light(int tick, int ticks) {
		return new EventSpec(Kind.LIGHT, tick, null, ticks, 0.0);
	}

	public enum Kind {
		SOUND,
		LIGHT
	}
}
