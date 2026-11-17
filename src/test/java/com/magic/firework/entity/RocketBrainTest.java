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
import com.magic.firework.program.Effect;
import com.magic.firework.program.Op;
import com.magic.firework.program.Program;
import com.magic.firework.program.ProgramLookup;
import com.magic.firework.rocket.RocketSpec;

import org.junit.jupiter.api.Test;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

class RocketBrainTest {
	private static final ResourceLocation ROOT = new ResourceLocation("test", "root");
	private static final ResourceLocation CHILD = new ResourceLocation("test", "child");
	private static final Vec3 UP = new Vec3(0.0, 1.0, 0.0);
	private static Program program(ResourceLocation id, Op... ops) {
		return new Program(id, List.of(ops));
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
		Program root = program(ROOT, new Op.Wait.Ticks(100), new Op.End());
		RocketBrain brain = RocketBrain.launch(RocketSpec.of(ROOT), root, AttributeBag.EMPTY, 1L, 0, UP);

		assertNull(brain.scheduleChild(RocketSpec.of(CHILD), 3));
		assertTrue(brain.dueChildren().isEmpty());

		for (int i = 0; i < 3; i++) {
			brain.advanceAge();
		}
		assertEquals(1, brain.dueChildren().size());
		assertTrue(brain.dueChildren().isEmpty(), "a child is handed out only once");
	}

	@Test
	void immediateChildIsHandedBackRightAway() {
		Program root = program(ROOT, new Op.Wait.Ticks(100), new Op.End());
		RocketBrain brain = RocketBrain.launch(RocketSpec.of(ROOT), root, AttributeBag.EMPTY, 1L, 0, UP);

		RocketBrain.Child child = brain.scheduleChild(RocketSpec.of(CHILD), 0);

		assertNotNull(child);
		assertEquals(brain.age(), child.atAge());
	}

	@Test
	void expiresOnlyAfterTheAfterglow() {
		Program root = program(ROOT, new Op.End());
		AttributeBag base = AttributeBag.EMPTY.withInt(Attributes.AFTERGLOW.key(), 2);
		RocketBrain brain = RocketBrain.launch(RocketSpec.of(ROOT), root, base, 1L, 0, UP);

		brain.advanceAge();
		brain.tick(lookup(root));
		assertTrue(brain.done());
		assertFalse(brain.expired(), "first finished tick is still inside the afterglow");

		brain.advanceAge();
		brain.tick(lookup(root));
		assertFalse(brain.expired());

		brain.advanceAge();
		brain.tick(lookup(root));
		assertTrue(brain.expired());
	}

	@Test
	void saveAndLoadKeepsAgeSeedAndStack() {
		Program root = program(ROOT, new Op.Wait.Ticks(20), new Op.Burst(), new Op.End());
		RocketBrain brain = RocketBrain.launch(RocketSpec.of(ROOT), root, AttributeBag.EMPTY, 7L, 2, UP);
		brain.advanceAge();
		brain.tick(lookup(root));

		CompoundTag tag = new CompoundTag();
		brain.save(tag);
		RocketBrain restored = RocketBrain.load(tag, lookup(root));

		assertNotNull(restored);
		assertEquals(brain.age(), restored.age());
		assertEquals(brain.seed(), restored.seed());
		assertEquals(brain.depth(), restored.depth());
		assertEquals(brain.depthLimit(), restored.depthLimit());
		assertEquals(brain.bag(), restored.bag());
	}

	@Test
	void childSlotsComeFromTheLaunchingItem() {
		RocketSpec nested = new RocketSpec(CHILD,
				AttributeBag.EMPTY.withFloat(Attributes.SPEED.key(), 0.3F), List.of(RocketSpec.of(ROOT)));
		RocketSpec spec = new RocketSpec(ROOT, AttributeBag.EMPTY, List.of(nested));
		RocketBrain brain = RocketBrain.launch(spec, program(ROOT, new Op.End()), AttributeBag.EMPTY, 1L, 0, UP);

		assertEquals(nested, brain.child(0));
		assertNull(brain.child(1), "an unfilled slot is null, so the spawn is skipped");
		assertNull(brain.child(-1));
	}

	@Test
	void pendingChildrenAndTheChildTreeSurviveSaveAndLoad() {
		RocketSpec nested = RocketSpec.of(CHILD);
		RocketSpec spec = new RocketSpec(ROOT, AttributeBag.EMPTY, List.of(nested));
		Program root = program(ROOT, new Op.Wait.Ticks(100), new Op.End());
		RocketBrain brain = RocketBrain.launch(spec, root, AttributeBag.EMPTY, 3L, 0, UP);
		assertNull(brain.scheduleChild(nested, 3), "a delayed child is queued, not handed back");

		CompoundTag tag = new CompoundTag();
		brain.save(tag);
		RocketBrain restored = RocketBrain.load(tag, lookup(root));

		assertNotNull(restored);
		assertEquals(nested, restored.child(0), "a delayed child still needs its item tree after a reload");
		assertTrue(restored.dueChildren().isEmpty(), "the delay is relative to the age it was queued at");
		for (int i = 0; i < 3; i++) {
			restored.advanceAge();
		}
		assertEquals(1, restored.dueChildren().size(), "a reload no longer loses a queued child");
	}

	@Test
	void aCollisionSpendsTheWaitingTime() {
		Program root = program(ROOT, new Op.Wait.Ticks(50), new Op.Burst(), new Op.End());
		RocketBrain brain = RocketBrain.launch(RocketSpec.of(ROOT), root, AttributeBag.EMPTY, 1L, 0, UP);
		ProgramLookup lookup = lookup(root);

		brain.advanceAge();
		assertTrue(brain.tick(lookup).effects().isEmpty(), "50 ticks of flight time is a long wait");

		brain.setCollided(true);
		brain.advanceAge();
		List<Effect> effects = brain.tick(lookup).effects();

		assertEquals(1, effects.stream().filter(effect -> effect instanceof Effect.Burst).count(),
				"hitting a block ends the flight, so the burst happens on the spot");
	}

	@Test
	void missingProfileWithoutSavedStateIsOrphaned() {
		CompoundTag tag = new CompoundTag();
		tag.putString("Profile", "test:missing");

		assertNull(RocketBrain.load(tag, lookup()));
	}
}
