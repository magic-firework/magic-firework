package com.magic.firework.platform;

import com.magic.firework.MagicFirework;
import com.magic.firework.attribute.AttributeBag;
import com.magic.firework.attribute.Attributes;
import com.magic.firework.config.FireworkLimits;
import com.magic.firework.content.ContentRef;
import com.magic.firework.core.Budget;
import com.magic.firework.core.Seed;
import com.magic.firework.core.Transform;
import com.magic.firework.core.eval.Evaluate;
import com.magic.firework.core.eval.FrameStream;
import com.magic.firework.core.eval.Plan;
import com.magic.firework.core.show.Show;
import com.magic.firework.network.FireworkNetworking;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

/**
 * The bridge from a firework's attribute bag to the show pipeline. One instance per runtime: it owns
 * the plan cache, the tick ledger and the scheduled effects it feeds. A burst resolves the show, bakes
 * it at most once per budget, sends it to nearby players and schedules the non-visual events.
 */
public final class ShowService {
	private final ContentService content;
	private final TickLedger ledger;
	private final ShowPlanCache plans;
	private final ScheduledEffects scheduled;

	public ShowService(ContentService content, TickLedger ledger, ShowPlanCache plans,
			ScheduledEffects scheduled) {
		this.content = content;
		this.ledger = ledger;
		this.plans = plans;
		this.scheduled = scheduled;
	}

	/**
	 * Handle one {@code burst} op.
	 *
	 * @param flicker whether a {@code flicker} op armed this explosion; it changes how the client
	 *                replays the same points, not what the server bakes
	 */
	public void burst(ServerLevel level, Vec3 origin, AttributeBag bag, long seed, boolean flicker) {
		if (bag == null) {
			return;
		}
		Show show = resolveShow(bag);
		if (show == null) {
			return;
		}
		int points = ledger.claimPoints(FireworkLimits.maxPointsPerShow);
		if (points <= 0) {
			return;
		}
		Plan plan = plan(show, seed, points);
		if (plan.isEmpty()) {
			return;
		}
		// A show is baked once and shared by every burst; whatever differs per rocket is applied
		// here, so one primitive can serve every node of a figure.
		Transform adjust = adjust(bag);
		FrameStream stream = plan.stream().transformed(adjust, color(bag));
		double radius = FireworkLimits.maxShowDistance;
		boolean cull = radius > 0.0;
		double limit = radius * radius;
		List<ServerPlayer> recipients = new ArrayList<>();
		for (ServerPlayer player : level.players()) {
			if (cull && player.distanceToSqr(origin) > limit) {
				continue;
			}
			if (ServerPlayNetworking.canSend(player, FireworkNetworking.CLOUD_CHANNEL)) {
				recipients.add(player);
			}
		}
		broadcast(origin, stream, flicker, recipients, FireworkNetworking::sendCloud);
		scheduled.schedule(level, origin, moved(plan.events(), adjust), level.getGameTime());
	}

	/** Encode the burst once, then hand the same payload to every recipient. */
	static <T> void broadcast(Vec3 origin, FrameStream stream, boolean flicker, Iterable<T> recipients,
			BiConsumer<T, byte[]> send) {
		byte[] payload = FireworkNetworking.encodeCloud(origin, stream, flicker);
		for (T recipient : recipients) {
			send.accept(recipient, payload);
		}
	}

	/** @return the per-burst geometry, or {@code null} when the burst uses the show as written */
	private static Transform adjust(AttributeBag bag) {
		float size = Attributes.SHOW_SIZE.get(bag);
		float yaw = Attributes.SHOW_YAW.get(bag);
		float pitch = Attributes.SHOW_PITCH.get(bag);
		if (size == 1.0F && yaw == 0.0F && pitch == 0.0F) {
			return null;
		}
		return Transform.rotation(Math.toRadians(pitch), Math.toRadians(yaw), 0.0, size);
	}

	/** @return the per-burst colour override, or {@code null} when the show's palette decides */
	private static Integer color(AttributeBag bag) {
		return bag.has(Attributes.SHOW_COLOR) ? bag.raw().getInt(Attributes.SHOW_COLOR) : null;
	}

	/** Event offsets ride the same transform as the points, so a sound stays on the figure. */
	private static List<Plan.ScheduledEvent> moved(List<Plan.ScheduledEvent> events, Transform adjust) {
		if (adjust == null || events.isEmpty()) {
			return events;
		}
		List<Plan.ScheduledEvent> moved = new ArrayList<>(events.size());
		for (Plan.ScheduledEvent event : events) {
			moved.add(new Plan.ScheduledEvent(event.kind(), event.time(), adjust.apply(event.at()),
					event.target(), event.a(), event.b()));
		}
		return List.copyOf(moved);
	}

	private Plan plan(Show show, long seed, int points) {
		Plan cached = plans.get(show, seed, points);
		if (cached != null) {
			return cached;
		}
		Plan plan = Evaluate.evaluate(show, new Seed(show.seedSensitive() ? seed : 0L), Budget.of(points));
		if (plan.truncated()) {
			MagicFirework.LOGGER.warn("show {} was truncated at {} points", show.id(), points);
		}
		plans.put(show, seed, points, plan);
		return plan;
	}

	private Show resolveShow(AttributeBag bag) {
		try {
			ContentRef ref = ContentRef.parse(bag.raw().get(Attributes.SHOW));
			Show show = content.show(ref);
			if (show == null) {
				MagicFirework.LOGGER.warn("burst has no show ({} on this firework)", ref);
			}
			return show;
		} catch (RuntimeException exception) {
			// The show tag is player-writable, so a malformed document must never break the tick.
			MagicFirework.LOGGER.warn("invalid show on a firework", exception);
			return null;
		}
	}
}
