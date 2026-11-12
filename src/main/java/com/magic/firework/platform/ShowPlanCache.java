package com.magic.firework.platform;

import java.util.LinkedHashMap;
import java.util.Map;

import com.magic.firework.core.Id;
import com.magic.firework.core.eval.Plan;
import com.magic.firework.core.show.Show;

/**
 * Small LRU of baked shows.
 *
 * <p>A show with no jitter or thickness is seed-independent, so the key uses seed 0 and the
 * same bake serves every burst. A seed-sensitive show still gets cached, but only repeated
 * bursts with the same seed can hit. The cache is cleared whenever content or the library
 * changes.
 */
public final class ShowPlanCache {
	private static final int MAX_ENTRIES = 32;

	private final Map<Key, Plan> plans = new LinkedHashMap<>(16, 0.75F, true) {
		@Override
		protected boolean removeEldestEntry(Map.Entry<Key, Plan> eldest) {
			return size() > MAX_ENTRIES;
		}
	};

	public Plan get(Show show, long seed, int points) {
		return plans.get(key(show, seed, points));
	}

	public void put(Show show, long seed, int points, Plan plan) {
		plans.put(key(show, seed, points), plan);
	}

	public void clear() {
		plans.clear();
	}

	public int size() {
		return plans.size();
	}

	private static Key key(Show show, long seed, int points) {
		return new Key(show.id(), show.seedSensitive() ? seed : 0L, points);
	}

	private record Key(Id show, long seed, int points) {
	}
}
