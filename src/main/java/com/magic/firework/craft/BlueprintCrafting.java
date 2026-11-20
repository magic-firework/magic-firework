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
