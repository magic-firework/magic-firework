package com.magic.firework.core;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class TransformTest {
	private static void assertVec(Vec3 expected, Vec3 actual) {
		assertEquals(expected.x, actual.x, 1.0E-9, "x");
		assertEquals(expected.y, actual.y, 1.0E-9, "y");
		assertEquals(expected.z, actual.z, 1.0E-9, "z");
	}

	@Test
	void identityKeepsPoints() {
		assertVec(new Vec3(1, 2, 3), Transform.IDENTITY.apply(new Vec3(1, 2, 3)));
	}

	@Test
	void rotationHappensBeforeTranslation() {
		Transform transform = Transform.of(new Vec3(10, 0, 0), 0.0, Math.PI / 2.0, 0.0, 1.0);
		// +X rotated a quarter turn around Y becomes -Z, then shifted to +10x.
		assertVec(new Vec3(10, 0, -1), transform.apply(new Vec3(1, 0, 0)));
	}

	@Test
	void composeScalesTheChildOffsetOnce() {
		Transform parent = Transform.of(Vec3.ZERO, 0, 0, 0, 2.0);
		Transform child = Transform.translation(new Vec3(1, 0, 0));

		// The classic double-scale bug: the offset must be scaled exactly once.
		assertVec(new Vec3(2, 0, 0), parent.compose(child).apply(Vec3.ZERO));
	}

	@Test
	void twoQuarterTurnsEqualAHalfTurn() {
		Transform quarter = Transform.rotation(0, Math.PI / 2.0, 0);
		assertVec(new Vec3(-1, 0, 0), quarter.compose(quarter).apply(new Vec3(1, 0, 0)));
	}
}
