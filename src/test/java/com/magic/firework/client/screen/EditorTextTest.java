package com.magic.firework.client.screen;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** Player-facing number formatting: ticks to seconds, degrees to whole degrees. */
class EditorTextTest {
	@Test
	void flightTicksAreShownInSecondsWithoutTrailingZeros() {
		assertEquals("0s", EditorText.secondsText(0));
		assertEquals("1s", EditorText.secondsText(20));
		assertEquals("1.5s", EditorText.secondsText(30));
		assertEquals("0.05s", EditorText.secondsText(1));
	}

	@Test
	void degreesAreRoundedToWholeNumbers() {
		assertEquals("90°", EditorText.degreesText(90.4F));
		assertEquals("91°", EditorText.degreesText(90.6F));
		assertEquals("360°", EditorText.degreesText(359.9F));
	}
}
