package com.magic.firework.entity;

import java.util.List;
import java.util.UUID;

import com.magic.firework.attribute.AttributeBag;
import com.magic.firework.attribute.Attributes;
import com.magic.firework.config.FireworkLimits;
import com.magic.firework.content.FireworkProfile;
import com.magic.firework.effect.Sounds;
import com.magic.firework.effect.TrailEmitter;
import com.magic.firework.network.FireworkNetworking;
import com.magic.firework.physics.FlightStep;
import com.magic.firework.platform.FireworkRuntime;
import com.magic.firework.platform.RuntimeRegistry;
import com.magic.firework.program.Effect;
import com.magic.firework.registry.ModRegistries;
import com.magic.firework.rocket.RocketSpec;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
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
	private FireworkRuntime runtime;
	private boolean orphaned;
	private boolean profileSynced;
	private UUID ownerUuid;

	public MagicFireworkEntity(EntityType<? extends MagicFireworkEntity> type, Level level) {
		super(type, level);
		// Vanilla gravity stays off on purpose: FlightStep applies the brake itself, along the flight
		// line, so the rocket stops at rest instead of being pulled back down.
		this.setNoGravity(true);
	}

	@Override
	protected void defineSynchedData() {
		// Nothing is synced through the data tracker: an attribute bag does not fit
		// EntityDataSerializers on 1.20.1, so it travels in a dedicated packet.
	}

	/**
	 * Creates one rocket from a spec: the profile whose program and defaults it runs, the per-instance
	 * overlay, and the child tree its {@code spawn} ops may name by slot. Split depth is a safety
	 * constant, {@link FireworkLimits#maxDepth}, not something an item declares.
	 * @param parent the rocket that split this one, or {@code null} for a launch
	 */
	public static MagicFireworkEntity launch(FireworkRuntime runtime, ServerLevel level, Vec3 position,
			Vec3 lookDirection,
			RocketSpec spec, Entity owner, MagicFireworkEntity parent) {
		if (runtime == null || !runtime.ledger().trySpawn()) {
			return null;
		}
		int depth = parent == null ? 0 : parent.depth() + 1;
		Launch plan = resolveLaunch(runtime, spec, depth);
		if (plan == null) {
			return null;
		}
		long seed = parent == null ? level.random.nextLong() : parent.brain.nextChildSeed();

		MagicFireworkEntity rocket = new MagicFireworkEntity(ModRegistries.MAGIC_FIREWORK_ENTITY, level);
		rocket.runtime = runtime;
		rocket.brain = RocketBrain.launch(spec, plan.profile().program(), plan.base(), seed, plan.depth(),
				lookDirection);
		rocket.setPos(position);
		if (owner != null) {
			rocket.ownerUuid = owner.getUUID();
		}

		level.addFreshEntity(rocket);

		if (plan.base().has(Attributes.LAUNCH_SOUND)) {
			Sounds.play(level, position,
					ResourceLocation.tryParse(plan.base().raw().getString(Attributes.LAUNCH_SOUND)), 3.0F, 1.0F);
		}
		return rocket;
	}

	/**
	 * The part of a launch that does not touch the world: which profile runs, the resolved attribute base
	 * and the generation depth. A pure seam, so a launch can be validated without a server.
	 * @param depth 0 for a root launch, {@code parent.depth() + 1} for a split
	 * @return the plan, or {@code null} when the profile is missing or the depth is over the limit
	 */
	public static Launch resolveLaunch(FireworkRuntime runtime, RocketSpec spec, int depth) {
		if (runtime == null || spec == null || depth < 0 || depth > FireworkLimits.maxDepth) {
			return null;
		}
		FireworkProfile profile = runtime.definitions().get(spec.profileId());
		if (profile == null) {
			return null;
		}
		return new Launch(profile, profile.defaults().overlay(spec.overlay()), depth);
	}

	/** A validated launch: the profile to run, its resolved defaults and the generation depth. */
	public record Launch(FireworkProfile profile, AttributeBag base, int depth) {
	}

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
			// The client runs the same flight step as the server: server positions stay
			// authoritative, but stepping locally is what keeps it smooth between packets.
			if (brain.initialized()) {
				setDeltaMovement(FlightStep.step(getDeltaMovement()));
				move(MoverType.SELF, getDeltaMovement());
			}
			TrailEmitter.trail(level(), position(), bag, brain.seed(), brain.age());
			return;
		}

		ServerLevel level = (ServerLevel) level();
		FireworkRuntime runtime = this.runtime;
		if (runtime == null) {
			runtime = RuntimeRegistry.of(level.getServer());
			this.runtime = runtime;
		}
		if (runtime == null) {
			discard();
			return;
		}
		setDeltaMovement(FlightStep.step(getDeltaMovement()));
		Effect.TickResult result = brain.tick(runtime.definitions()::program);
		apply(level, runtime, result.effects());

		this.move(MoverType.SELF, getDeltaMovement());
		brain.setCollided(horizontalCollision || verticalCollision);
		orient();
		tickPendingChildren(level);

		if (!profileSynced) {
			// Sent after the first tick so the packet carries the velocity the launch op set;
			// a rocket nobody can see yet does not need a profile.
			profileSynced = true;
			sendProfile(level);
		}

		if (brain.expired()) {
			this.discard();
		}
	}

	private void apply(ServerLevel level, FireworkRuntime runtime, List<Effect> effects) {
		for (Effect effect : effects) {
			if (effect instanceof Effect.SetVelocity set) {
				setDeltaMovement(set.velocity());
			} else if (effect instanceof Effect.Burst) {
				AttributeBag bag = brain.bag();
				runtime.shows().burst(level, position(), bag, brain.seed(), Attributes.FLICKER.get(bag));
			} else if (effect instanceof Effect.SpawnChild child) {
				scheduleChild(level, child);
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

	public long seed() {
		return brain.seed();
	}

	public int depth() {
		return brain.depth();
	}

	public AttributeBag bag() {
		return brain.bag();
	}

	/**
	 * Turns one spawn request into a rocket. A slot target reads the launching item's tree, so
	 * what appears was decided when the item was crafted; a profile target falls back to content.
	 * A slot the item does not fill spawns nothing, which is how a program can offer more slots
	 * than a given rocket was built with.
	 */
	private void scheduleChild(ServerLevel level, Effect.SpawnChild effect) {
		RocketSpec target = effect.target().isSlot()
				? brain.child(effect.target().childIndex())
				: RocketSpec.of(effect.target().profile());
		if (target == null) {
			return;
		}
		RocketBrain.Child immediate = brain.scheduleChild(target, effect.delayTicks());
		if (immediate != null) {
			spawnChildNow(level, immediate);
		}
	}

	private void tickPendingChildren(ServerLevel level) {
		for (RocketBrain.Child child : brain.dueChildren()) {
			spawnChildNow(level, child);
		}
	}

	private void spawnChildNow(ServerLevel level, RocketBrain.Child child) {
		// Deliberately no direction override: a split child flies the way its own
		// profile (flight.direction.*) and its own launch op say. Injecting a vector
		// here would win over both, because launch lets attributes override the op.
		MagicFireworkEntity entity = launch(runtime, level, position(), brain.lookDirection(), child.spec(), null,
				this);
		if (entity != null) {
			entity.setDeltaMovement(getDeltaMovement());
		}
	}

	/** Client side: bind the synced attributes and velocity, then fly. */
	public void acceptProfile(long seed, int age, Vec3 velocity, AttributeBag base) {
		brain.acceptClientView(base, seed, age);
		setDeltaMovement(velocity);
	}

	/**
	 * A player who starts tracking an already airborne rocket needs the profile too, and with the
	 * velocity it has at that moment, otherwise their client would hold the rocket still.
	 */
	@Override
	public void startSeenByPlayer(ServerPlayer player) {
		super.startSeenByPlayer(player);
		if (!level().isClientSide && brain.initialized()) {
			FireworkNetworking.sendProfile(player, this);
		}
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
		this.runtime = RuntimeRegistry.of(level().getServer());
		FireworkRuntime runtime = this.runtime;
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

	/** True once a program or a client profile has been bound. */
	public boolean profileReady() {
		return brain.initialized();
	}

	/** Age in ticks, synced to late trackers so their trail lines up. */
	public int programAge() {
		return brain.age();
	}
}
