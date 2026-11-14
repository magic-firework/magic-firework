package com.magic.firework.program;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.magic.firework.attribute.AttributeBag;
import com.magic.firework.attribute.Attributes;

import org.junit.jupiter.api.Test;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

class InterpreterTest {
	private static final ResourceLocation ROOT = new ResourceLocation("test", "root");
	private static final ResourceLocation CHILD = new ResourceLocation("test", "child");

	private static Program program(ResourceLocation id, Op... ops) {
		return new Program(id, List.of(ops));
	}

	private static ProgramLookup lookup(Program... programs) {
		Map<ResourceLocation, Program> map = new java.util.HashMap<>();
		for (Program program : programs) {
			map.put(program.id(), program);
		}
		return ProgramLookup.of(map);
	}

	private static List<Effect> tick(ProgramState state, ProgramLookup lookup) {
		state.tickAge();
		return Interpreter.tick(state, new InterpreterContext(lookup, Vec3.ZERO, new Vec3(0.0, 1.0, 0.0), false))
				.effects();
	}

	@Test
	void burstHappensAfterTheAwait() {
		Program root = program(ROOT,
				new Op.Await(new Cond.Ticks(5)),
				new Op.Burst(),
				new Op.End());
		ProgramState state = ProgramState.start(root, AttributeBag.EMPTY, 1L, 0);
		ProgramLookup lookup = lookup(root);

		List<Integer> burstAges = new ArrayList<>();
		for (int age = 1; age <= 10; age++) {
			List<Effect> effects = tick(state, lookup);
			if (effects.stream().anyMatch(effect -> effect instanceof Effect.Burst)) {
				burstAges.add(state.age());
			}
		}

		assertEquals(List.of(6), burstAges);
		assertTrue(state.done());
	}

	@Test
	void callRunsNestedProgramThenReturns() {
		Program child = program(CHILD, new Op.Burst(), new Op.End());
		Program root = program(ROOT, new Op.Call(CHILD), new Op.Burst(), new Op.End());
		ProgramState state = ProgramState.start(root, AttributeBag.EMPTY, 1L, 0);

		List<Effect> effects = tick(state, lookup(root, child));

		assertEquals(2, effects.stream().filter(effect -> effect instanceof Effect.Burst).count());
		assertTrue(state.done());
	}

	@Test
	void selfCallIsRefused() {
		Program root = program(ROOT, new Op.Call(ROOT), new Op.End());
		ProgramState state = ProgramState.start(root, AttributeBag.EMPTY, 1L, 0);

		tick(state, lookup(root));

		// If the cycle were accepted the stack would grow without bound; instead the
		// push is dropped and the program still terminates on the same tick.
		assertTrue(state.done());
		assertEquals(0, state.stackSize());
	}

	@Test
	void spawnRespectsChildLimit() {
		Program child = program(CHILD, new Op.End());
		Program root = program(ROOT, new Op.Spawn(CHILD, 8, 0.1F, 0), new Op.End());
		AttributeBag base = AttributeBag.EMPTY.withInt(Attributes.MAX_CHILDREN.key(), 3);
		ProgramState state = ProgramState.start(root, base, 1L, 0);

		List<Effect> effects = tick(state, lookup(root, child));

		assertEquals(3, effects.stream().filter(effect -> effect instanceof Effect.SpawnChild).count());
		assertEquals(3, state.childCount());
	}

	@Test
	void setMergesAttributesInOrder() {
		Program root = program(ROOT,
				new Op.Set(AttributeBag.EMPTY.withFloat(Attributes.SPEED.key(), 0.5F)),
				new Op.Set(AttributeBag.EMPTY.withFloat(Attributes.SPEED.key(), 0.9F)),
				new Op.End());
		ProgramState state = ProgramState.start(root, AttributeBag.EMPTY, 1L, 0);

		tick(state, lookup(root));

		assertEquals(0.9F, Attributes.SPEED.get(state.bag()), 1.0E-6);
	}

