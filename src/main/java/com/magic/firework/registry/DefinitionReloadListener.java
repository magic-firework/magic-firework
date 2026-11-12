package com.magic.firework.registry;

import com.magic.firework.MagicFirework;
import com.magic.firework.platform.RuntimeRegistry;

import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;

/** Delegates to the current runtime so the listener itself holds no definition state. */
public final class DefinitionReloadListener implements SimpleSynchronousResourceReloadListener {
	@Override
	public ResourceLocation getFabricId() {
		return MagicFirework.id("firework_definitions");
	}

	@Override
	public void onResourceManagerReload(ResourceManager manager) {
		RuntimeRegistry.reloadDefinitions(manager);
	}
}
