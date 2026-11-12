package com.magic.firework.command;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.StringJoiner;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.magic.firework.MagicFirework;
import com.magic.firework.content.ContentLibrary;
import com.magic.firework.content.NbtTree;
import com.magic.firework.core.Id;
import com.magic.firework.attribute.Attributes;
import com.magic.firework.attribute.AttributeBag;
import com.magic.firework.content.DefinitionIds;
import com.magic.firework.content.FireworkProfile;
import com.magic.firework.entity.MagicFireworkEntity;
import com.magic.firework.item.FireworkDataHelper;
import com.magic.firework.platform.FireworkRuntime;
import com.magic.firework.platform.RuntimeRegistry;
import com.magic.firework.registry.ModRegistries;
import com.magic.firework.sim.FireworkSimulator;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/** {@code /magicfirework} — run the framework, and manage the content library, without crafting. */
public final class FireworkCommand {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	private FireworkCommand() {
	}

	public static void init() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(
				Commands.literal("magicfirework")
						.requires(source -> source.hasPermission(2))
						.then(Commands.literal("test")
								.executes(FireworkCommand::usage)
								.then(Commands.argument("profile", ResourceLocationArgument.id())
										.suggests(FireworkCommand::suggestProfiles)
										.executes(context -> test(context,
												ResourceLocationArgument.getId(context, "profile")))))
						.then(Commands.literal("give")
								.then(Commands.argument("profile", ResourceLocationArgument.id())
										.suggests(FireworkCommand::suggestProfiles)
										.executes(context -> give(context,
												ResourceLocationArgument.getId(context, "profile"), 1))
										.then(Commands.argument("count", IntegerArgumentType.integer(1, 64))
												.executes(context -> give(context,
														ResourceLocationArgument.getId(context, "profile"),
														IntegerArgumentType.getInteger(context, "count"))))))
						.then(Commands.literal("golden_peony")
								.executes(context -> give(context, DefinitionIds.GOLDEN_PEONY, 1))
								.then(Commands.argument("count", IntegerArgumentType.integer(1, 64))
										.executes(context -> give(context, DefinitionIds.GOLDEN_PEONY,
												IntegerArgumentType.getInteger(context, "count")))))
						.then(Commands.literal("list")
								.executes(FireworkCommand::list))
						.then(Commands.literal("sim")
								.executes(context -> sim(context, DefinitionIds.BASIC))
								.then(Commands.argument("profile", ResourceLocationArgument.id())
										.suggests(FireworkCommand::suggestProfiles)
										.executes(context -> sim(context,
												ResourceLocationArgument.getId(context, "profile")))
										.then(Commands.argument("ticks", IntegerArgumentType.integer(1, 600))
												.executes(context -> sim(context,
														ResourceLocationArgument.getId(context, "profile"),
														IntegerArgumentType.getInteger(context, "ticks"))))))
						.then(Commands.literal("stats")
								.executes(FireworkCommand::stats))
						.then(Commands.literal("library")
								.executes(FireworkCommand::library))
						.then(Commands.literal("save")
								.then(Commands.argument("name", StringArgumentType.word())
										.executes(context -> save(context,
												StringArgumentType.getString(context, "name")))))
						.then(Commands.literal("load")
								.then(Commands.argument("name", StringArgumentType.word())
										.executes(context -> load(context,
												StringArgumentType.getString(context, "name")))))
						.then(Commands.literal("delete")
								.then(Commands.argument("name", StringArgumentType.word())
										.executes(context -> delete(context,
												StringArgumentType.getString(context, "name")))))
						.then(Commands.literal("export")
								.then(Commands.argument("name", StringArgumentType.word())
										.executes(context -> export(context,
												StringArgumentType.getString(context, "name")))))));
	}

	/** One running server per JVM, so the registry lookup is unambiguous. */
	private static FireworkRuntime runtime(CommandSourceStack source) {
		FireworkRuntime runtime = RuntimeRegistry.current();
		if (runtime == null) {
			source.sendFailure(Component.literal("the firework runtime is not running"));
		}
		return runtime;
	}

	private static int test(com.mojang.brigadier.context.CommandContext<CommandSourceStack> context,
			ResourceLocation id) {
		MagicFirework.LOGGER.info("test command: input='{}' resolved={}", context.getInput(), id);
		ServerPlayer player = context.getSource().getPlayer();
		if (player == null) {
			context.getSource().sendFailure(Component.literal("run this as a player"));
			return 0;
		}
		FireworkRuntime runtime = runtime(context.getSource());
		if (runtime == null) {
			return 0;
		}
		FireworkProfile profile = runtime.definitions().get(id);
		if (profile == null) {
			context.getSource().sendFailure(Component.literal("unknown profile " + id));
			return 0;
		}
		ServerLevel level = (ServerLevel) player.level();
		Vec3 spawn = player.getEyePosition().add(player.getLookAngle().scale(0.5));
		MagicFireworkEntity rocket = MagicFireworkEntity.launch(level, spawn, player.getLookAngle(),
				profile, AttributeBag.EMPTY, player, null);
		context.getSource().sendSuccess(() -> Component.literal(rocket == null
				? "launch refused (budget or depth limit)"
				: "launched " + id), false);
		return rocket == null ? 0 : 1;
	}

	private static int usage(com.mojang.brigadier.context.CommandContext<CommandSourceStack> context) {
		context.getSource().sendFailure(Component.literal(
				"usage: /magicfirework test <profile>   e.g. /magicfirework test magic-firework:flower"
						+ "   |   /magicfirework golden_peony [count]"));
		return list(context);
	}

	private static int give(com.mojang.brigadier.context.CommandContext<CommandSourceStack> context,
			ResourceLocation id, int count) {
		ServerPlayer player = context.getSource().getPlayer();
		if (player == null) {
			context.getSource().sendFailure(Component.literal("run this as a player"));
			return 0;
		}
		FireworkRuntime runtime = runtime(context.getSource());
		if (runtime == null) {
			return 0;
		}
		if (runtime.definitions().get(id) == null) {
			context.getSource().sendFailure(Component.literal("unknown profile " + id));
			return 0;
		}
		ItemStack stack = FireworkDataHelper.create(ModRegistries.MAGIC_FIREWORK_ITEM, id, AttributeBag.EMPTY);
		int amount = Math.max(1, Math.min(count, stack.getMaxStackSize()));
		stack.setCount(amount);
		if (!player.getInventory().add(stack)) {
			player.drop(stack, false);
		}
		context.getSource().sendSuccess(() -> Component.literal("gave " + amount + " × " + id), false);
		return amount;
	}

	/** Stores the held item's inline document under a library name. */
	private static int save(com.mojang.brigadier.context.CommandContext<CommandSourceStack> context, String name) {
		ServerPlayer player = context.getSource().getPlayer();
		if (player == null) {
			context.getSource().sendFailure(Component.literal("run this as a player"));
			return 0;
		}
		FireworkRuntime runtime = runtime(context.getSource());
		if (runtime == null) {
			return 0;
		}
		Tag show = FireworkDataHelper.overlay(player.getMainHandItem()).raw().get(Attributes.SHOW);
		if (!(show instanceof CompoundTag document)) {
			context.getSource().sendFailure(
					Component.literal("hold an item whose show is an inline document"));
			return 0;
		}
		try {
			ContentLibrary.Entry entry = runtime.library().save(new Id(ContentLibrary.NAMESPACE, name), document,
					player.getUUID());
			runtime.invalidateLibrary();
			context.getSource().sendSuccess(() -> Component.literal("saved " + entry.id()), false);
			return 1;
		} catch (IOException | RuntimeException exception) {
			context.getSource().sendFailure(Component.literal(exception.getMessage()));
			return 0;
		}
	}

	/** Points the held item at a library entry. */
	private static int load(com.mojang.brigadier.context.CommandContext<CommandSourceStack> context, String name) {
		ServerPlayer player = context.getSource().getPlayer();
		if (player == null) {
			context.getSource().sendFailure(Component.literal("run this as a player"));
			return 0;
		}
		FireworkRuntime runtime = runtime(context.getSource());
		if (runtime == null) {
			return 0;
		}
		Id id = new Id(ContentLibrary.NAMESPACE, name);
		if (runtime.library().get(id).isEmpty()) {
			context.getSource().sendFailure(Component.literal("unknown library entry " + id));
			return 0;
		}
		FireworkDataHelper.setShow(player.getMainHandItem(), StringTag.valueOf(id.toString()));
		context.getSource().sendSuccess(() -> Component.literal("loaded " + id), false);
		return 1;
	}

	private static int delete(com.mojang.brigadier.context.CommandContext<CommandSourceStack> context, String name) {
		ServerPlayer player = context.getSource().getPlayer();
		if (player == null) {
			context.getSource().sendFailure(Component.literal("run this as a player"));
			return 0;
		}
		FireworkRuntime runtime = runtime(context.getSource());
		if (runtime == null) {
			return 0;
		}
		Id id = new Id(ContentLibrary.NAMESPACE, name);
		try {
			boolean removed = runtime.library().delete(id, player.getUUID(), true);
			if (!removed) {
				context.getSource().sendFailure(Component.literal("unknown library entry " + id));
				return 0;
			}
			runtime.invalidateLibrary();
			context.getSource().sendSuccess(() -> Component.literal("deleted " + id), false);
			return 1;
		} catch (IOException exception) {
			context.getSource().sendFailure(Component.literal(exception.getMessage()));
			return 0;
		}
	}

	/** Writes a library entry as JSON, ready to drop into a data pack. */
	private static int export(com.mojang.brigadier.context.CommandContext<CommandSourceStack> context, String name) {
		FireworkRuntime runtime = runtime(context.getSource());
		if (runtime == null) {
			return 0;
		}
		Id id = new Id(ContentLibrary.NAMESPACE, name);
		Optional<ContentLibrary.Entry> entry = runtime.library().get(id);
		if (entry.isEmpty()) {
			context.getSource().sendFailure(Component.literal("unknown library entry " + id));
			return 0;
		}
		Path directory = FabricLoader.getInstance().getConfigDir().resolve("magic-firework").resolve("export");
		try {
			Files.createDirectories(directory);
			Path file = directory.resolve(name + ".json");
			Files.writeString(file, GSON.toJson(NbtTree.toJson(entry.get().document())));
			context.getSource().sendSuccess(() -> Component.literal("exported " + file), false);
			return 1;
		} catch (IOException exception) {
			context.getSource().sendFailure(Component.literal(exception.getMessage()));
			return 0;
		}
	}

	private static int library(com.mojang.brigadier.context.CommandContext<CommandSourceStack> context) {
		FireworkRuntime runtime = runtime(context.getSource());
		if (runtime == null) {
			return 0;
		}
		StringJoiner joiner = new StringJoiner(", ");
		runtime.library().ids().forEach(id -> joiner.add(id.toString()));
		context.getSource().sendSuccess(() -> Component.literal("library: " + joiner), false);
		return 1;
	}

	private static int list(com.mojang.brigadier.context.CommandContext<CommandSourceStack> context) {
		FireworkRuntime runtime = runtime(context.getSource());
		if (runtime == null) {
			return 0;
		}
		StringJoiner joiner = new StringJoiner(", ");
		runtime.definitions().ids().forEach(id -> joiner.add(id.toString()));
		context.getSource().sendSuccess(() -> Component.literal("profiles: " + joiner), false);
		return 1;
	}

	private static int sim(com.mojang.brigadier.context.CommandContext<CommandSourceStack> context, ResourceLocation id) {
		return sim(context, id, 200);
	}

	/** Headless run: the same interpreter and flight step the entity uses. */
	private static int sim(com.mojang.brigadier.context.CommandContext<CommandSourceStack> context, ResourceLocation id,
			int ticks) {
		FireworkRuntime runtime = runtime(context.getSource());
		if (runtime == null) {
			return 0;
		}
		FireworkProfile profile = runtime.definitions().get(id);
		if (profile == null) {
			context.getSource().sendFailure(Component.literal("unknown profile " + id));
			return 0;
		}
		FireworkSimulator.Result result = FireworkSimulator.run(profile.program(), runtime.definitions()::program,
				profile.defaults(), Vec3.ZERO, new Vec3(0.0, 1.0, 0.0), ticks, 0.0);
		StringBuilder trace = new StringBuilder();
		trace.append(id).append(": finished=").append(result.finished())
				.append(" ticks=").append(result.liveTicks())
				.append(" bursts=").append(result.bursts())
				.append(" children=").append(result.children())
				.append(" maxY=").append(String.format("%.2f", result.maxHeight()));
		FireworkSimulator.Sample last = null;
		for (FireworkSimulator.Sample sample : result.samples()) {
			if (sample.age() % 10 == 0) {
				trace.append("\n  t=").append(sample.age())
						.append(" y=").append(String.format("%.2f", sample.position().y))
						.append(" vy=").append(String.format("%.3f", sample.velocity().y));
			}
			last = sample;
		}
		if (last != null) {
			trace.append("\n  last y=").append(String.format("%.2f", last.position().y));
		}
		context.getSource().sendSuccess(() -> Component.literal(trace.toString()), false);
		return result.finished() ? 1 : 0;
	}

	private static int stats(com.mojang.brigadier.context.CommandContext<CommandSourceStack> context) {
		FireworkRuntime runtime = runtime(context.getSource());
		if (runtime == null) {
			return 0;
		}
		context.getSource().sendSuccess(() -> Component.literal(
				"profiles=" + runtime.definitions().size()
						+ " spawnsThisTick=" + runtime.ledger().spawnsThisTick()
						+ " pointsThisTick=" + runtime.ledger().pointsThisTick()), false);
		return 1;
	}

	private static java.util.concurrent.CompletableFuture<com.mojang.brigadier.suggestion.Suggestions> suggestProfiles(
			com.mojang.brigadier.context.CommandContext<CommandSourceStack> context,
			com.mojang.brigadier.suggestion.SuggestionsBuilder builder) {
		FireworkRuntime runtime = runtime(context.getSource());
		if (runtime != null) {
			runtime.definitions().ids().forEach(id -> builder.suggest(id.toString()));
		}
		return builder.buildFuture();
	}
}