	@Test
	void launchUsesAttributeDirectionWhenPresent() {
		Program root = program(ROOT,
				new Op.Launch(Op.Direction.UP, Vec3.ZERO, 0.2F),
				new Op.End());
		AttributeBag base = AttributeBag.EMPTY
				.withString(Attributes.DIRECTION_MODE.key(), "vector")
				.withFloat(Attributes.DIRECTION_X.key(), 1.0F)
				.withFloat(Attributes.DIRECTION_Y.key(), 0.0F)
				.withFloat(Attributes.DIRECTION_Z.key(), 0.0F);
		ProgramState state = ProgramState.start(root, base, 1L, 0);

		List<Effect> effects = tick(state, lookup(root));
		Effect.SetVelocity velocity = (Effect.SetVelocity) effects.get(0);

		assertEquals(0.2, velocity.velocity().x, 1.0E-6);
		assertEquals(0.0, velocity.velocity().y, 1.0E-6);
	}

	@Test
	void relativeDirectionIsMeasuredFromTheLaunchersForward() {
		Program root = program(ROOT,
				new Op.Launch(Op.Direction.UP, Vec3.ZERO, 1.0F),
				new Op.End());
		ProgramLookup lookup = lookup(root);
		Vec3 facing = new Vec3(0.0, 0.0, 1.0);

		// Horizontal 0° is dead ahead; 90° is the launcher's right, which is -X when facing +Z.
		Vec3 ahead = relativeVelocity(root, lookup, 0.0F, 90.0F, facing);
		assertEquals(0.0, ahead.x, 1.0E-6);
		assertEquals(0.0, ahead.y, 1.0E-6);
		assertEquals(1.0, ahead.z, 1.0E-6);

		Vec3 right = relativeVelocity(root, lookup, 90.0F, 90.0F, facing);
		assertEquals(-1.0, right.x, 1.0E-6);
		assertEquals(0.0, right.z, 1.0E-6);

		// Vertical 0° points up no matter the heading.
		Vec3 up = relativeVelocity(root, lookup, 123.0F, 0.0F, facing);
		assertEquals(0.0, up.x, 1.0E-6);
		assertEquals(1.0, up.y, 1.0E-6);
		assertEquals(0.0, up.z, 1.0E-6);
	}

	private static Vec3 relativeVelocity(Program root, ProgramLookup lookup, float yaw, float pitch, Vec3 facing) {
		AttributeBag base = AttributeBag.EMPTY
				.withString(Attributes.DIRECTION_MODE.key(), "relative")
				.withFloat(Attributes.DIRECTION_YAW.key(), yaw)
				.withFloat(Attributes.DIRECTION_PITCH.key(), pitch);
		ProgramState state = ProgramState.start(root, base, 1L, 0);
		state.tickAge();
		List<Effect> effects = Interpreter.tick(state,
				new InterpreterContext(lookup, Vec3.ZERO, facing, false)).effects();
		return ((Effect.SetVelocity) effects.get(0)).velocity();
	}

	@Test
	void launchUsesAttributeSpeedWhenPresent() {
		Program root = program(ROOT,
				new Op.Launch(Op.Direction.UP, Vec3.ZERO, 0.2F),
				new Op.End());
		AttributeBag base = AttributeBag.EMPTY.withFloat(Attributes.SPEED.key(), 0.7F);
		ProgramState state = ProgramState.start(root, base, 1L, 0);

		Effect.SetVelocity velocity = (Effect.SetVelocity) tick(state, lookup(root)).get(0);

		assertEquals(0.7, velocity.velocity().y, 1.0E-6);
	}

	@Test
	void launchUsesOpSpeedWhenAttributeIsAbsent() {
		Program root = program(ROOT,
				new Op.Launch(Op.Direction.UP, Vec3.ZERO, 0.2F),
				new Op.End());
		ProgramState state = ProgramState.start(root, AttributeBag.EMPTY, 1L, 0);

		Effect.SetVelocity velocity = (Effect.SetVelocity) tick(state, lookup(root)).get(0);

		assertEquals(0.2, velocity.velocity().y, 1.0E-6);
	}

	@Test
	void secondLaunchKeepsItsOwnSpeedWhenNoOverride() {
		Program root = program(ROOT,
				new Op.Launch(Op.Direction.UP, Vec3.ZERO, 0.2F),
				new Op.Launch(Op.Direction.UP, Vec3.ZERO, 0.5F),
				new Op.End());
		ProgramState state = ProgramState.start(root, AttributeBag.EMPTY, 1L, 0);

		List<Effect> effects = tick(state, lookup(root));

		assertEquals(0.2, ((Effect.SetVelocity) effects.get(0)).velocity().y, 1.0E-6);
		assertEquals(0.5, ((Effect.SetVelocity) effects.get(1)).velocity().y, 1.0E-6);
	}

