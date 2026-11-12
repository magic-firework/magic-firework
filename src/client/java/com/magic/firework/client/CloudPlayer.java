package com.magic.firework.client;

import java.util.ArrayList;
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
 */
public final class CloudPlayer {
	/** A misbehaving sender must not be able to queue unbounded clouds. */
	private static final int MAX_PLAYING = 64;

	private static final List<Playing> PLAYING = new ArrayList<>();

	private CloudPlayer() {
	}

	public static void accept(Minecraft client, Vec3 origin, FrameStream stream) {
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
		PLAYING.add(new Playing(origin, stream, level.getGameTime()));
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

	private static final class Playing {
		private final Vec3 origin;
		private final FrameStream stream;
		private final long startTick;
		private int next;

		private Playing(Vec3 origin, FrameStream stream, long startTick) {
			this.origin = origin;
			this.stream = stream;
			this.startTick = startTick;
		}

		private void play(ClientLevel level, long now) {
			float elapsed = (float) (now - startTick);
			while (next < stream.size() && stream.point(next).time() <= elapsed) {
				FrameStream.Point point = stream.point(next);
				level.addParticle(options(point), origin.x + point.offset().x, origin.y + point.offset().y,
						origin.z + point.offset().z, 0.0, 0.0, 0.0);
				next++;
			}
		}

		private boolean done() {
			return next >= stream.size();
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
