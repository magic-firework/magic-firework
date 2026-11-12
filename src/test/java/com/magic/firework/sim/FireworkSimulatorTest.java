package com.magic.firework.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import com.magic.firework.attribute.AttributeBag;
import com.magic.firework.attribute.Attributes;
import com.magic.firework.program.Cond;
import com.magic.firework.program.Op;
import com.magic.firework.program.Program;
import com.magic.firework.program.ProgramLookup;

import org.junit.jupiter.api.Test;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

class FireworkSimulatorTest {
	private static final ResourceLocation UP = new ResourceLocation("test", "up");
	private static final ResourceLocation HOVER = new ResourceLocation("test", "hover");
	private static final ResourceLocation SPLIT = new ResourceLocation("test", "split");

	@Test
	void fliesUpAndBursts() {
		Program program = new Program(UP, List.of(
				new Op.Launch(Op.Direction.UP, Vec3.ZERO, 0.2F),
				new Op.Await(new Cond.Ticks(10)),
				new Op.Burst(),
				new Op.End()));

		FireworkSimulator.Result result = FireworkSimulator.run(program, ProgramLookup.of(java.util.Map.of()),
				AttributeBag.EMPTY, Vec3.ZERO, new Vec3(0.0, 1.0, 0.0), 100, Double.NEGATIVE_INFINITY);

		assertTrue(result.finished());
		assertEquals(1, result.bursts());
		assertTrue(result.maxHeight() > 1.5, "expected to climb, got " + result.maxHeight());
	}

	@Test
	void maxSpeedZeroHovers() {
		Program program = new Program(HOVER, List.of(
				new Op.Launch(Op.Direction.UP, Vec3.ZERO, 0.3F),
				new Op.Await(new Cond.Ticks(5)),
				new Op.Set(AttributeBag.EMPTY
						.withFloat(Attributes.MAX_SPEED.key(), 0.0F)
						.withFloat(Attributes.GRAVITY.key(), 0.0F)),
				new Op.Await(new Cond.Ticks(10)),
				new Op.End()));

		FireworkSimulator.Result result = FireworkSimulator.run(program, ProgramLookup.of(java.util.Map.of()),
				AttributeBag.EMPTY, Vec3.ZERO, new Vec3(0.0, 1.0, 0.0), 100, Double.NEGATIVE_INFINITY);

		assertTrue(result.finished());
		List<FireworkSimulator.Sample> samples = result.samples();
		Vec3 afterBrake = samples.get(7).position();
		Vec3 atEnd = samples.get(samples.size() - 1).position();
		assertEquals(afterBrake.y, atEnd.y, 1.0E-6);
	}

	@Test
	void groundCollisionSatisfiesAwait() {
		Program program = new Program(UP, List.of(
				new Op.Await(new Cond.Collision()),
				new Op.Burst(),
				new Op.End()));

		FireworkSimulator.Result result = FireworkSimulator.run(program, ProgramLookup.of(java.util.Map.of()),
				// gravity makes it fall onto the ground plane at y = 0.5
				AttributeBag.EMPTY.withFloat(Attributes.GRAVITY.key(), 0.05F),
				new Vec3(0.0, 1.0, 0.0), new Vec3(0.0, 1.0, 0.0), 100, 0.5);

		assertTrue(result.finished());
		assertEquals(1, result.bursts());
	}

	@Test
	void spawnEmitsChildrenWithinLimit() {
		Program child = new Program(new ResourceLocation("test", "child"), List.of(new Op.End()));
		Program program = new Program(SPLIT, List.of(
				new Op.Spawn(child.id(), 6, 0.2F, 0),
				new Op.End()));
		AttributeBag base = AttributeBag.EMPTY.withInt(Attributes.MAX_CHILDREN.key(), 4);

		FireworkSimulator.Result result = FireworkSimulator.run(program,
				ProgramLookup.of(java.util.Map.of(child.id(), child)), base, Vec3.ZERO,
				new Vec3(0.0, 1.0, 0.0), 20, Double.NEGATIVE_INFINITY);

		assertEquals(4, result.children());
		assertTrue(result.finished());
	}
}
