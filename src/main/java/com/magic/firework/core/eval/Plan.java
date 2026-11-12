package com.magic.firework.core.eval;

import java.util.List;

import com.magic.firework.core.Id;
import com.magic.firework.core.Vec3;
import com.magic.firework.core.show.EventSpec;

/**
 * Result of one evaluation: the baked point stream plus the non-visual events.
 *
 * <p>There is deliberately no "live subtree" in this model any more. Every point has a tick,
 * so the client can play the whole show from one packet with one cursor.
 */
public record Plan(FrameStream stream, List<ScheduledEvent> events) {
	public Plan {
		stream = stream == null ? FrameStream.empty() : stream;
		events = events == null ? List.of() : List.copyOf(events);
	}

	public static Plan empty() {
		return new Plan(FrameStream.empty(), List.of());
	}

	public boolean isEmpty() {
		return stream.isEmpty() && events.isEmpty();
	}

	/** A non-visual effect resolved to an absolute tick offset. */
	public record ScheduledEvent(EventSpec.Kind kind, float time, Vec3 at, Id target, double a, double b) {
	}
}
