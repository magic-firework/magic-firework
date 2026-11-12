package com.magic.firework.program;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import com.google.gson.JsonParser;
import com.magic.firework.attribute.Attributes;
import com.magic.firework.content.JsonToNbt;

import org.junit.jupiter.api.Test;

import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

class ProgramParserTest {
	private static final ResourceLocation ID = new ResourceLocation("test", "program");

	@Test
	void parsesEveryOp() throws Exception {
		String json = """
				[
				  { "op": "launch", "direction": "vector", "vector": [0.0, 1.0, 0.0], "speed": 0.2 },
				  { "op": "await", "ticks": 5 },
				  { "op": "await", "height": 42.0, "timeout": 100 },
				  { "op": "await", "collision": true },
				  { "op": "set", "attributes": { "flight.speed": 0.0 } },
				  { "op": "burst" },
				  { "op": "call", "program": "test:child" },
				  { "op": "spawn", "program": "test:child", "count": 3, "spread": 0.2, "delay": 4 },
				  { "op": "sound", "id": "minecraft:entity.firework_rocket.launch", "volume": 2.0 },
				  { "op": "light", "ticks": 20 },
				  { "op": "end" }
				]
				""";
		Program program = ProgramParser.parse(ID, nbt(json));

		assertEquals(11, program.size());
		assertInstanceOf(Op.Launch.class, program.op(0));
		assertInstanceOf(Op.Await.class, program.op(1));
		assertInstanceOf(Op.Await.class, program.op(2));
		assertTrue(((Op.Await) program.op(2)).cond() instanceof Cond.Or);
		assertInstanceOf(Op.Await.class, program.op(3));
		assertInstanceOf(Op.Set.class, program.op(4));
		assertInstanceOf(Op.Burst.class, program.op(5));
		assertInstanceOf(Op.Call.class, program.op(6));
		assertInstanceOf(Op.Spawn.class, program.op(7));
		assertInstanceOf(Op.Sound.class, program.op(8));
		assertInstanceOf(Op.Light.class, program.op(9));
		assertInstanceOf(Op.End.class, program.op(10));
	}

	@Test
	void spawnDefaultsAreSafe() throws Exception {
		Program program = ProgramParser.parse(ID, nbt("""
				[{ "op": "spawn", "program": "test:child" }]
				"""));
		Op.Spawn spawn = (Op.Spawn) program.op(0);

		assertEquals(1, spawn.count());
		assertEquals(0.0F, spawn.spread(), 1.0E-6);
		assertEquals(0, spawn.delayTicks());
	}

	@Test
	void unknownOpIsRejected() {
		assertThrows(ProgramParser.ProgramException.class, () -> ProgramParser.parse(ID, nbt("""
				[{ "op": "teleport" }]
				""")));
	}

	@Test
	void awaitWithoutConditionUsesCodeDefault() throws Exception {
		Program program = ProgramParser.parse(ID, nbt("""
				[{ "op": "await" }]
				"""));
		Op.Await await = (Op.Await) program.op(0);

		assertEquals(Attributes.FUSE_TICKS.def(), ((Cond.Ticks) await.cond()).ticks());
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
		// Regression: JsonOps.convertTo(NbtOps) turns [0.0, 0.5, 0.0] into bytes and
		// truncates every float in a data pack. JsonToNbt must preserve doubles.
		Program program = ProgramParser.parse(ID, nbt("""
				[{ "op": "launch", "direction": "vector", "vector": [0.0, 0.5, 0.0], "speed": 0.25 }]
				"""));
		Op.Launch launch = (Op.Launch) program.op(0);

		assertEquals(new Vec3(0.0, 0.5, 0.0), launch.vector());
		assertEquals(0.25F, launch.speed(), 1.0E-6);
	}

	private static Tag nbt(String json) {
		return JsonToNbt.convert(JsonParser.parseString(json));
	}
}