	@Test
	void attributeSpeedOverridesEveryLaunch() {
		Program root = program(ROOT,
				new Op.Launch(Op.Direction.UP, Vec3.ZERO, 0.2F),
				new Op.Launch(Op.Direction.UP, Vec3.ZERO, 0.5F),
				new Op.End());
		AttributeBag base = AttributeBag.EMPTY.withFloat(Attributes.SPEED.key(), 0.7F);
		ProgramState state = ProgramState.start(root, base, 1L, 0);

		List<Effect> effects = tick(state, lookup(root));

		assertEquals(0.7, ((Effect.SetVelocity) effects.get(0)).velocity().y, 1.0E-6);
		assertEquals(0.7, ((Effect.SetVelocity) effects.get(1)).velocity().y, 1.0E-6);
	}

	@Test
	void soundUsesAttributeOverrides() {
		Program root = program(ROOT,
				new Op.Sound(new ResourceLocation("minecraft", "entity.firework_rocket.blast"), 1.0F, 1.0F),
				new Op.End());
		AttributeBag base = AttributeBag.EMPTY
				.withString(Attributes.SOUND_ID, "minecraft:block.note_block.bell")
				.withFloat(Attributes.SOUND_VOLUME.key(), 2.0F)
				.withFloat(Attributes.SOUND_PITCH.key(), 0.5F);
		ProgramState state = ProgramState.start(root, base, 1L, 0);

		Effect.PlaySound sound = (Effect.PlaySound) tick(state, lookup(root)).get(0);

		assertEquals("minecraft:block.note_block.bell", sound.id().toString());
		assertEquals(2.0F, sound.volume(), 1.0E-6);
		assertEquals(0.5F, sound.pitch(), 1.0E-6);
	}

	@Test
	void lightUsesAttributeTicks() {
		Program root = program(ROOT, new Op.Light(20), new Op.End());
		AttributeBag base = AttributeBag.EMPTY.withInt(Attributes.LIGHT_TICKS.key(), 65);
		ProgramState state = ProgramState.start(root, base, 1L, 0);

		Effect.Light light = (Effect.Light) tick(state, lookup(root)).get(0);

		assertEquals(65, light.ticks());
	}

	@Test
	void fuseTicksOverridesOnlyTheAwaitBeforeBurst() {
		Program root = program(ROOT,
				new Op.Await(new Cond.Ticks(40)),
				new Op.Burst(),
				new Op.Await(new Cond.Ticks(10)),
				new Op.End());
		AttributeBag base = AttributeBag.EMPTY.withInt(Attributes.FUSE_TICKS.key(), 2);
		ProgramState state = ProgramState.start(root, base, 1L, 0);

		int burstAge = -1;
		int doneAge = -1;
		for (int age = 1; age <= 20; age++) {
			List<Effect> effects = tick(state, lookup(root));
			if (effects.stream().anyMatch(effect -> effect instanceof Effect.Burst)) {
				burstAge = state.age();
			}
			if (state.done()) {
				doneAge = state.age();
				break;
			}
		}

		assertEquals(3, burstAge, "fuse.ticks=2 should burst at age 3");
		assertEquals(13, doneAge, "the post-burst await must stay at 10 ticks");
	}

	@Test
	void programWithoutFramesIsDone() {
		ProgramState state = ProgramState.empty();
		Effect.TickResult result = Interpreter.tick(state,
				new InterpreterContext(lookup(), Vec3.ZERO, new Vec3(0.0, 1.0, 0.0), false));

		assertTrue(result.finished());
		assertTrue(result.effects().isEmpty());
		assertFalse(state.initialized());
	}

	@Test
	void savesAndResumesMidProgram() {
		Program root = program(ROOT, new Op.Await(new Cond.Ticks(20)), new Op.Burst(), new Op.End());
		ProgramState state = ProgramState.start(root, AttributeBag.EMPTY, 7L, 2);
		ProgramLookup lookup = lookup(root);
		for (int i = 0; i < 5; i++) {
			tick(state, lookup);
		}

		ProgramState resumed = ProgramState.load(state.save(), lookup, AttributeBag.EMPTY);

		assertEquals(5, resumed.age());
		assertEquals(7L, resumed.seed());
		assertEquals(2, resumed.depth());
		assertEquals(1, resumed.stackSize());
	}
}
