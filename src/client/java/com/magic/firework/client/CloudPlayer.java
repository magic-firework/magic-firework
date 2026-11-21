package com.magic.firework.client;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import com.magic.firework.client.particle.FireworkSparkParticle;
import com.magic.firework.config.FireworkLimits;
import com.magic.firework.core.PointBudget;
import com.magic.firework.core.eval.FrameStream;
import com.magic.firework.registry.ModParticles;

import org.joml.Vector3f;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.phys.Vec3;

/**
 * Client side of the baked executor: plays a {@link FrameStream} by emitting each point at its tick
 * offset, in arrival order, so playback needs one cursor and no per-tick allocation. A point with a
 * lifetime is a {@link FireworkSparkParticle}, so vanilla ticks drag, gravity, trail, flicker and
 * fade; one without a lifetime is a static puff emitted as dust, which has no per-particle hook.
 */
public final class CloudPlayer {
	/** A misbehaving sender must not be able to queue unbounded clouds. */
	private static final int MAX_PLAYING = 64;

	/** One budget for every cloud playing this tick, not one per cloud. */
	private static final PointBudget BUDGET = new PointBudget(FireworkLimits.maxClientPointsPerTick);

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
		BUDGET.beginTick();
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
			playing.play(level, now, BUDGET);
			if (playing.done()) {
				iterator.remove();
			}
		}
	}

	/** @return particles dropped by the budget during the last client tick */
	public static int droppedThisTick() {
		return BUDGET.droppedThisTick();
	}

	public static void clear() {
		PLAYING.clear();
	}

	public static int playing() {
		return PLAYING.size();
	}

	private static final class Playing {
		private final Vec3 origin;
		private final boolean flicker;
		private final CloudCursor cursor;

		private Playing(Vec3 origin, FrameStream stream, boolean flicker, long startTick) {
			this.origin = origin;
			this.flicker = flicker;
			this.cursor = new CloudCursor(stream, flicker, startTick);
		}

		private void play(ClientLevel level, long now, PointBudget budget) {
			cursor.advance(now, budget,
					point -> emitStatic(level, point),
					this::emitFlash,
					point -> emit(level, point));
		}

		private boolean done() {
			return cursor.done();
		}

		private void emit(ClientLevel level, FrameStream.Point point) {
			if (point.hasMotion()) {
				emitSpark(level, point);
			} else {
				emitStatic(level, point);
			}
		}

		private void emitStatic(ClientLevel level, FrameStream.Point point) {
			level.addParticle(options(point), origin.x + point.offset().x, origin.y + point.offset().y,
					origin.z + point.offset().z, 0.0, 0.0, 0.0);
		}

		/**
		 * Spawn the actual vanilla firework particle and configure it from the baked point. The
		 * particle's own tick then applies vanilla friction (0.91), gravity (0.004/tick), the
		 * half-life trail and the flicker/fade logic, which is exactly what the show asks for.
		 */
		private void emitSpark(ClientLevel level, FrameStream.Point point) {
			var velocity = point.velocity();
			Particle particle = Minecraft.getInstance().particleEngine.createParticle(ModParticles.FIREWORK_SPARK,
					origin.x + point.offset().x, origin.y + point.offset().y, origin.z + point.offset().z,
					velocity.x, velocity.y, velocity.z);
			if (!(particle instanceof FireworkSparkParticle spark)) {
				// A resource pack or a future version may not register the particle: keep drawing.
				emitStatic(level, point);
				return;
			}
			spark.setTrail(point.trail());
			spark.setFlicker(point.flicker() || flicker);
			spark.setColor(point.argb() & 0xFFFFFF);
			if (point.fadeArgb() != 0) {
				spark.setFadeColor(point.fadeArgb() & 0xFFFFFF);
			}
			if (point.lifetime() > 0.0F) {
				spark.setLifetime(Math.max(1, Math.round(point.lifetime())));
			}
		}

		private void emitFlash(FrameStream.Flash flash) {
			Particle particle = Minecraft.getInstance().particleEngine.createParticle(ParticleTypes.FLASH,
					origin.x + flash.offset().x, origin.y + flash.offset().y, origin.z + flash.offset().z,
					0.0, 0.0, 0.0);
			if (particle == null) {
				return;
			}
			int argb = flash.argb();
			particle.setColor(
					((argb >> 16) & 0xFF) / 255.0F,
					((argb >> 8) & 0xFF) / 255.0F,
					(argb & 0xFF) / 255.0F);
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
