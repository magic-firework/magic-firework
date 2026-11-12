package com.magic.firework.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.magic.firework.core.eval.Evaluate;
import com.magic.firework.core.eval.FrameStream;
import com.magic.firework.core.eval.Plan;
import com.magic.firework.core.path.Path;
import com.magic.firework.core.shape.Form;
import com.magic.firework.core.shape.Ink;
import com.magic.firework.core.show.EventSpec;
import com.magic.firework.core.show.Show;
import com.magic.firework.core.show.Stamp;

class EvaluateTest {
	private static final Id SHOW = new Id("test", "show");
	private static final Id DOT = new Id("test", "dot");
	private static final Ink DOT_INK = Ink.dot(DOT, 1.0);

	private static Form dot() {
		return Form.leaf(DOT_INK);
	}

	private static Stamp stamp(Form form, int at, int duration, Path path) {
		return new Stamp(form, at, duration, path, Transform.IDENTITY, 1.0, 0.0, 0.0, List.of());
	}

	private static Show show(Stamp... stamps) {
		return new Show(SHOW, new int[] { 0xFFFFFF }, List.of(stamps));
	}

	private static Plan plan(Show show) {
		return Evaluate.evaluate(show, new Seed(7L), Budget.of(256));
	}

	private static Ink cloud(int points) {
		Vec3[] vertices = new Vec3[points];
		double[] sizes = new double[points];
		for (int i = 0; i < points; i++) {
			double angle = Math.PI * 2.0 * i / points;
			vertices[i] = new Vec3(Math.cos(angle), Math.sin(angle), 0.0);
			sizes[i] = 1.0;
		}
		return Ink.of(new Id("test", "cloud" + points), vertices, new int[0], sizes);
	}

	@Test
	void burstStampsOnceAtTheDelayedTick() {
		Plan plan = plan(show(stamp(dot(), 5, 0, null)));

		assertEquals(1, plan.stream().size());
		assertEquals(5.0F, plan.stream().point(0).time(), 1.0E-6);
	}

	@Test
	void trailWalksThePathOverTime() {
		Plan plan = plan(show(stamp(dot(), 0, 4, new Path.Line(1.0))));

		FrameStream stream = plan.stream();
		assertEquals(4, stream.size());
		assertEquals(0.0F, stream.point(0).time(), 1.0E-6);
		assertEquals(3.0F, stream.point(3).time(), 1.0E-6);
		assertTrue(stream.point(3).offset().y > stream.point(0).offset().y, "the pen must move up the path");
	}

	@Test
	void placeAndPathCompose() {
		Stamp placed = new Stamp(dot(), 0, 2, new Path.Line(2.0),
				Transform.translation(new Vec3(0.0, 3.0, 0.0)), 1.0, 0.0, 0.0, List.of());

		FrameStream stream = plan(show(placed)).stream();

		assertEquals(2, stream.size());
		assertEquals(4.0, stream.point(0).offset().y, 1.0E-9);
		assertEquals(5.0, stream.point(1).offset().y, 1.0E-9);
	}

	@Test
	void budgetIsAnUpperBound() {
		Plan plan = Evaluate.evaluate(show(stamp(Form.leaf(cloud(16)), 0, 0, null)), new Seed(1L), Budget.of(5));

		assertEquals(5, plan.stream().size());
	}

	@Test
	void sizeAndColorOffsetAreAppliedByTheForm() {
		Ink ink = Ink.of(new Id("test", "inked"), new Vec3[] { Vec3.ZERO }, new int[] { 1 }, new double[] { 2.0 });
		Form form = new Form.Leaf(ink, Transform.IDENTITY, 2, 3.0);
		Stamp stamp = new Stamp(form, 0, 0, null, Transform.IDENTITY, 1.0, 0.0, 0.0, List.of());

		FrameStream.Point point = plan(show(stamp)).stream().point(0);

		assertEquals(6.0F, point.size(), 1.0E-6);
		assertEquals(0xFFFFFFFF, point.argb());
	}

	@Test
	void eventsAreScheduledAtTheStampFrame() {
		Stamp stamp = new Stamp(dot(), 2, 0, null,
				Transform.translation(new Vec3(0.0, 3.0, 0.0)), 1.0, 0.0, 0.0,
				List.of(EventSpec.sound(3, new Id("minecraft", "entity.firework_rocket.blast"), 4.0, 1.0)));

		Plan plan = plan(show(stamp));

		assertEquals(1, plan.events().size());
		assertEquals(5.0F, plan.events().get(0).time(), 1.0E-6, "delay 2 + event tick 3");
		assertEquals(3.0, plan.events().get(0).at().y, 1.0E-9);
	}

