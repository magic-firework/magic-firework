package com.magic.firework.program;

import net.minecraft.world.phys.Vec3;

/**
 * Read-only view of the host an op needs: program lookup for {@link Op.Call} and the launcher's
 * heading for {@link Op.Launch} in {@code look} / {@code relative} mode.
 *
 * <p>Deliberately tiny: no {@code Level}, no entity, no position. Time is spent by
 * {@link Op.Wait}, never by reading the world, so a program runs identically on the server, in
 * the headless simulator and in a unit test.
 */
public record InterpreterContext(ProgramLookup programs, Vec3 lookDirection) {
}
