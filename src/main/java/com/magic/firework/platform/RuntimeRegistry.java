package com.magic.firework.platform;

import com.magic.firework.MagicFirework;
import com.magic.firework.content.ContentLoader;
import com.magic.firework.content.ContentSnapshot;
import com.magic.firework.registry.DefinitionLoader;
import com.magic.firework.registry.Definitions;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.resources.ResourceManager;

/**
 * The one deliberately global reference: the runtime of the server that is currently running.
 *
 * <p>Minecraft exposes no "current server" accessor to a resource-reload listener, and a JVM
 * runs at most one server at a time, so this is the seam. Everything else that used to be
 * static now hangs off {@link FireworkRuntime}.
 *
 * <p>Reloads may run before {@code SERVER_STARTING} on some launch paths, so they land in a
 * pending snapshot that the first runtime adopts.
 */
public final class RuntimeRegistry {
	private static volatile FireworkRuntime current;
	private static volatile Definitions pendingDefinitions = Definitions.empty();
	private static volatile ContentSnapshot pendingContent = ContentSnapshot.empty();

	private RuntimeRegistry() {
	}

	/** @return the running server's runtime, or {@code null} on a dedicated client */
	public static FireworkRuntime current() {
		return current;
	}

	public static FireworkRuntime require() {
		FireworkRuntime runtime = current;
		if (runtime == null) {
			throw new IllegalStateException("no firework runtime: the server is not running");
		}
		return runtime;
	}

	public static void start(MinecraftServer server) {
		current = new FireworkRuntime(server, pendingDefinitions, pendingContent);
		MagicFirework.LOGGER.info("firework runtime started");
	}

	public static void stop(MinecraftServer server) {
		FireworkRuntime runtime = current;
		if (runtime != null && runtime.server() == server) {
			runtime.close();
			current = null;
		}
	}

	public static void reloadDefinitions(ResourceManager manager) {
		Definitions loaded = DefinitionLoader.load(manager);
		FireworkRuntime runtime = current;
		if (runtime != null) {
			runtime.setDefinitions(loaded);
		} else {
			pendingDefinitions = loaded;
		}
	}

	public static void reloadContent(ResourceManager manager) {
		ContentSnapshot loaded = ContentLoader.load(manager);
		FireworkRuntime runtime = current;
		if (runtime != null) {
			runtime.setContent(loaded);
		} else {
			pendingContent = loaded;
		}
	}
}
