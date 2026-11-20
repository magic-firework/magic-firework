package com.magic.firework.client.screen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;

import org.junit.jupiter.api.Test;

/** The category grid is fixed geometry; these are the numbers the hit tests rely on. */
class EditorLayoutTest {
	static {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
	}

	@Test
	void everyCategoryFitsTheImageAndHasAUniqueLabel() {
		Set<String> labels = new HashSet<>();
		for (int i = 0; i < EditorLayout.CATEGORIES.size(); i++) {
			EditorLayout.Category category = EditorLayout.CATEGORIES.get(i);
			assertTrue(labels.add(category.labelKey()), "duplicate category " + category.labelKey());
			assertTrue(
					EditorLayout.categoryX(i) >= 0
							&& EditorLayout.categoryX(i) + EditorLayout.SLOT_SIZE <= EditorLayout.IMAGE_W,
					"category " + i + " is outside the image width");
			assertTrue(
					EditorLayout.categoryY(i) >= 0
							&& EditorLayout.categoryY(i) + EditorLayout.SLOT_SIZE <= EditorLayout.IMAGE_H,
					"category " + i + " is outside the image height");
		}
		assertEquals(8, EditorLayout.CATEGORIES.size());
	}

	@Test
	void theGridWrapsAfterFourColumns() {
		assertEquals(EditorLayout.categoryY(0), EditorLayout.categoryY(3), "the first four slots share a row");
		assertTrue(EditorLayout.categoryX(0) < EditorLayout.categoryX(3), "columns move to the right");
		assertEquals(EditorLayout.categoryX(0), EditorLayout.categoryX(4), "the fifth slot starts a new row");
		assertTrue(EditorLayout.categoryY(4) > EditorLayout.categoryY(0));
	}

	@Test
	void theStepperGeometryStaysInsideTheImage() {
		assertTrue(EditorLayout.ROW1_Y > 0 && EditorLayout.ROW2_Y > EditorLayout.ROW1_Y);
		assertTrue(EditorLayout.BACK_X + EditorLayout.BACK_W <= EditorLayout.IMAGE_W);
		assertTrue(EditorLayout.BACK_Y + EditorLayout.BACK_H <= EditorLayout.IMAGE_H);
		assertTrue(EditorLayout.STEP_PLUS_X + EditorLayout.STEP_BTN_W <= EditorLayout.IMAGE_W);
		assertTrue(EditorLayout.ATTR_X + EditorLayout.ATTR_COL_GAP + EditorLayout.STEP_BTN_W <= EditorLayout.IMAGE_W);
	}
}
