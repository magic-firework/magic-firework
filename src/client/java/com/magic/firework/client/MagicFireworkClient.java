package com.magic.firework.client;

import com.magic.firework.client.particle.FireworkSparkParticle;
import com.magic.firework.client.render.MagicFireworkEntityRenderer;
import com.magic.firework.client.screen.FireworkEditorScreen;
import com.magic.firework.network.FireworkNetworking;
import com.magic.firework.registry.ModParticles;
import com.magic.firework.registry.ModRegistries;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.particle.v1.ParticleFactoryRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;

import net.minecraft.client.gui.screens.MenuScreens;

public class MagicFireworkClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		EntityRendererRegistry.register(ModRegistries.MAGIC_FIREWORK_ENTITY, MagicFireworkEntityRenderer::new);
		ParticleFactoryRegistry.getInstance().register(ModParticles.FIREWORK_SPARK,
				sprites -> new FireworkSparkParticle.Provider(sprites));

		MenuScreens.register(ModRegistries.FIREWORK_EDITOR_MENU, FireworkEditorScreen::new);

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
						client.execute(() -> CloudPlayer.accept(client, packet.origin(), packet.stream(),
							packet.flicker()));
					}
				});

		ClientTickEvents.END_CLIENT_TICK.register(ClientProfiles::tick);
		ClientTickEvents.END_CLIENT_TICK.register(CloudPlayer::tick);
	}
}