	@Test
	void overlappingStampsStayTimeOrdered() {
		Stamp slow = stamp(dot(), 0, 10, new Path.Line(1.0));
		Stamp quick = stamp(dot(), 0, 0, null);

		FrameStream stream = plan(show(slow, quick)).stream();

		assertTrue(stream.size() > 1);
		assertTrue(stream.isTimeOrdered(), "the client walks the stream with one cursor");
	}

	@Test
	void sameSeedGivesTheSamePlan() {
		Show show = show(stamp(dot(), 0, 6, new Path.Line(1.0)),
				new Stamp(dot(), 1, 0, null, Transform.translation(new Vec3(0.0, 1.0, 0.0)),
						1.0, 0.0, 0.0, List.of()));

		FrameStream first = Evaluate.evaluate(show, new Seed(99L), Budget.of(64)).stream();
		FrameStream second = Evaluate.evaluate(show, new Seed(99L), Budget.of(64)).stream();

		assertEquals(first.size(), second.size());
		for (int i = 0; i < first.size(); i++) {
			assertEquals(first.point(i).offset(), second.point(i).offset());
			assertEquals(first.point(i).time(), second.point(i).time(), 0.0F);
		}
	}

	@Test
	void ringExpandsOncePerCopy() {
		Form ring = new Form.Ring(Form.leaf(cloud(4)), 6, 2.0, 360.0, Transform.IDENTITY, 0, 1.0);

		Plan plan = Evaluate.evaluate(show(stamp(ring, 0, 0, null)), new Seed(1L), Budget.of(1000));

		assertEquals(24, plan.stream().size());
	}

	@Test
	void formExpansionStopsAtTheBudget() {
		Form ring = new Form.Ring(Form.leaf(cloud(4)), 6, 2.0, 360.0, Transform.IDENTITY, 0, 1.0);

		Plan plan = Evaluate.evaluate(show(stamp(ring, 0, 0, null)), new Seed(1L), Budget.of(10));

		assertEquals(10, plan.stream().size());
	}

	@Test
	void exhaustedBudgetStopsExpansionImmediately() {
		Form ring = new Form.Ring(Form.leaf(cloud(4)), 256, 2.0, 360.0, Transform.IDENTITY, 0, 1.0);

		Plan plan = Evaluate.evaluate(show(stamp(ring, 0, 4096, new Path.Line(1.0))),
				new Seed(1L), Budget.of(0));

		assertTrue(plan.stream().isEmpty());
	}

	@Test
	void longDurationsCannotOutrunTheBudget() {
		Plan plan = Evaluate.evaluate(show(stamp(Form.leaf(cloud(16)), 0, 4096, new Path.Line(1.0))),
				new Seed(1L), Budget.of(32));

		assertEquals(32, plan.stream().size());
	}

	@Test
	void mirrorReflectsAcrossTheRequestedAxes() {
		Ink right = Ink.of(new Id("test", "right"), new Vec3[] { new Vec3(1.0, 0.0, 0.0) }, new int[0], new double[0]);
		Form mirror = new Form.Mirror(Form.leaf(right), true, false, false, Transform.IDENTITY, 0, 1.0);

		List<FrameStream.Point> points = Evaluate.evaluate(show(stamp(mirror, 0, 0, null)),
				new Seed(1L), Budget.of(8)).stream().points();

		assertEquals(2, points.size());
		assertEquals(0.0, points.get(0).offset().x + points.get(1).offset().x, 1.0E-9);
		assertTrue(points.get(0).offset().x != points.get(1).offset().x);
	}

	@Test
	void groupComposesTransformsAndColours() {
		Form group = new Form.Group(List.of(
				new Form.Leaf(DOT_INK, Transform.translation(new Vec3(0.0, 1.0, 0.0)), 0, 1.0),
				new Form.Leaf(DOT_INK, Transform.translation(new Vec3(0.0, 2.0, 0.0)), 1, 1.0)),
				Transform.translation(new Vec3(0.0, 10.0, 0.0)), 0, 1.0);

		List<FrameStream.Point> points = Evaluate.evaluate(show(stamp(group, 0, 0, null)),
				new Seed(1L), Budget.of(8)).stream().points();

		assertEquals(2, points.size());
		assertEquals(11.0, points.get(0).offset().y, 1.0E-9);
		assertEquals(12.0, points.get(1).offset().y, 1.0E-9);
	}
}
