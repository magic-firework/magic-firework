package com.magic.firework.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import org.junit.jupiter.api.Test;

import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;

/** The command tree without a server: which arguments parse, and which are rejected. */
class FireworkCommandTest {
	/** Permission level 2, matching the root requirement; no level or server is touched while parsing. */
	private static final CommandSourceStack SOURCE = new CommandSourceStack(CommandSource.NULL, Vec3.ZERO, Vec2.ZERO,
			null, 2, "test", Component.literal("test"), null, null);

	private static ParseResults<CommandSourceStack> parse(String command) throws CommandSyntaxException {
		CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();
		FireworkCommand.register(dispatcher);
		return dispatcher.parse(command, SOURCE);
	}

	private static CommandContext<CommandSourceStack> context(ParseResults<CommandSourceStack> parsed,
			String command) {
		return parsed.getContext().build(command);
	}

	/** Brigadier reports leftover input as a readable reader, and bad arguments in the exception map. */
	private static void assertRejected(String command) throws Exception {
		ParseResults<CommandSourceStack> parsed = parse(command);
		assertTrue(parsed.getReader().canRead() || !parsed.getExceptions().isEmpty(),
				command + " should be rejected, but parsed cleanly");
	}

	private static void assertAccepted(String command) throws Exception {
		ParseResults<CommandSourceStack> parsed = parse(command);
		assertFalse(parsed.getReader().canRead(), command + " left input unread");
		assertTrue(parsed.getExceptions().isEmpty(), command + " has parse errors: " + parsed.getExceptions());
	}

	@Test
	void giveTakesAProfileAndAnOptionalBoundedCount() throws Exception {
		String withCount = "magicfirework give magic-firework:ring12 3";
		assertAccepted(withCount);
		ParseResults<CommandSourceStack> parsed = parse(withCount);
		assertEquals(new ResourceLocation("magic-firework", "ring12"),
				ResourceLocationArgument.getId(context(parsed, withCount), "profile"));
		assertEquals(3, IntegerArgumentType.getInteger(context(parsed, withCount), "count"));

		String defaultCount = "magicfirework give magic-firework:ring12";
		assertAccepted(defaultCount);
		assertEquals(new ResourceLocation("magic-firework", "ring12"),
				ResourceLocationArgument.getId(context(parse(defaultCount), defaultCount), "profile"));
	}

	@Test
	void simTakesABoundedTicksArgument() throws Exception {
		String sim = "magicfirework sim magic-firework:ring12 600";
		assertAccepted(sim);
		assertEquals(600, IntegerArgumentType.getInteger(context(parse(sim), sim), "ticks"));

		assertRejected("magicfirework sim magic-firework:ring12 601");
	}

	@Test
	void libraryNamesAreSingleWords() throws Exception {
		assertAccepted("magicfirework save peony");
		assertAccepted("magicfirework load peony");
		assertAccepted("magicfirework delete peony");
		assertAccepted("magicfirework export peony");

		assertRejected("magicfirework save two words");
	}

	@Test
	void missingOrOutOfRangeArgumentsAreRejected() throws Exception {
		// A bare literal is not a parse error, but it has no executor and must not run.
		assertNull(context(parse("magicfirework give"), "magicfirework give").getCommand());
		assertRejected("magicfirework give magic-firework:ring12 0");
		assertRejected("magicfirework give magic-firework:ring12 65");
	}

	@Test
	void unknownSubcommandsAreRejected() throws Exception {
		assertRejected("magicfirework nope");
		assertRejected("magicfirework list extra");
	}
}
