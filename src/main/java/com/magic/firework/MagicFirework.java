package com.magic.firework;

import java.io.IOException;

import com.magic.firework.command.FireworkCommand;
import com.magic.firework.content.ContentLibrary;
import com.magic.firework.network.FireworkNetworking;
import com.magic.firework.platform.ContentReloadListener;
import com.magic.firework.platform.FireworkRuntime;
import com.magic.firework.platform.RuntimeRegistry;
import com.magic.firework.registry.DefinitionReloadListener;
import com.magic.firework.registry.ModRegistries;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.PackType;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.level.storage.LevelResource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MagicFirework implements ModInitializer {
	public static final String MOD_ID = "magic-firework";

	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		// content: profiles and shows from data packs, plus the writable library
		ModRegistries.init();
		ResourceManagerHelper.get(PackType.SERVER_DATA).registerReloadListener(new DefinitionReloadListener());
		ResourceManagerHelper.get(PackType.SERVER_DATA).registerReloadListener(new ContentReloadListener());
		FireworkNetworking.init();

		// runtime: one instance per running server, so no content or budget is global
		ServerLifecycleEvents.SERVER_STARTING.register(RuntimeRegistry::start);
		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			FireworkRuntime runtime = RuntimeRegistry.current();
			if (runtime != null) {
				runtime.bindLibrary(openLibrary(server));
			}
		});
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			FireworkRuntime runtime = RuntimeRegistry.current();
			if (runtime != null) {
				runtime.tick();
			}
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(RuntimeRegistry::stop);

		ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs.TOOLS_AND_UTILITIES)
				.register(entries -> entries.accept(ModRegistries.defaultStack()));
		FireworkCommand.init();

		LOGGER.info("Magic Firework initialised");
	}

	private static ContentLibrary openLibrary(MinecraftServer server) {
		ContentLibrary library = new ContentLibrary(
				server.getWorldPath(LevelResource.ROOT).resolve("magic_firework").resolve("library"));
		try {
			library.load();
		} catch (IOException exception) {
			LOGGER.warn("failed to load the content library", exception);
		}
		LOGGER.info("content library: {} entries", library.size());
		return library;
	}

	public static ResourceLocation id(String path) {
		return new ResourceLocation(MOD_ID, path);
	}
}
