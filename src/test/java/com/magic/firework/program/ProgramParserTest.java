package com.magic.firework.program;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;

import com.google.gson.JsonParser;
import com.magic.firework.content.JsonToNbt;

import org.junit.jupiter.api.Test;

import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

class ProgramParserTest {
	private static final ResourceLocation ID = new ResourceLocation("test", "program");
	private static final ResourceLocation CHILD = new ResourceLocation("test", "child");

	@Test
	void parsesEveryOp() throws Exception {
		String json = """
				[
				  { "op": "launch", "speed": 0.2 },
				  { "op": "wait", "ticks": 25 },
				  { "op": "burst" },
				  { "op": "call", "profile": "test:child" },
				  { "op": "spawn", "profile": "test:child", "count": 3, "delay": 4 },
				  { "op": "sound", "id": "minecraft:entity.firework_rocket.launch", "volume": 2.0 },
				  { "op": "light", "ticks": 20 },
				  { "op": "end" }
				]
				""";
		Program program = ProgramParser.parse(ID, nbt(json));

		assertEquals(8, program.size());
		assertInstanceOf(Op.Launch.class, program.op(0));
		assertEquals(25, ((Op.Wait.Ticks) program.op(1)).ticks(), "an explicit wait keeps its number");
		assertInstanceOf(Op.Burst.class, program.op(2));
		assertInstanceOf(Op.Call.class, program.op(3));
		assertEquals(ID.getNamespace(), program.id().getNamespace());
		Op.Spawn spawn = (Op.Spawn) program.op(4);
		assertEquals(3, spawn.targets().size(), "count expands into concrete targets");
		assertEquals(4, spawn.delayTicks());
		assertInstanceOf(Op.Sound.class, program.op(5));
		assertInstanceOf(Op.Light.class, program.op(6));
		assertInstanceOf(Op.End.class, program.op(7));
	}

	@Test
	void flickerIsNotAnOp() {
		// It is an attribute now: a program that still says "flicker" must fail loudly.
		assertThrows(ProgramParser.ProgramException.class, () -> ProgramParser.parse(ID, nbt("""
				[{ "op": "flicker" }]
				""")));
	}

	@Test
	void aWaitWithoutTicksSpendsTheFlightTime() throws Exception {
		Program program = ProgramParser.parse(ID, nbt("""
				[{ "op": "wait" }]
				"""));

		assertInstanceOf(Op.Wait.Flight.class, program.op(0),
				"no number means the flight time the crafting table edits");
	}

	@Test
	void aWaitOfZeroIsRejected() {
		assertThrows(ProgramParser.ProgramException.class, () -> ProgramParser.parse(ID, nbt("""
				[{ "op": "wait", "ticks": 0 }]
				""")));
	}

	@Test
	void aWaitBeyondTheCapIsRejected() {
		assertThrows(ProgramParser.ProgramException.class, () -> ProgramParser.parse(ID, nbt("""
				[{ "op": "wait", "ticks": 401 }]
				""")));
	}

	@Test
	void aRemovedKeyIsAHardError() {
		// `at` and `when` are gone: silently ignoring them would run a different show than the
		// author wrote, so the profile is rejected instead.
		assertThrows(ProgramParser.ProgramException.class, () -> ProgramParser.parse(ID, nbt("""
				[{ "op": "burst", "at": 10 }]
				""")));
		assertThrows(ProgramParser.ProgramException.class, () -> ProgramParser.parse(ID, nbt("""
				[{ "op": "burst", "when": { "height": 40 } }]
				""")));
	}

	@Test
	void aMisspelledKeyIsAHardError() {
		assertThrows(ProgramParser.ProgramException.class, () -> ProgramParser.parse(ID, nbt("""
				[{ "op": "spawn", "profile": "test:child", "dalay": 2 }]
				""")));
	}

	@Test
	void spawnDefaultsAreSafe() throws Exception {
		Program program = ProgramParser.parse(ID, nbt("""
				[{ "op": "spawn", "profile": "test:child" }]
				"""));
		Op.Spawn spawn = (Op.Spawn) program.op(0);

		assertEquals(1, spawn.targets().size());
		assertEquals(0, spawn.delayTicks());
		assertEquals(SpawnTarget.of(CHILD), spawn.targets().get(0));
	}

	@Test
	void spawnChildrenNameItemSlots() throws Exception {
		Program program = ProgramParser.parse(ID, nbt("""
				[{ "op": "spawn", "children": [0, 2, 2], "delay": 3 }]
				"""));
		Op.Spawn spawn = (Op.Spawn) program.op(0);

		assertEquals(List.of(SpawnTarget.slot(0), SpawnTarget.slot(2), SpawnTarget.slot(2)), spawn.targets(),
				"slots keep their order and may repeat");
		assertEquals(3, spawn.delayTicks());
	}

	@Test
	void spawnWithBothFormsIsRejected() {
		assertThrows(ProgramParser.ProgramException.class, () -> ProgramParser.parse(ID, nbt("""
				[{ "op": "spawn", "profile": "test:child", "children": [0] }]
				""")));
	}

	@Test
	void spawnWithoutATargetIsRejected() {
		assertThrows(ProgramParser.ProgramException.class, () -> ProgramParser.parse(ID, nbt("""
				[{ "op": "spawn" }]
				""")));
	}

	@Test
	void countIsRejectedWithChildren() {
		assertThrows(ProgramParser.ProgramException.class, () -> ProgramParser.parse(ID, nbt("""
				[{ "op": "spawn", "children": [0], "count": 2 }]
				""")));
	}

	@Test
	void spawnSlotOutOfRangeIsRejected() {
		assertThrows(ProgramParser.ProgramException.class, () -> ProgramParser.parse(ID, nbt("""
				[{ "op": "spawn", "children": [-1] }]
				""")));
	}

	@Test
	void unknownOpIsRejected() {
		assertThrows(ProgramParser.ProgramException.class, () -> ProgramParser.parse(ID, nbt("""
				[{ "op": "teleport" }]
				""")));
	}

	@Test
	void anUnknownTimeOpIsRejected() {
		assertThrows(ProgramParser.ProgramException.class, () -> ProgramParser.parse(ID, nbt("""
				[{ "op": "delay", "ticks": 5 }]
				""")));
	}

	@Test
	void missingProgramIsRejected() {
		assertThrows(ProgramParser.ProgramException.class, () -> ProgramParser.parse(ID, nbt("""
				[{ "op": "call" }]
				""")));
	}

	@Test
	void nonListProgramIsRejected() {
		assertThrows(ProgramParser.ProgramException.class, () -> ProgramParser.parse(ID, nbt("{}")));
	}

	@Test
	void emptyProgramIsAllowed() throws Exception {
		Program program = ProgramParser.parse(ID, nbt("[]"));
		assertEquals(0, program.size());
		assertEquals(List.of(), program.ops());
	}

	@Test
	void jsonNumbersKeepTheirType() throws Exception {
		// Regression: JsonOps.convertTo(NbtOps) turns small numbers into bytes, and 0.25 would
		// then read as 0. JsonToNbt must preserve doubles.
		Program program = ProgramParser.parse(ID, nbt("""
				[{ "op": "launch", "speed": 0.25 }]
				"""));
		Op.Launch launch = (Op.Launch) program.op(0);

		assertEquals(0.25F, launch.speed(), 1.0E-6);
	}

	private static Tag nbt(String json) {
		return JsonToNbt.convert(JsonParser.parseString(json));
	}
}
