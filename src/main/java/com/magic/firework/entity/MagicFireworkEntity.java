package com.magic.firework.entity;

import java.util.List;
import java.util.UUID;

import com.magic.firework.attribute.AttributeBag;
import com.magic.firework.attribute.Attributes;
import com.magic.firework.content.FireworkProfile;
import com.magic.firework.effect.Sounds;
import com.magic.firework.effect.TrailEmitter;
import com.magic.firework.network.FireworkNetworking;
import com.magic.firework.physics.FlightStep;
import com.magic.firework.platform.FireworkRuntime;
import com.magic.firework.platform.RuntimeRegistry;
import com.magic.firework.program.Effect;
import com.magic.firework.registry.ModRegistries;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * The Minecraft adapter for one rocket. The program, its call stack and its delayed children
 * live in {@link RocketBrain}; this class only applies effects, moves the entity, turns it
 * and persists the brain.
 */
public class MagicFireworkEntity extends Entity {
	private static final double EPSILON = 1.0E-6;

	private RocketBrain brain = RocketBrain.empty();
	private boolean orphaned;
	private UUID ownerUuid;

	public MagicFireworkEntity(EntityType<? extends MagicFireworkEntity> type, Level level) {
		super(type, level);
		this.setNoGravity(true);
	}

	@Override
	protected void defineSynchedData() {
		// Nothing is synced through the data tracker: an attribute bag does not fit
		// EntityDataSerializers on 1.20.1, so it travels in a dedicated packet.
	}

	/**
	 * @param parent  the rocket that split this one, or {@code null} for a launch
	 */
	public static MagicFireworkEntity launch(ServerLevel level, Vec3 position, Vec3 lookDirection,
			FireworkProfile profile, AttributeBag overlay, Entity owner, MagicFireworkEntity parent) {
		FireworkRuntime runtime = RuntimeRegistry.current();
		if (runtime == null || !runtime.ledger().trySpawn() || profile == null) {
			return null;
		}
		AttributeBag base = profile.defaults().overlay(overlay);
		int depth = parent == null ? 0 : parent.depth() + 1;
		if (parent != null && depth > Attributes.MAX_DEPTH.get(parent.bag())) {
			return null;
		}
		long seed = parent == null ? level.random.nextLong() : parent.brain.nextChildSeed();

		MagicFireworkEntity rocket = new MagicFireworkEntity(ModRegistries.MAGIC_FIREWORK_ENTITY, level);
		rocket.brain = RocketBrain.launch(profile.id(), profile.program(), base, seed, depth, lookDirection);
		rocket.setPos(position);
		if (owner != null) {
			rocket.ownerUuid = owner.getUUID();
		}

		level.addFreshEntity(rocket);
		rocket.sendProfile(level);

		if (base.has(Attributes.LAUNCH_SOUND)) {
			Sounds.play(level, position, ResourceLocation.tryParse(base.raw().getString(Attributes.LAUNCH_SOUND)),
					3.0F, 1.0F);
		}
		return rocket;
	}

	// ── ticking ──────────────────────────────────────────────────────────────

	@Override
	public void tick() {
		super.tick();
		if (orphaned) {
			// The profile this rocket was running is no longer installed.
			discard();
			return;
		}
		if (!brain.initialized()) {
			return;
		}
		brain.advanceAge();
		AttributeBag bag = brain.bag();

		if (level().isClientSide) {
			// The client only needs the attributes: no program, no packets.
			TrailEmitter.trail(level(), position(), bag, brain.seed(), brain.age());
			return;
		}

		FireworkRuntime runtime = RuntimeRegistry.current();
		if (runtime == null) {
			discard();
			return;
		}
		ServerLevel level = (ServerLevel) level();
		setDeltaMovement(FlightStep.step(bag, getDeltaMovement()));
		Effect.TickResult result = brain.tick(runtime.definitions()::program, position());
		apply(level, runtime, result.effects());

		this.move(MoverType.SELF, getDeltaMovement());
		brain.setCollided(Attributes.COLLIDE.get(bag) && (horizontalCollision || verticalCollision));
		orient();
		tickPendingChildren(level, runtime);

		if (brain.expired()) {
			this.discard();
		}
	}

	private void apply(ServerLevel level, FireworkRuntime runtime, List<Effect> effects) {
		for (Effect effect : effects) {
			if (effect instanceof Effect.SetVelocity set) {
				setDeltaMovement(set.velocity());
			} else if (effect instanceof Effect.Burst) {
				runtime.shows().burst(level, position(), brain.bag(), brain.seed());
			} else if (effect instanceof Effect.SpawnChild child) {
				scheduleChild(level, runtime, child.program(), child.spread(), child.delayTicks());
			} else if (effect instanceof Effect.PlaySound sound) {
				Sounds.play(level, position(), sound.id(), sound.volume(), sound.pitch());
			} else if (effect instanceof Effect.Light light) {
				runtime.lights().light(level, blockPosition(), light.ticks());
			}
		}
	}

