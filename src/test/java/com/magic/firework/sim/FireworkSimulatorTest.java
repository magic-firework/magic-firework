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
	private static final ResourceLocation SPLIT = new ResourceLocation("test", "split");
	private static final Vec3 UP_LOOK = new Vec3(0.0, 1.0, 0.0);

	@Test
	void fliesUpAndBursts() {
		Program program = new Program(UP, List.of(
				new Op.Launch(0.6F),
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
				new Op.Launch(0.6F),
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
	void theBrakeBleedsTheRiseToAHover() {
		Program program = new Program(UP, List.of(
				new Op.Launch(0.4F),
				new Op.Wait.Ticks(40),
				new Op.End()));

		FireworkSimulator.Result result = FireworkSimulator.run(program, null, ProgramLookup.of(Map.of()),
				AttributeBag.EMPTY, Vec3.ZERO, UP_LOOK, 100, Double.NEGATIVE_INFINITY);

		List<FireworkSimulator.Sample> samples = result.samples();
		// One tick of braking takes 0.08 off the speed...
		assertEquals(0.32, samples.get(1).velocity().y, 1.0E-6);
		// ...down to rest, and never past it: the rest of the wait is spent hovering.
		for (FireworkSimulator.Sample sample : samples) {
			assertTrue(sample.velocity().y >= 0.0, "the brake must never point the rocket down");
		}
		assertEquals(1.2, samples.get(samples.size() - 1).position().y, 1.0E-6);
		assertTrue(result.finished());
	}

	@Test
	void anAimedShotStopsWithoutChangingItsLine() {
		// 45° up and 45° to the launcher's right: the same brake, taken off the whole speed, so the
		// shot keeps its heading all the way and simply runs out of it.
		AttributeBag base = AttributeBag.EMPTY
				.withFloat(Attributes.DIRECTION_YAW.key(), 45.0F)
				.withFloat(Attributes.DIRECTION_PITCH.key(), 45.0F);
		Program program = new Program(UP, List.of(
				new Op.Launch(0.4F),
				new Op.Wait.Ticks(40),
				new Op.End()));

		FireworkSimulator.Result result = FireworkSimulator.run(program, null, ProgramLookup.of(Map.of()),
				base, Vec3.ZERO, UP_LOOK, 100, Double.NEGATIVE_INFINITY);

		Vec3 heading = new Vec3(-0.5, Math.sqrt(0.5), 0.5);
		List<FireworkSimulator.Sample> samples = result.samples();
		for (FireworkSimulator.Sample sample : samples) {
			if (sample.velocity().lengthSqr() > 1.0E-9) {
				assertTrue(sample.velocity().cross(heading).lengthSqr() < 1.0E-9,
						"braking must not bend the flight line: " + sample.velocity());
			}
		}
		Vec3 atRest = samples.get(samples.size() - 1).position();
		assertEquals(1.2 * heading.x, atRest.x, 1.0E-6);
		assertEquals(1.2 * heading.y, atRest.y, 1.0E-6);
		assertEquals(1.2 * heading.z, atRest.z, 1.0E-6);
		assertEquals(0.0, samples.get(samples.size() - 1).velocity().length(), 1.0E-9);
	}

	@Test
	void spawnEmitsChildrenWithinLimit() {
		Program child = new Program(new ResourceLocation("test", "child"), List.of(new Op.End()));
		Program program = new Program(SPLIT, List.of(
				new Op.Spawn(List.of(SpawnTarget.of(child.id()), SpawnTarget.of(child.id()),
						SpawnTarget.of(child.id()), SpawnTarget.of(child.id())), 0),
				new Op.End()));
		AttributeBag base = AttributeBag.EMPTY;

		FireworkSimulator.Result result = FireworkSimulator.run(program, RocketSpec.of(SPLIT),
				ProgramLookup.of(Map.of(child.id(), child)), base, Vec3.ZERO, UP_LOOK, 20,
				Double.NEGATIVE_INFINITY);

		assertEquals(4, result.children());
		assertTrue(result.finished());
	}
}
