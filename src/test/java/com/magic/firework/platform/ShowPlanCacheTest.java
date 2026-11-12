package com.magic.firework.platform;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.List;

import com.magic.firework.core.Id;
import com.magic.firework.core.eval.Plan;
import com.magic.firework.core.shape.Form;
import com.magic.firework.core.shape.Ink;
import com.magic.firework.core.show.Show;
import com.magic.firework.core.show.Stamp;

import org.junit.jupiter.api.Test;

class ShowPlanCacheTest {
	private static final Id INK = new Id("magic-firework", "dot");

	private static Show show(String name, double jitter) {
		Form form = Form.leaf(Ink.dot(INK, 1.0));
		Stamp stamp = new Stamp(form, 0, 0, null, null, 1.0, 0.0, jitter, List.of());
		return new Show(new Id("magic-firework", name), new int[] { 0xFFFFFF }, List.of(stamp));
	}

	@Test
	void seedFreeShowIgnoresTheSeedInTheKey() {
		Show show = show("steady", 0.0);
		ShowPlanCache cache = new ShowPlanCache();
		Plan plan = Plan.empty();

		cache.put(show, 1L, 128, plan);

		assertSame(plan, cache.get(show, 2L, 128), "a jitter-free show is seed-independent");
		assertNull(cache.get(show, 1L, 64), "a different point budget is a different bake");
	}

	@Test
	void seedSensitiveShowSeparatesSeeds() {
		Show show = show("jittery", 0.5);
		ShowPlanCache cache = new ShowPlanCache();
		Plan plan = Plan.empty();

		cache.put(show, 1L, 128, plan);

		assertNotNull(cache.get(show, 1L, 128));
		assertNull(cache.get(show, 2L, 128));
	}

	@Test
	void clearDropsEverything() {
		Show show = show("steady", 0.0);
		ShowPlanCache cache = new ShowPlanCache();
		cache.put(show, 0L, 128, Plan.empty());

		cache.clear();

		assertNull(cache.get(show, 0L, 128));
	}
}
