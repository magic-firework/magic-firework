package com.magic.firework.craft;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The bill check shared by the blueprint crafting recipe: a written blueprint can be turned
 * back into the firework only when every ingredient in its frozen bill is present in full.
 */
public final class BlueprintCrafting {
	private BlueprintCrafting() {
	}

	/**
	 * The grid contract: one written blueprint and a payment matching the frozen bill per ingredient.
	 * @param blueprints how many blueprint items the grid contains
	 * @param written    how many of those are written
	 * @param provided   every non-blueprint ingredient, by item id
	 */
	public static boolean matchesBill(List<Cost> bill, int blueprints, int written,
			Map<String, Integer> provided) {
		if (bill == null || bill.isEmpty() || provided == null) {
			return false;
		}
		if (blueprints != 1 || written != 1) {
			return false;
		}
		int providedTotal = provided.values().stream().mapToInt(Integer::intValue).sum();
		int requiredTotal = bill.stream().mapToInt(Cost::count).sum();
		return providedTotal == requiredTotal && canPay(bill, provided);
	}

	public static boolean canPay(List<Cost> bill, Map<String, Integer> available) {
		if (bill == null || bill.isEmpty() || available == null) {
			return false;
		}
		for (Cost cost : bill) {
			if (available.getOrDefault(cost.item(), 0) < cost.count()) {
				return false;
			}
		}
		return true;
	}

	/** @return the remaining inventory, or {@code null} when the bill cannot be paid */
	public static Map<String, Integer> pay(List<Cost> bill, Map<String, Integer> available) {
		if (!canPay(bill, available)) {
			return null;
		}
		Map<String, Integer> remaining = new LinkedHashMap<>(available);
		for (Cost cost : bill) {
			remaining.merge(cost.item(), -cost.count(), Integer::sum);
		}
		remaining.entrySet().removeIf(entry -> entry.getValue() <= 0);
		return remaining;
	}
}
