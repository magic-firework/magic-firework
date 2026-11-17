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
	/**
	 * Launch speed in blocks/tick; when present it overrides the launch op's speed. Its range top
	 * is {@link com.magic.firework.config.FireworkLimits#maxSpeed}, the hard velocity ceiling.
	 */
	public static final Attr.FloatAttr SPEED = new Attr.FloatAttr("flight.speed", 0.1F, 0.0F, 4.0F);
	/**
	 * Flight time in ticks: what a {@code wait} without a number spends, and therefore how long
	 * the rocket rises before whatever the program does next. The crafting table edits this, so
	 * the player owns it and a data pack never has to guess.
	 */
	public static final Attr.IntAttr FLIGHT_TICKS = new Attr.IntAttr("flight.ticks", 40, 0, 400);
	/**
	 * The direction editor's two dials in degrees (0–360), relative to whoever launches the
	 * rocket: horizontal 0° is straight ahead, growing toward their right, and the vertical dial
	 * is the polar angle from up (0° = up). Both at 0° is a straight ascent, so an untouched
	 * firework and an aimed one run the same program; the world vector is resolved at launch.
	 */
	public static final Attr.FloatAttr DIRECTION_YAW = new Attr.FloatAttr("flight.direction.yaw", 0.0F, 0.0F, 360.0F);
	public static final Attr.FloatAttr DIRECTION_PITCH = new Attr.FloatAttr("flight.direction.pitch", 0.0F, 0.0F, 360.0F);

	// ── behaviour ────────────────────────────────────────────────────────────
	/** Pure display: immune to damage, ignored by entity targeting. */
	public static final Attr.BoolAttr DECORATIVE = new Attr.BoolAttr("decorative", true);
	/** Ticks the entity stays alive after its program ends. */
	public static final Attr.IntAttr AFTERGLOW = new Attr.IntAttr("afterglow", 0, 0, 200);

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
	/**
	 * Per-burst colour override (ARGB). Absent means the show's own palette decides, which is what
	 * a multi-colour show wants; present means every point of <em>this</em> burst uses it.
	 *
	 * <p>It is a raw key rather than an {@link Attr} because ARGB values are negative ints and a
	 * clamped attribute would mangle them; "absent" is expressed by the key not being there.
	 */
	public static final String SHOW_COLOR = "show.color";
	/** Per-burst geometry scale around the burst origin; 1.0 keeps the show's own size. */
	public static final Attr.FloatAttr SHOW_SIZE = new Attr.FloatAttr("show.size", 1.0F, 0.1F, 8.0F);
	/**
	 * Per-burst rotation in degrees around the burst origin: yaw around Y, pitch around X. Together
	 * with {@link #SHOW_SIZE} this is how one primitive show serves every node of a figure.
	 */
	public static final Attr.FloatAttr SHOW_YAW = new Attr.FloatAttr("show.yaw", 0.0F, -360.0F, 360.0F);
	public static final Attr.FloatAttr SHOW_PITCH = new Attr.FloatAttr("show.pitch", 0.0F, -360.0F, 360.0F);
	/**
	 * Make every burst of this rocket flicker, the way a vanilla firework star's Flicker flag
	 * makes its sparks blink back instead of fading once.
	 *
	 * <p>It is an attribute rather than a program op because it belongs to the rocket, not to a
	 * step: the crafting table (or an item's NBT) can set it without touching a data pack.
	 */
	public static final Attr.BoolAttr FLICKER = new Attr.BoolAttr("flicker", false);
}
