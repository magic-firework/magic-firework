package com.magic.firework.platform;

import com.magic.firework.MagicFirework;

import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;

/** Delegates to the current runtime so the listener itself holds no content state. */
public final class ContentReloadListener implements SimpleSynchronousResourceReloadListener {
	@Override
	public ResourceLocation getFabricId() {
		return MagicFirework.id("content");
	}

	@Override
	public void onResourceManagerReload(ResourceManager manager) {
		RuntimeRegistry.reloadContent(manager);
	}
}
