package com.magic.firework.craft;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

/**
 * The {@code blueprint_crafting} grid contract. The recipe walks a {@code CraftingContainer}
 * and delegates every decision to {@link BlueprintCrafting#matchesBill}, so these cases are the
 * recipe's rules; the item/NBT round trip is covered by {@link BlueprintDataTest}.
 */
class BlueprintCraftingRecipeTest {
	private static final List<Cost> BILL = List.of(
			new Cost(FormulaCost.PAPER, 1),
			new Cost(FormulaCost.GUNPOWDER, 3));

	private static Map<String, Integer> exactPayment() {
		return Map.of(FormulaCost.PAPER, 1, FormulaCost.GUNPOWDER, 3);
	}

	@Test
	void oneWrittenBlueprintPaidInFullMatches() {
		assertTrue(BlueprintCrafting.matchesBill(BILL, 1, 1, exactPayment()));
	}

	@Test
	void aShortOrExtraPaymentIsRejected() {
		assertFalse(BlueprintCrafting.matchesBill(BILL, 1, 1,
				Map.of(FormulaCost.PAPER, 1, FormulaCost.GUNPOWDER, 2)), "missing ingredient");
		assertFalse(BlueprintCrafting.matchesBill(BILL, 1, 1,
				Map.of(FormulaCost.PAPER, 2, FormulaCost.GUNPOWDER, 3)), "extra ingredient");
	}

	@Test
	void theSameTotalWithTheWrongDistributionIsRejected() {
		// 2 paper + 2 gunpowder is still 4 items, but the bill needs 3 gunpowder.
		assertFalse(BlueprintCrafting.matchesBill(BILL, 1, 1,
				Map.of(FormulaCost.PAPER, 2, FormulaCost.GUNPOWDER, 2)));
	}

	@Test
	void aBlankOrDuplicatedBlueprintIsRejected() {
		assertFalse(BlueprintCrafting.matchesBill(BILL, 1, 0, exactPayment()), "blank blueprint");
		assertFalse(BlueprintCrafting.matchesBill(BILL, 2, 1, exactPayment()), "one blueprint only");
		assertFalse(BlueprintCrafting.matchesBill(BILL, 2, 2, exactPayment()), "two written blueprints");
		assertFalse(BlueprintCrafting.matchesBill(BILL, 0, 0, exactPayment()), "no blueprint");
	}

	@Test
	void anEmptyBillOrMissingPaymentIsRejected() {
		assertFalse(BlueprintCrafting.matchesBill(List.of(), 1, 1, exactPayment()));
		assertFalse(BlueprintCrafting.matchesBill(BILL, 1, 1, Map.of()));
		assertFalse(BlueprintCrafting.matchesBill(null, 1, 1, exactPayment()));
		assertFalse(BlueprintCrafting.matchesBill(BILL, 1, 1, null));
	}

	@Test
	void anUnrelatedIngredientIsNotAValidPayment() {
		assertFalse(BlueprintCrafting.matchesBill(BILL, 1, 1,
				Map.of("minecraft:glowstone_dust", 4)));
	}
}
