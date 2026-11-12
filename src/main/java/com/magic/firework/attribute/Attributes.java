package com.magic.firework.attribute;

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
	/** Velocity cap per tick. Setting this to 0 through a {@code set} op is how a rocket hovers. */
	public static final Attr.FloatAttr MAX_SPEED = new Attr.FloatAttr("flight.max_speed", 4.0F, 0.0F, 8.0F);
	/** Velocity multiplier per tick (1.0 = no drag). */
	public static final Attr.FloatAttr DRAG = new Attr.FloatAttr("flight.drag", 0.99F, 0.0F, 1.0F);
	/** {@code up} | {@code look} | {@code vector}. */
	public static final Attr.StringAttr DIRECTION_MODE = new Attr.StringAttr("flight.direction.mode", "up");
	public static final Attr.FloatAttr DIRECTION_X = new Attr.FloatAttr("flight.direction.x", 0.0F, -1.0F, 1.0F);
	public static final Attr.FloatAttr DIRECTION_Y = new Attr.FloatAttr("flight.direction.y", 1.0F, -1.0F, 1.0F);
	public static final Attr.FloatAttr DIRECTION_Z = new Attr.FloatAttr("flight.direction.z", 0.0F, -1.0F, 1.0F);

	// ── behaviour ────────────────────────────────────────────────────────────
	/** Explode on the first block/entity collision. */
	public static final Attr.BoolAttr COLLIDE = new Attr.BoolAttr("collide", true);
	/** Pure display: immune to damage, ignored by entity targeting. */
	public static final Attr.BoolAttr DECORATIVE = new Attr.BoolAttr("decorative", true);
	/** Ticks the entity stays alive after its program ends. */
	public static final Attr.IntAttr AFTERGLOW = new Attr.IntAttr("afterglow", 0, 0, 200);
	/** Maximum nested split depth (0 = this rocket may not spawn children). */
	public static final Attr.IntAttr MAX_DEPTH = new Attr.IntAttr("max.depth", 3, 0, 16);
	/** Maximum children this rocket may spawn over its whole life. */
	public static final Attr.IntAttr MAX_CHILDREN = new Attr.IntAttr("max.children", 32, 0, 256);

	// ── trail ────────────────────────────────────────────────────────────────
	public static final Attr.BoolAttr TRAIL = new Attr.BoolAttr("trail.enabled", true);
	/** Emit a trail particle every N ticks. */
	public static final Attr.IntAttr TRAIL_INTERVAL = new Attr.IntAttr("trail.interval", 1, 1, 20);
	public static final String TRAIL_COLORS = "trail.colors";
	public static final Attr.FloatAttr TRAIL_SCALE = new Attr.FloatAttr("trail.scale", 0.6F, 0.1F, 4.0F);

	// ── launch ───────────────────────────────────────────────────────────────
	public static final String LAUNCH_SOUND = "launch.sound";

	// ── op parameters ────────────────────────────────────────────────────────
	/** Only the {@code await} immediately before a {@code burst} reads this. */
	public static final Attr.IntAttr FUSE_TICKS = new Attr.IntAttr("fuse.ticks", 40, 1, 400);
	public static final String SOUND_ID = "sound.id";
	public static final Attr.FloatAttr SOUND_VOLUME = new Attr.FloatAttr("sound.volume", 1.0F, 0.0F, 8.0F);
	public static final Attr.FloatAttr SOUND_PITCH = new Attr.FloatAttr("sound.pitch", 1.0F, 0.5F, 2.0F);
	public static final Attr.IntAttr LIGHT_TICKS = new Attr.IntAttr("light.ticks", 20, 0, 400);

	// ── content ──────────────────────────────────────────────────────────────
	/** Id of the {@code Show} a {@code burst} op bakes. This is the only content source. */
	public static final String SHOW = "show";
}
