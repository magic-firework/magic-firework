package com.magic.firework.client.screen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.magic.firework.screen.FireworkEditorMenu;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;

import org.junit.jupiter.api.Test;

/** The stepper table: one row per (channel, which) pair, and the bounds the buttons use. */
class EditorStepsTest {
	static {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
	}

	@Test
	void everyChannelAndWhichPairMapsToItsRow() {
		assertEquals(EditorSteps.SPEED, EditorSteps.of(EditorSteps.CHANNEL_FLIGHT, FireworkEditorMenu.ADJUST_SPEED));
		assertEquals(EditorSteps.FLIGHT,
				EditorSteps.of(EditorSteps.CHANNEL_FLIGHT, FireworkEditorMenu.ADJUST_FLIGHT_TIME));
		assertEquals(EditorSteps.YAW, EditorSteps.of(EditorSteps.CHANNEL_DIRECTION, FireworkEditorMenu.ADJUST_YAW));
		assertEquals(EditorSteps.PITCH, EditorSteps.of(EditorSteps.CHANNEL_DIRECTION, FireworkEditorMenu.ADJUST_PITCH));
	}

	@Test
	void anUnknownPairHasNoStep() {
		// The four pairs are (0,0), (0,1), (1,0), (1,1); a channel or which outside them is unknown.
		assertNull(EditorSteps.of(2, 0));
		assertNull(EditorSteps.of(0, 2));
		assertNull(EditorSteps.of(99, 99));
	}

	@Test
	void flightOptionsUseTheSecondRowAndDirectionsUseTheDirectionStep() {
		assertEquals(EditorLayout.ROW1_Y, EditorSteps.SPEED.rowY());
		assertEquals(EditorLayout.ROW2_Y, EditorSteps.FLIGHT.rowY());
		assertEquals(EditorLayout.ROW1_Y, EditorSteps.YAW.rowY());
		assertEquals(EditorLayout.ROW2_Y, EditorSteps.PITCH.rowY());

		assertEquals(1, EditorSteps.SPEED.amount());
		assertEquals(EditorSteps.DIRECTION_STEP, EditorSteps.YAW.amount());
		assertEquals(EditorSteps.DIRECTION_STEP, EditorSteps.PITCH.amount());
		assertNotNull(EditorSteps.FLIGHT, "the enum table is complete");
	}
}
