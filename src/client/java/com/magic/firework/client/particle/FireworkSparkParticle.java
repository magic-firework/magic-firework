package com.magic.firework.client.particle;

import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SimpleAnimatedParticle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.core.particles.SimpleParticleType;

/**
 * A configurable copy of vanilla's firework spark.
 *
 * <p>Vanilla's {@code FireworkParticles.SparkParticle} is package-private, so a baked show
 * cannot set its colour, fade, lifetime, trail or flicker. Copying the small class gives the
 * show those knobs while keeping vanilla's own physics: friction 0.91, gravity 0.004/tick,
 * the half-life trail and the flicker render rule.
 */
public class FireworkSparkParticle extends SimpleAnimatedParticle {
	private boolean trail;
	private boolean flicker;
	private final ParticleEngine engine;

	public FireworkSparkParticle(ClientLevel level, double x, double y, double z,
			double velocityX, double velocityY, double velocityZ, ParticleEngine engine, SpriteSet sprites) {
		super(level, x, y, z, sprites, 0.1F);
		this.xd = velocityX;
		this.yd = velocityY;
		this.zd = velocityZ;
		this.engine = engine;
		this.quadSize *= 0.75F;
		this.lifetime = 48 + this.random.nextInt(12);
		this.alpha = 0.99F;
		this.setSpriteFromAge(sprites);
	}

	public void setTrail(boolean trail) {
		this.trail = trail;
	}

	public void setFlicker(boolean flicker) {
		this.flicker = flicker;
	}

	@Override
	public void render(VertexConsumer buffer, Camera camera, float partialTick) {
		if (!this.flicker || this.age < this.lifetime / 3
				|| (this.age + this.lifetime) / 3 % 2 == 0) {
			super.render(buffer, camera, partialTick);
		}
	}

	@Override
	public void tick() {
		super.tick();
		if (this.trail && this.age < this.lifetime / 2 && (this.age + this.lifetime) % 2 == 0) {
			FireworkSparkParticle echo = new FireworkSparkParticle(this.level, this.x, this.y, this.z,
					0.0, 0.0, 0.0, this.engine, this.sprites);
			echo.alpha = 0.99F;
			echo.setColor(this.rCol, this.gCol, this.bCol);
			echo.age = echo.lifetime / 2;
			echo.flicker = this.flicker;
			this.engine.add(echo);
		}
	}

	/** Provides the spark, exactly as vanilla's firework provider does. */
	public static final class Provider implements ParticleProvider<SimpleParticleType> {
		private final SpriteSet sprites;

		public Provider(SpriteSet sprites) {
			this.sprites = sprites;
		}

		@Override
		public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z,
				double velocityX, double velocityY, double velocityZ) {
			return new FireworkSparkParticle(level, x, y, z, velocityX, velocityY, velocityZ,
					Minecraft.getInstance().particleEngine, this.sprites);
		}
	}
}