	private void orient() {
		Vec3 velocity = getDeltaMovement();
		if (velocity.lengthSqr() < EPSILON) {
			return;
		}
		setYRot((float) (Math.atan2(velocity.x, velocity.z) * (180.0 / Math.PI)));
		setXRot((float) (-Math.atan2(velocity.y, velocity.horizontalDistance()) * (180.0 / Math.PI)));
	}

	// ── state accessors ──────────────────────────────────────────────────────

	public long seed() {
		return brain.seed();
	}

	public int depth() {
		return brain.depth();
	}

	public AttributeBag bag() {
		return brain.bag();
	}

	private void scheduleChild(ServerLevel level, FireworkRuntime runtime, ResourceLocation programId,
			float spread, int delayTicks) {
		if (level.isClientSide || programId == null) {
			return;
		}
		RocketBrain.Child immediate = brain.scheduleChild(programId, spread, delayTicks);
		if (immediate != null) {
			spawnChildNow(level, runtime, immediate);
		}
	}

	private void tickPendingChildren(ServerLevel level, FireworkRuntime runtime) {
		for (RocketBrain.Child child : brain.dueChildren()) {
			spawnChildNow(level, runtime, child);
		}
	}

	private void spawnChildNow(ServerLevel level, FireworkRuntime runtime, RocketBrain.Child child) {
		FireworkProfile profile = runtime.definitions().get(child.programId());
		if (profile == null) {
			return;
		}
		Vec3 forward = normalized(getDeltaMovement(), brain.lookDirection());
		RandomSource random = RandomSource.create(child.seed());
		Vec3 jitter = normalized(new Vec3(
				random.nextGaussian(),
				random.nextGaussian() * 0.4,
				random.nextGaussian()), forward);
		float spread = Math.max(0.0F, Math.min(1.0F, child.spread()));
		Vec3 direction = normalized(forward.scale(1.0 - spread).add(jitter.scale(spread)), forward);

		// Aim the child through attributes: its own `launch` op honours these.
		AttributeBag aim = AttributeBag.EMPTY
				.withString(Attributes.DIRECTION_MODE.key(), "vector")
				.withFloat(Attributes.DIRECTION_X.key(), (float) direction.x)
				.withFloat(Attributes.DIRECTION_Y.key(), (float) direction.y)
				.withFloat(Attributes.DIRECTION_Z.key(), (float) direction.z);

		MagicFireworkEntity entity = launch(level, position(), brain.lookDirection(), profile, aim, null, this);
		if (entity != null) {
			entity.setDeltaMovement(getDeltaMovement());
		}
	}

	// ── sync / persistence ───────────────────────────────────────────────────

	public void acceptProfile(long seed, int age, AttributeBag base) {
		brain.acceptClientView(base, seed, age);
	}

	private void sendProfile(ServerLevel level) {
		for (ServerPlayer player : level.players()) {
			FireworkNetworking.sendProfile(player, this);
		}
	}

	@Override
	protected void addAdditionalSaveData(CompoundTag tag) {
		brain.save(tag);
		if (ownerUuid != null) {
			tag.putUUID("Owner", ownerUuid);
		}
	}

	@Override
	protected void readAdditionalSaveData(CompoundTag tag) {
		if (tag.hasUUID("Owner")) {
			ownerUuid = tag.getUUID("Owner");
		}
		FireworkRuntime runtime = RuntimeRegistry.current();
		if (runtime == null) {
			orphaned = true;
			return;
		}
		RocketBrain loaded = RocketBrain.load(tag, runtime.definitions()::program);
		if (loaded == null) {
			orphaned = true;
			brain = RocketBrain.empty();
			return;
		}
		brain = loaded;
	}

	// ── entity constraints ───────────────────────────────────────────────────

	@Override
	public boolean isPickable() {
		return false;
	}

	@Override
	public boolean isPushable() {
		return false;
	}

	@Override
	public boolean canBeCollidedWith() {
		return false;
	}

	@Override
	public boolean hurt(DamageSource source, float amount) {
		return !Attributes.DECORATIVE.get(bag());
	}

	public ResourceLocation profileId() {
		return brain.profileId();
	}

	/** True once a program or a client profile has been bound. */
	public boolean profileReady() {
		return brain.initialized();
	}

	/** Age in ticks, synced to late trackers so their trail lines up. */
	public int programAge() {
		return brain.age();
	}

	private static Vec3 normalized(Vec3 value, Vec3 fallback) {
		if (value == null || value.lengthSqr() < EPSILON) {
			return fallback;
		}
		return value.normalize();
	}
}
