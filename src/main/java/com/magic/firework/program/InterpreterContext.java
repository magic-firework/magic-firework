package com.magic.firework.program;

import net.minecraft.world.phys.Vec3;

/**
 * Read-only view of the host that conditions need. Deliberately tiny: the interpreter
 * must stay runnable in a unit test / the simulator without an entity.
 */
public record InterpreterContext(ProgramLookup programs, Vec3 position, Vec3 lookDirection, boolean collided) {
	public static InterpreterContext of(ProgramLookup programs, Vec3 position) {
		return new InterpreterContext(programs, position, new Vec3(0.0, 1.0, 0.0), false);
	}
}
