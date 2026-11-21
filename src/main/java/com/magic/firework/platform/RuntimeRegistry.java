package com.magic.firework.platform;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.magic.firework.MagicFirework;
import com.magic.firework.content.ContentLoader;
import com.magic.firework.content.ContentSnapshot;
import com.magic.firework.registry.DefinitionLoader;
import com.magic.firework.registry.Definitions;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.resources.ResourceManager;

/**
 * The one deliberately global reference: the runtime of the server that is currently running. Minecraft
 * exposes no "current server" accessor to a resource-reload listener and a JVM runs at most one server,
 * so this is the seam; everything else that used to be static hangs off {@link FireworkRuntime}.
 * Reloads may run before {@code SERVER_STARTING}, so they land in a snapshot the first runtime adopts.
 */
public final class RuntimeRegistry {
	private static final Map<MinecraftServer, FireworkRuntime> RUNNING = new ConcurrentHashMap<>();
	private static volatile FireworkRuntime current;
	private static volatile Definitions pendingDefinitions = Definitions.empty();
	private static volatile ContentSnapshot pendingContent = ContentSnapshot.empty();

	private RuntimeRegistry() {
	}

	/** @return the running server's runtime, or {@code null} on a dedicated client */
	public static FireworkRuntime current() {
		return current;
	}

	/** Server-scoped lookup for call sites that already have the server, without ambient state. */
	public static FireworkRuntime of(MinecraftServer server) {
		return server == null ? null : RUNNING.get(server);
	}

	public static void start(MinecraftServer server) {
		FireworkRuntime runtime = new FireworkRuntime(server, pendingDefinitions, pendingContent);
		RUNNING.put(server, runtime);
		current = runtime;
		MagicFirework.LOGGER.info("firework runtime started");
	}

	public static void stop(MinecraftServer server) {
		FireworkRuntime runtime = current;
		if (runtime != null && runtime.server() == server) {
			runtime.close();
			RUNNING.remove(server);
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
