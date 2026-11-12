package com.magic.firework.client;

import com.magic.firework.client.render.MagicFireworkEntityRenderer;
import com.magic.firework.network.FireworkNetworking;
import com.magic.firework.registry.ModRegistries;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;

public class MagicFireworkClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		EntityRendererRegistry.register(ModRegistries.MAGIC_FIREWORK_ENTITY, MagicFireworkEntityRenderer::new);

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
