package com.magic.firework.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.magic.firework.attribute.AttributeBag;
import com.magic.firework.attribute.Attributes;
import com.magic.firework.program.Instruction;
import com.magic.firework.program.Op;
import com.magic.firework.program.When;
import com.magic.firework.program.Program;
import com.magic.firework.program.ProgramLookup;

import org.junit.jupiter.api.Test;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

class RocketBrainTest {
	private static final ResourceLocation ROOT = new ResourceLocation("test", "root");
	private static final ResourceLocation CHILD = new ResourceLocation("test", "child");
	private static final Vec3 UP = new Vec3(0.0, 1.0, 0.0);

	private static Program program(ResourceLocation id, Op... ops) {
		return new Program(id, java.util.Arrays.stream(ops).map(Instruction::now).toList());
	}

	private static Program timed(ResourceLocation id, Instruction... instructions) {
		return new Program(id, List.of(instructions));
	}

	private static Instruction at(int tick, Op op) {
		return new Instruction(op, tick, When.NONE);
	}

	private static ProgramLookup lookup(Program... programs) {
		Map<ResourceLocation, Program> map = new HashMap<>();
		for (Program program : programs) {
			map.put(program.id(), program);
		}
		return ProgramLookup.of(map);
	}

	@Test
	void delayedChildAppearsAfterItsTick() {
		Program root = timed(ROOT, at(100, new Op.End()));
		RocketBrain brain = RocketBrain.launch(ROOT, root, AttributeBag.EMPTY, 1L, 0, UP);

		assertNull(brain.scheduleChild(CHILD, 0.1F, 3));
		assertTrue(brain.dueChildren().isEmpty());

		for (int i = 0; i < 3; i++) {
			brain.advanceAge();
		}
		assertEquals(1, brain.dueChildren().size());
		assertTrue(brain.dueChildren().isEmpty(), "a child is handed out only once");
	}

	@Test
	void immediateChildIsHandedBackRightAway() {
		Program root = timed(ROOT, at(100, new Op.End()));
		RocketBrain brain = RocketBrain.launch(ROOT, root, AttributeBag.EMPTY, 1L, 0, UP);

		RocketBrain.Child child = brain.scheduleChild(CHILD, 0.1F, 0);

		assertNotNull(child);
		assertEquals(brain.age(), child.atAge());
	}

	@Test
	void expiresOnlyAfterTheAfterglow() {
		Program root = program(ROOT, new Op.End());
		AttributeBag base = AttributeBag.EMPTY.withInt(Attributes.AFTERGLOW.key(), 2);
		RocketBrain brain = RocketBrain.launch(ROOT, root, base, 1L, 0, UP);

		brain.advanceAge();
		brain.tick(lookup(root), Vec3.ZERO);
		assertTrue(brain.done());
		assertFalse(brain.expired(), "first finished tick is still inside the afterglow");

		brain.advanceAge();
		brain.tick(lookup(root), Vec3.ZERO);
		assertFalse(brain.expired());

		brain.advanceAge();
		brain.tick(lookup(root), Vec3.ZERO);
		assertTrue(brain.expired());
	}

	@Test
	void saveAndLoadKeepsAgeSeedAndStack() {
		Program root = timed(ROOT, at(20, new Op.Burst()), at(20, new Op.End()));
		RocketBrain brain = RocketBrain.launch(ROOT, root, AttributeBag.EMPTY, 7L, 2, UP);
		brain.advanceAge();
		brain.tick(lookup(root), Vec3.ZERO);

		CompoundTag tag = new CompoundTag();
		brain.save(tag);
		RocketBrain restored = RocketBrain.load(tag, lookup(root));

		assertNotNull(restored);
		assertEquals(brain.age(), restored.age());
		assertEquals(brain.seed(), restored.seed());
		assertEquals(brain.depth(), restored.depth());
		assertEquals(brain.bag(), restored.bag());
	}

	@Test
	void missingProfileWithoutSavedStateIsOrphaned() {
		CompoundTag tag = new CompoundTag();
		tag.putString("Profile", "test:missing");

		assertNull(RocketBrain.load(tag, lookup()));
	}
}
