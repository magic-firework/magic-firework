package com.magic.firework.core.eval;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import com.magic.firework.core.Transform;
import com.magic.firework.core.Vec3;

import org.junit.jupiter.api.Test;

/** A baked stream is shared by every burst, so a per-burst adjustment must build a new one. */
class FrameStreamTest {
	private static final float TOLERANCE = 1.0E-6F;

	private static FrameStream twoPoints() {
		return FrameStream.of(List.of(
				new FrameStream.Point(new Vec3(1.0, 0.0, 0.0), 0.0F, 0xFF0000, 1.0F),
				new FrameStream.Point(new Vec3(0.0, 0.0, 1.0), 2.0F, 0x00FF00, 2.0F)));
	}

	@Test
	void anUntouchedBurstReusesTheBakedStream() {
		FrameStream stream = twoPoints();

		assertSame(stream, stream.transformed(null, null));
	}

	@Test
	void aColourOverrideRecoloursEveryPointAndKeepsTheTiming() {
		FrameStream stream = twoPoints().transformed(null, 0x123456);

		assertEquals(0x123456, stream.point(0).argb());
		assertEquals(0x123456, stream.point(1).argb());
		assertEquals(2.0F, stream.point(1).time(), TOLERANCE);
		assertTrue(stream.isTimeOrdered());
	}

	@Test
	void scaleGrowsTheOffsetsAndTheParticleSize() {
		FrameStream stream = twoPoints().transformed(Transform.rotation(0.0, 0.0, 0.0, 2.0), null);

		assertEquals(2.0, stream.point(0).offset().x, 1.0E-9);
		assertEquals(4.0F, stream.point(1).size(), TOLERANCE, "a dot has no offset to grow, so the size carries it");
	}

	@Test
	void yawTurnsPlusXIntoMinusZ() {
		FrameStream stream = twoPoints().transformed(Transform.rotation(0.0, Math.PI / 2.0, 0.0, 1.0), null);

		assertEquals(0.0, stream.point(0).offset().x, 1.0E-9);
		assertEquals(-1.0, stream.point(0).offset().z, 1.0E-9);
	}

	@Test
	void theBakedStreamIsNeverMutated() {
		FrameStream baked = twoPoints();
		baked.transformed(Transform.rotation(0.0, Math.PI / 2.0, 0.0, 3.0), 0xFFFFFF);

		assertEquals(1.0, baked.point(0).offset().x, 1.0E-9);
		assertEquals(0xFF0000, baked.point(0).argb());
		assertEquals(1.0F, baked.point(0).size(), TOLERANCE);
	}
}
