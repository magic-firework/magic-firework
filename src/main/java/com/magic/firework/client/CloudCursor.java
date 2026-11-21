package com.magic.firework.client;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.function.Consumer;

import com.magic.firework.core.PointBudget;
import com.magic.firework.core.eval.FrameStream;

/**
 * Playback cursor for one baked stream: which points and flashes are due at a given tick, plus the
 * repeat flashes a flickering static puff asks for. Pure bookkeeping, extracted from
 * {@link CloudPlayer} so the timing rules are testable without a client level; the caller still
 * owns the particles and the per-tick budget count.
 */
public final class CloudCursor {
	/** Ticks between the flashes of one flickering static puff. */
	private static final float FLICKER_ECHO_DELAY = 8.0F;
	/** How many extra flashes a flickering static puff gets after its first. */
	private static final int FLICKER_ECHOES = 2;

	private final FrameStream stream;
	private final boolean flicker;
	private final long startTick;
	private final Deque<Echo> echoes = new ArrayDeque<>();
	private int next;
	private int nextFlash;

	public CloudCursor(FrameStream stream, boolean flicker, long startTick) {
		this.stream = stream;
		this.flicker = flicker;
		this.startTick = startTick;
	}

	/**
	 * Emit everything due at {@code now}: echoes, then flashes, then new points, in the renderer's order.
	 * @param echoSink repeat flashes of a flickering static puff
	 * @param flashSink vanilla flash overlays
	 * @param pointSink first emission of each point
	 */
	public void advance(long now, PointBudget budget, Consumer<FrameStream.Point> echoSink,
			Consumer<FrameStream.Flash> flashSink, Consumer<FrameStream.Point> pointSink) {
		float elapsed = (float) (now - startTick);
		// Echoes are queued in time order (the stream is), so one peek per tick is enough.
		while (!echoes.isEmpty() && echoes.peekFirst().time() <= elapsed) {
			Echo echo = echoes.pollFirst();
			if (budget.claim()) {
				echoSink.accept(echo.point());
			}
		}
		while (nextFlash < stream.flashCount() && stream.flash(nextFlash).time() <= elapsed) {
			FrameStream.Flash flash = stream.flash(nextFlash++);
			if (budget.claim()) {
				flashSink.accept(flash);
			}
		}
		while (next < stream.size() && stream.point(next).time() <= elapsed) {
			FrameStream.Point point = stream.point(next++);
			if (!budget.claim()) {
				continue;
			}
			pointSink.accept(point);
			if (flicker && !point.hasMotion()) {
				for (int i = 1; i <= FLICKER_ECHOES; i++) {
					echoes.addLast(new Echo(point.time() + i * FLICKER_ECHO_DELAY, point));
				}
			}
		}
	}

	public boolean done() {
		return next >= stream.size() && nextFlash >= stream.flashCount() && echoes.isEmpty();
	}

	/** A spark that is due again later: a flickering static puff's repeat flash. */
	private record Echo(float time, FrameStream.Point point) {
	}
}
