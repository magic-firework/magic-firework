package com.magic.firework.client;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;

import com.magic.firework.core.eval.FrameStream;

import org.joml.Vector3f;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.world.phys.Vec3;

/**
 * Client side of the baked executor: plays a {@link FrameStream} by emitting each point when
 * its tick offset arrives.
 *
 * <p>Points arrive ordered by time, so playback is one cursor per stream: no sorting, no
 * allocation per tick. This is why a whole figure fits in a single packet.
 *
 * <p>A flickering burst plays each spark more than once: vanilla hides a flickering spark for
 * alternating thirds of its life, which a dust particle cannot express (there is no per-particle
 * render hook), so the same spark is re-emitted a few ticks later and reads as a blink.
 */
public final class CloudPlayer {
	/** A misbehaving sender must not be able to queue unbounded clouds. */
	private static final int MAX_PLAYING = 64;

	/** Ticks between the flashes of one flickering spark. */
	private static final float FLICKER_ECHO_DELAY = 8.0F;
	/** How many extra flashes a flickering spark gets after its first. */
	private static final int FLICKER_ECHOES = 2;

	private static final List<Playing> PLAYING = new ArrayList<>();

	private CloudPlayer() {
	}

	public static void accept(Minecraft client, Vec3 origin, FrameStream stream, boolean flicker) {
		if (stream == null || stream.isEmpty()) {
			return;
		}
		ClientLevel level = client.level;
		if (level == null) {
			return;
		}
		while (PLAYING.size() >= MAX_PLAYING) {
			PLAYING.remove(0);
		}
		PLAYING.add(new Playing(origin, stream, flicker, level.getGameTime()));
	}

	public static void tick(Minecraft client) {
		ClientLevel level = client.level;
		if (level == null) {
			PLAYING.clear();
			return;
		}
		if (PLAYING.isEmpty()) {
			return;
		}
		long now = level.getGameTime();
		for (Iterator<Playing> iterator = PLAYING.iterator(); iterator.hasNext();) {
			Playing playing = iterator.next();
			playing.play(level, now);
			if (playing.done()) {
				iterator.remove();
			}
		}
	}

	public static void clear() {
		PLAYING.clear();
	}

	public static int playing() {
		return PLAYING.size();
	}

	/** A spark that is due again later: a flickering point's repeat flash. */
	private record Echo(float time, FrameStream.Point point) {
	}

	private static final class Playing {
		private final Vec3 origin;
		private final FrameStream stream;
		private final boolean flicker;
		private final Deque<Echo> echoes = new ArrayDeque<>();
		private final long startTick;
		private int next;

		private Playing(Vec3 origin, FrameStream stream, boolean flicker, long startTick) {
			this.origin = origin;
			this.stream = stream;
			this.flicker = flicker;
			this.startTick = startTick;
		}

		private void play(ClientLevel level, long now) {
			float elapsed = (float) (now - startTick);
			// Echoes are queued in time order (the stream is), so one peek per tick is enough.
			while (!echoes.isEmpty() && echoes.peekFirst().time() <= elapsed) {
				emit(level, echoes.pollFirst().point());
			}
			while (next < stream.size() && stream.point(next).time() <= elapsed) {
				FrameStream.Point point = stream.point(next++);
				emit(level, point);
				if (flicker) {
					for (int i = 1; i <= FLICKER_ECHOES; i++) {
						echoes.addLast(new Echo(point.time() + i * FLICKER_ECHO_DELAY, point));
					}
				}
			}
		}

		private boolean done() {
			return next >= stream.size() && echoes.isEmpty();
		}

		private void emit(ClientLevel level, FrameStream.Point point) {
			level.addParticle(options(point), origin.x + point.offset().x, origin.y + point.offset().y,
					origin.z + point.offset().z, 0.0, 0.0, 0.0);
		}

		private static ParticleOptions options(FrameStream.Point point) {
			int argb = point.argb();
			Vector3f color = new Vector3f(
					((argb >> 16) & 0xFF) / 255.0F,
					((argb >> 8) & 0xFF) / 255.0F,
					(argb & 0xFF) / 255.0F);
			return new DustParticleOptions(color, Math.max(0.15F, point.size()));
		}
	}
}
