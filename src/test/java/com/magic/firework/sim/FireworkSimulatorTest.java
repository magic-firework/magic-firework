package com.magic.firework.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import com.magic.firework.attribute.AttributeBag;
import com.magic.firework.attribute.Attributes;
import com.magic.firework.program.Op;
import com.magic.firework.program.Program;
import com.magic.firework.program.ProgramLookup;
import com.magic.firework.program.SpawnTarget;
import com.magic.firework.rocket.RocketSpec;

import org.junit.jupiter.api.Test;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

class FireworkSimulatorTest {
	private static final ResourceLocation UP = new ResourceLocation("test", "up");
	private static final ResourceLocation HOVER = new ResourceLocation("test", "hover");
	private static final ResourceLocation SPLIT = new ResourceLocation("test", "split");
	private static final Vec3 UP_LOOK = new Vec3(0.0, 1.0, 0.0);

	@Test
	void fliesUpAndBursts() {
		Program program = new Program(UP, List.of(
				new Op.Launch(0.2F),
				new Op.Wait.Ticks(10),
				new Op.Burst(),
				new Op.End()));

		FireworkSimulator.Result result = FireworkSimulator.run(program, null, ProgramLookup.of(Map.of()),
				AttributeBag.EMPTY, Vec3.ZERO, UP_LOOK, 100, Double.NEGATIVE_INFINITY);

		assertTrue(result.finished());
		assertEquals(1, result.bursts());
		assertTrue(result.maxHeight() > 1.5, "expected to climb, got " + result.maxHeight());
	}

	@Test
	void aWaitKeepsTheRocketFlyingBeforeTheBurst() {
		Program program = new Program(UP, List.of(
				new Op.Launch(0.2F),
				new Op.Wait.Ticks(10),
				new Op.Burst(),
				new Op.End()));

		FireworkSimulator.Result result = FireworkSimulator.run(program, null, ProgramLookup.of(Map.of()),
				AttributeBag.EMPTY, Vec3.ZERO, UP_LOOK, 100, Double.NEGATIVE_INFINITY);

		// The burst is the last sample, so its position must be well above the launch point.
		FireworkSimulator.Sample burst = result.samples().get(result.samples().size() - 1);
		assertTrue(burst.position().y > 1.0, "the wait is flight time, not a freeze");
	}

	@Test
	void maxSpeedZeroKeepsTheRocketInOnePlace() {
		Program program = new Program(HOVER, List.of(
				new Op.Launch(0.3F),
				new Op.Wait.Ticks(20),
				new Op.End()));
		// Attributes are fixed at launch: hovering is a property of the bag, not an op.
		AttributeBag base = AttributeBag.EMPTY
				.withFloat(Attributes.MAX_SPEED.key(), 0.0F)
				.withFloat(Attributes.GRAVITY.key(), 0.0F);

		FireworkSimulator.Result result = FireworkSimulator.run(program, null, ProgramLookup.of(Map.of()),
				base, Vec3.ZERO, UP_LOOK, 100, Double.NEGATIVE_INFINITY);

		assertTrue(result.finished());
		List<FireworkSimulator.Sample> samples = result.samples();
		Vec3 settled = samples.get(1).position();
		Vec3 atEnd = samples.get(samples.size() - 1).position();
		assertEquals(settled.y, atEnd.y, 1.0E-6);
	}

	@Test
	void gravityBringsTheRocketDownWhileItWaits() {
		Program program = new Program(UP, List.of(
				new Op.Launch(0.2F),
				new Op.Wait.Ticks(40),
				new Op.End()));

		FireworkSimulator.Result result = FireworkSimulator.run(program, null, ProgramLookup.of(Map.of()),
				AttributeBag.EMPTY.withFloat(Attributes.GRAVITY.key(), 0.05F), Vec3.ZERO, UP_LOOK, 100,
				Double.NEGATIVE_INFINITY);

		double highest = result.maxHeight();
		double last = result.lastPosition().y;
		assertTrue(last < highest, "gravity pulls it back down while the program waits");
	}

	@Test
	void spawnEmitsChildrenWithinLimit() {
		Program child = new Program(new ResourceLocation("test", "child"), List.of(new Op.End()));
		Program program = new Program(SPLIT, List.of(
				new Op.Spawn(List.of(SpawnTarget.of(child.id()), SpawnTarget.of(child.id()),
						SpawnTarget.of(child.id()), SpawnTarget.of(child.id())), 0),
				new Op.End()));
		AttributeBag base = AttributeBag.EMPTY.withInt(Attributes.MAX_CHILDREN.key(), 4);

		FireworkSimulator.Result result = FireworkSimulator.run(program, RocketSpec.of(SPLIT),
				ProgramLookup.of(Map.of(child.id(), child)), base, Vec3.ZERO, UP_LOOK, 20,
				Double.NEGATIVE_INFINITY);

		assertEquals(4, result.children());
		assertTrue(result.finished());
	}
}
