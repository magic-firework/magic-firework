package com.magic.firework.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import com.magic.firework.core.PointBudget;
import com.magic.firework.core.Vec3;
import com.magic.firework.core.eval.FrameStream;

import org.junit.jupiter.api.Test;

/** The playback cursor: due times, flash order, flicker echoes and the per-tick budget. */
class CloudCursorTest {
	private static FrameStream.Point puff(float time) {
		return new FrameStream.Point(Vec3.ZERO, time, 0xFFFFFFFF, 1.0F);
	}

	private static FrameStream.Point spark(float time) {
		return new FrameStream.Point(Vec3.ZERO, time, 0xFFFFFFFF, 1.0F, new Vec3(1.0, 0.0, 0.0), 10.0F, 0,
				false, false);
	}

	private static FrameStream.Flash flash(float time) {
		return new FrameStream.Flash(Vec3.ZERO, time, 0xFFFFFFFF);
	}

	private static final class Recorder {
		private final List<Float> echoes = new ArrayList<>();
		private final List<Float> flashes = new ArrayList<>();
		private final List<Float> points = new ArrayList<>();

		private void advance(CloudCursor cursor, long now, PointBudget budget) {
			cursor.advance(now, budget, p -> echoes.add(p.time()), f -> flashes.add(f.time()),
					p -> points.add(p.time()));
		}
	}

	@Test
	void pointsBecomeDueAtTheirTimeInOrder() {
		CloudCursor cursor = new CloudCursor(
				FrameStream.of(List.of(puff(2.0F), puff(0.0F), puff(1.0F))), false, 100L);
		PointBudget budget = new PointBudget(100);
		Recorder recorder = new Recorder();

		budget.beginTick();
		recorder.advance(cursor, 100L, budget);
		assertEquals(List.of(0.0F), recorder.points);
		assertFalse(cursor.done());

		budget.beginTick();
		recorder.advance(cursor, 101L, budget);
		assertEquals(List.of(0.0F, 1.0F), recorder.points);

		budget.beginTick();
		recorder.advance(cursor, 102L, budget);
		assertEquals(List.of(0.0F, 1.0F, 2.0F), recorder.points);
		assertTrue(cursor.done());
	}

	@Test
	void flashesAreEmittedBeforePointsAtTheSameTick() {
		CloudCursor cursor = new CloudCursor(FrameStream.of(List.of(puff(0.0F)), List.of(flash(0.0F))), false, 0L);
		PointBudget budget = new PointBudget(100);
		List<String> order = new ArrayList<>();

		budget.beginTick();
		cursor.advance(0L, budget, p -> order.add("echo"), f -> order.add("flash"), p -> order.add("point"));

		assertEquals(List.of("flash", "point"), order);
	}

	@Test
	void aFlickeringStaticPuffRepeatsTwice() {
		CloudCursor cursor = new CloudCursor(FrameStream.of(List.of(puff(0.0F))), true, 0L);
		PointBudget budget = new PointBudget(100);
		Recorder recorder = new Recorder();

		budget.beginTick();
		recorder.advance(cursor, 0L, budget);
		assertEquals(List.of(0.0F), recorder.points);
		assertTrue(recorder.echoes.isEmpty());

		budget.beginTick();
		recorder.advance(cursor, 7L, budget);
		assertTrue(recorder.echoes.isEmpty(), "the first echo is 8 ticks after the point");

		budget.beginTick();
		recorder.advance(cursor, 8L, budget);
		assertEquals(List.of(0.0F), recorder.echoes);

		budget.beginTick();
		recorder.advance(cursor, 16L, budget);
		assertEquals(List.of(0.0F, 0.0F), recorder.echoes);
		assertTrue(cursor.done());
	}

	@Test
	void aMovingSparkIsNotEchoed() {
		CloudCursor cursor = new CloudCursor(FrameStream.of(List.of(spark(0.0F))), true, 0L);
		PointBudget budget = new PointBudget(100);
		Recorder recorder = new Recorder();

		budget.beginTick();
		recorder.advance(cursor, 0L, budget);

		assertEquals(1, recorder.points.size());
		assertTrue(recorder.echoes.isEmpty());
		assertTrue(cursor.done());
	}

	@Test
	void anExhaustedBudgetStillAdvancesTheCursor() {
		CloudCursor cursor = new CloudCursor(FrameStream.of(List.of(puff(0.0F), puff(0.0F))), false, 0L);
		PointBudget budget = new PointBudget(1);
		Recorder recorder = new Recorder();

		budget.beginTick();
		recorder.advance(cursor, 0L, budget);

		assertEquals(1, recorder.points.size(), "one point is emitted");
		assertEquals(1, budget.droppedThisTick(), "the other is dropped, not replayed later");
		assertTrue(cursor.done(), "dropped points do not keep the cursor alive");
	}

	@Test
	void echoesShareTheSamePerTickBudget() {
		CloudCursor cursor = new CloudCursor(FrameStream.of(List.of(puff(0.0F), puff(8.0F))), true, 0L);
		PointBudget budget = new PointBudget(1);
		Recorder recorder = new Recorder();

		budget.beginTick();
		recorder.advance(cursor, 0L, budget); // the original consumes the only claim
		assertEquals(List.of(0.0F), recorder.points);

		budget.beginTick();
		recorder.advance(cursor, 8L, budget); // the echo is due first and wins the claim
		assertEquals(List.of(0.0F), recorder.echoes);
		assertEquals(1, budget.droppedThisTick(), "the new point at tick 8 is dropped");
	}
}
