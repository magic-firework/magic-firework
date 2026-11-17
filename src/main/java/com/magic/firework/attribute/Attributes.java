package com.magic.firework.attribute;

import com.magic.firework.config.FireworkLimits;

/**
 * Every built-in attribute key.
 *
 * <p>Adding a future attribute = add one constant here (plus read it where it matters).
 * No schema version bump, no registration, old data keeps working because the default
 * is part of the constant.
 */
public final class Attributes {
	private Attributes() {
	}

	// ── flight ───────────────────────────────────────────────────────────────
	/** Launch speed in blocks/tick; when present it overrides the launch op's speed. */
	public static final Attr.FloatAttr SPEED = new Attr.FloatAttr("flight.speed", 0.1F, 0.0F, 4.0F);
	/** Downward acceleration per tick. 0 keeps the rocket hovering. */
	public static final Attr.FloatAttr GRAVITY = new Attr.FloatAttr("flight.gravity", 0.0F, -1.0F, 1.0F);
	/** Velocity cap per tick: 0 keeps the rocket in place while its program's clock runs. */
	public static final Attr.FloatAttr MAX_SPEED = new Attr.FloatAttr("flight.max_speed", 4.0F, 0.0F, 8.0F);
	/** Velocity multiplier per tick (1.0 = no drag). */
	public static final Attr.FloatAttr DRAG = new Attr.FloatAttr("flight.drag", 0.99F, 0.0F, 1.0F);
	/**
	 * Flight time in ticks: what a {@code wait} without a number spends, and therefore how long
	 * the rocket rises before whatever the program does next. The crafting table edits this, so
	 * the player owns it and a data pack never has to guess.
	 */
	public static final Attr.IntAttr FLIGHT_TICKS = new Attr.IntAttr("flight.ticks", 40, 0, 400);
	/** {@code up} | {@code look} | {@code relative}. */
	public static final Attr.StringAttr DIRECTION_MODE = new Attr.StringAttr("flight.direction.mode", "up");
	/**
	 * The direction editor's two dials in degrees (0–360), relative to whoever launches the
	 * rocket: horizontal 0° is straight ahead, growing toward their right, and the vertical dial
	 * is the polar angle from up (0° = up). They pair with {@code flight.direction.mode =
	 * "relative"}; the world vector is resolved at launch.
	 */
	public static final Attr.FloatAttr DIRECTION_YAW = new Attr.FloatAttr("flight.direction.yaw", 0.0F, 0.0F, 360.0F);
	public static final Attr.FloatAttr DIRECTION_PITCH = new Attr.FloatAttr("flight.direction.pitch", 0.0F, 0.0F, 360.0F);

	// ── behaviour ────────────────────────────────────────────────────────────
	/** Pure display: immune to damage, ignored by entity targeting. */
	public static final Attr.BoolAttr DECORATIVE = new Attr.BoolAttr("decorative", true);
	/** Ticks the entity stays alive after its program ends. */
	public static final Attr.IntAttr AFTERGLOW = new Attr.IntAttr("afterglow", 0, 0, 200);
	/** Maximum nested split depth (0 = this rocket may not spawn children). */
	public static final Attr.IntAttr MAX_DEPTH = new Attr.IntAttr("max.depth", FireworkLimits.maxDepth, 0,
			FireworkLimits.maxDepthCeiling);
	/** Maximum children this rocket may spawn over its whole life. */
	public static final Attr.IntAttr MAX_CHILDREN = new Attr.IntAttr("max.children", FireworkLimits.maxChildren, 0,
			FireworkLimits.maxChildrenCeiling);

	// ── trail ────────────────────────────────────────────────────────────────
	public static final Attr.BoolAttr TRAIL = new Attr.BoolAttr("trail.enabled", true);
	/** Emit a trail particle every N ticks. */
	public static final Attr.IntAttr TRAIL_INTERVAL = new Attr.IntAttr("trail.interval", 1, 1, 20);
	public static final String TRAIL_COLORS = "trail.colors";
	public static final Attr.FloatAttr TRAIL_SCALE = new Attr.FloatAttr("trail.scale", 0.6F, 0.1F, 4.0F);

	// ── launch ───────────────────────────────────────────────────────────────
	public static final String LAUNCH_SOUND = "launch.sound";

	// ── op parameters ────────────────────────────────────────────────────────
	public static final String SOUND_ID = "sound.id";
	public static final Attr.FloatAttr SOUND_VOLUME = new Attr.FloatAttr("sound.volume", 1.0F, 0.0F, 8.0F);
	public static final Attr.FloatAttr SOUND_PITCH = new Attr.FloatAttr("sound.pitch", 1.0F, 0.5F, 2.0F);
	public static final Attr.IntAttr LIGHT_TICKS = new Attr.IntAttr("light.ticks", 20, 0, 400);

	// ── content ──────────────────────────────────────────────────────────────
	/** Id of the {@code Show} a {@code burst} op bakes. This is the only content source. */
	public static final String SHOW = "show";
}
