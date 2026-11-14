package com.magic.firework.network;

import com.magic.firework.MagicFirework;
import com.magic.firework.core.eval.FrameStream;
import com.magic.firework.attribute.AttributeBag;
import com.magic.firework.attribute.Attributes;
import com.magic.firework.blockentity.FireworkCraftingTableBlockEntity;
import com.magic.firework.entity.MagicFireworkEntity;
import com.magic.firework.platform.CloudProtocol;
import com.magic.firework.platform.FireworkRuntime;
import com.magic.firework.platform.RuntimeRegistry;
import com.magic.firework.screen.FireworkEditorMenu;

import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.phys.Vec3;

/**
 * Server -> client profile and cloud sync.
 *
 * <p>1.20.1 has no {@code CustomPacketPayload}, so this is a named channel carrying a
 * {@link FriendlyByteBuf}. Only what the client cannot derive travels: the seed, the age
 * and the resolved attribute bag (the trail is rendered from it). The program itself
 * never leaves the server, so a data pack mismatch can only change particles, never the
 * simulation.
 *
 * <p>Every packet starts with {@link #PROTOCOL_VERSION}. A client with a different version
 * drops the packet instead of misreading it.
 */
public final class FireworkNetworking {
	public static final int PROTOCOL_VERSION = 1;
	public static final ResourceLocation PROFILE_CHANNEL = MagicFirework.id("profile");
	/** Baked stroke cloud: origin + points with per-point time and colour. One packet per burst. */
	public static final ResourceLocation CLOUD_CHANNEL = MagicFirework.id("cloud");
	/** Client -> server: move one step along the speed / flight-time scale. */
	public static final ResourceLocation ADJUST_FLIGHT_CHANNEL = MagicFirework.id("adjust_flight");
	/** Client -> server: turn one of the direction dials. */
	public static final ResourceLocation ADJUST_DIRECTION_CHANNEL = MagicFirework.id("adjust_direction");

	private FireworkNetworking() {
	}

	public static void init() {
		// The flight page is part of the editor screen, so adjusting does not reopen a menu —
		// the updated firework simply syncs back through the already-open container. The
		// server validates every step: it re-reads the firework and consumes/refunds the
		// material itself, so a forged packet can only move within the allowed range.
		ServerPlayNetworking.registerGlobalReceiver(ADJUST_FLIGHT_CHANNEL,
				(server, player, handler, buf, responseSender) -> {
					if (buf.readableBytes() < 2) {
						return;
					}
					int which = buf.readByte();
					int delta = buf.readByte();
					server.execute(() -> {
						if (player.containerMenu instanceof FireworkEditorMenu menu
								&& menu.center() instanceof FireworkCraftingTableBlockEntity table) {
							FireworkRuntime runtime = RuntimeRegistry.current();
							if (runtime != null) {
								table.adjustFlight(runtime.definitions(), player, which, delta);
							}
						}
					});
				});

		ServerPlayNetworking.registerGlobalReceiver(ADJUST_DIRECTION_CHANNEL,
				(server, player, handler, buf, responseSender) -> {
					if (buf.readableBytes() < 2) {
						return;
					}
					int which = buf.readByte();
					int delta = buf.readByte();
					server.execute(() -> {
						if (player.containerMenu instanceof FireworkEditorMenu menu
								&& menu.center() instanceof FireworkCraftingTableBlockEntity table) {
							FireworkRuntime runtime = RuntimeRegistry.current();
							if (runtime != null) {
								table.adjustDirection(runtime.definitions(), which, delta);
							}
						}
					});
				});
	}

	public static void sendProfile(ServerPlayer player, MagicFireworkEntity entity) {
		if (!entity.profileReady()) {
			return;
		}
		if (!ServerPlayNetworking.canSend(player, PROFILE_CHANNEL)) {
			return;
		}
		FriendlyByteBuf buf = PacketByteBufs.create();
		buf.writeByte(PROTOCOL_VERSION);
		buf.writeVarInt(entity.getId());
		buf.writeLong(entity.seed());
		buf.writeVarInt(entity.programAge());
		buf.writeNbt(entity.bag().without(Attributes.SHOW).save());
		ServerPlayNetworking.send(player, PROFILE_CHANNEL, buf);
	}

	/** @return the profile, or {@code null} when the packet came from another protocol version */
	public static Profile readProfile(FriendlyByteBuf buf) {
		if (buf.readableBytes() < 1 || buf.readByte() != PROTOCOL_VERSION) {
			MagicFirework.LOGGER.debug("dropped a profile packet with a different protocol version");
			return null;
		}
		int entityId = buf.readVarInt();
		long seed = buf.readLong();
		int age = buf.readVarInt();
		AttributeBag bag = AttributeBag.load(buf.readNbt());
		return new Profile(entityId, seed, age, bag);
	}

	public record Profile(int entityId, long seed, int age, AttributeBag bag) {
	}

	public static void sendCloud(ServerPlayer player, Vec3 origin, FrameStream stream) {
		FriendlyByteBuf buf = PacketByteBufs.create();
		buf.writeByte(PROTOCOL_VERSION);
		buf.writeDouble(origin.x);
		buf.writeDouble(origin.y);
		buf.writeDouble(origin.z);
		CloudProtocol.write(buf, stream);
		ServerPlayNetworking.send(player, CLOUD_CHANNEL, buf);
	}

	/** @return the cloud, or {@code null} when the packet came from another protocol version */
	public static CloudPacket readCloud(FriendlyByteBuf buf) {
		if (buf.readableBytes() < 1 || buf.readByte() != PROTOCOL_VERSION) {
			MagicFirework.LOGGER.debug("dropped a cloud packet with a different protocol version");
			return null;
		}
		Vec3 origin = new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble());
		return new CloudPacket(origin, CloudProtocol.read(buf));
	}

	/** A baked burst: point offsets relative to {@code origin}. */
	public record CloudPacket(Vec3 origin, FrameStream stream) {
	}
}
