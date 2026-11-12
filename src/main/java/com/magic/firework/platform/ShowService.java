package com.magic.firework.platform;

import com.magic.firework.MagicFirework;
import com.magic.firework.attribute.AttributeBag;
import com.magic.firework.attribute.Attributes;
import com.magic.firework.config.FireworkLimits;
import com.magic.firework.content.ContentRef;
import com.magic.firework.core.Budget;
import com.magic.firework.core.Seed;
import com.magic.firework.core.eval.Evaluate;
import com.magic.firework.core.eval.Plan;
import com.magic.firework.core.show.Show;
import com.magic.firework.network.FireworkNetworking;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * The bridge from a firework's attribute bag to the show pipeline.
 *
 * <p>One instance per runtime: it owns the plan cache, the tick ledger and the scheduled
 * effects it feeds. A burst resolves the show, bakes it at most once per budget, sends it to
 * nearby players and schedules the non-visual events.
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

	/** Handle one {@code burst} op. */
	public void burst(ServerLevel level, Vec3 origin, AttributeBag bag, long seed) {
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
		double radius = FireworkLimits.maxShowDistance;
		boolean cull = radius > 0.0;
		double limit = radius * radius;
		for (ServerPlayer player : level.players()) {
			if (cull && player.distanceToSqr(origin) > limit) {
				continue;
			}
			if (ServerPlayNetworking.canSend(player, FireworkNetworking.CLOUD_CHANNEL)) {
				FireworkNetworking.sendCloud(player, origin, plan.stream());
			}
		}
		scheduled.schedule(level, origin, plan.events(), level.getGameTime());
	}

	private Plan plan(Show show, long seed, int points) {
		Plan cached = plans.get(show, seed, points);
		if (cached != null) {
			return cached;
		}
		Plan plan = Evaluate.evaluate(show, new Seed(show.seedSensitive() ? seed : 0L), Budget.of(points));
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
