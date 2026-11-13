package com.magic.firework.client;

import com.magic.firework.client.render.MagicFireworkEntityRenderer;
import com.magic.firework.client.screen.FireworkEditorScreen;
import com.magic.firework.network.FireworkNetworking;
import com.magic.firework.registry.ModRegistries;

import com.mojang.blaze3d.platform.InputConstants;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.screens.MenuScreens;

import org.lwjgl.glfw.GLFW;

public class MagicFireworkClient implements ClientModInitializer {
	public static final KeyMapping EDITOR_KEY = KeyBindingHelper.registerKeyBinding(
			new KeyMapping("key.magic-firework.open_editor", InputConstants.Type.KEYSYM,
					GLFW.GLFW_KEY_K, "key.categories.magic-firework"));

	@Override
	public void onInitializeClient() {
		EntityRendererRegistry.register(ModRegistries.MAGIC_FIREWORK_ENTITY, MagicFireworkEntityRenderer::new);

		MenuScreens.register(ModRegistries.FIREWORK_EDITOR_MENU, FireworkEditorScreen::new);

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (EDITOR_KEY.consumeClick()) {
				ClientPlayNetworking.send(FireworkNetworking.OPEN_EDITOR_CHANNEL, PacketByteBufs.empty());
			}
		});

		ClientPlayNetworking.registerGlobalReceiver(FireworkNetworking.PROFILE_CHANNEL,
				(client, handler, buf, responseSender) -> {
					FireworkNetworking.Profile profile = FireworkNetworking.readProfile(buf);
					if (profile != null) {
						client.execute(() -> ClientProfiles.accept(client, profile));
					}
				});

		ClientPlayNetworking.registerGlobalReceiver(FireworkNetworking.CLOUD_CHANNEL,
				(client, handler, buf, responseSender) -> {
					FireworkNetworking.CloudPacket packet = FireworkNetworking.readCloud(buf);
					if (packet != null) {
						client.execute(() -> CloudPlayer.accept(client, packet.origin(), packet.stream()));
					}
				});

		ClientTickEvents.END_CLIENT_TICK.register(ClientProfiles::tick);
		ClientTickEvents.END_CLIENT_TICK.register(CloudPlayer::tick);
	}
}
