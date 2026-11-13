package com.magic.firework.craft;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Compares a crafting grid against a blueprint's stored bill.
 *
 * <p>Pure on purpose: the grid is reduced to {@code item id -> total count} by the recipe,
 * so this logic is unit-testable without a game bootstrap.
 */
public final class FormulaMatch {
	private FormulaMatch() {
	}

	/**
	 * @param cost the bill written into the blueprint; zero counts are ignored
	 * @param have the total count per item id currently in the grid (excluding the blueprint)
	 * @return true when the grid holds exactly the bill, no more and no less
	 */
	public static boolean matches(List<Cost> cost, Map<String, Integer> have) {
		Map<String, Integer> needed = new HashMap<>();
		if (cost != null) {
			for (Cost entry : cost) {
				if (entry != null && entry.count() > 0) {
					needed.merge(entry.item(), entry.count(), Integer::sum);
				}
			}
		}
		if (needed.isEmpty()) {
			return false;
		}
		return needed.equals(have == null ? Map.of() : have);
	}
}
