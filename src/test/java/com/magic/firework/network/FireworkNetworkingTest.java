package com.magic.firework.network;

import static org.junit.jupiter.api.Assertions.assertNull;

import io.netty.buffer.Unpooled;

import org.junit.jupiter.api.Test;

import net.minecraft.network.FriendlyByteBuf;

class FireworkNetworkingTest {
	@Test
	void profileFromAnotherProtocolVersionIsDropped() {
		FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
		buf.writeByte(FireworkNetworking.PROTOCOL_VERSION + 1);

		assertNull(FireworkNetworking.readProfile(buf));
	}

	@Test
	void cloudFromAnotherProtocolVersionIsDropped() {
		FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
		buf.writeByte(FireworkNetworking.PROTOCOL_VERSION + 1);

		assertNull(FireworkNetworking.readCloud(buf));
	}

	@Test
	void emptyPacketIsDroppedInsteadOfThrowing() {
		assertNull(FireworkNetworking.readProfile(new FriendlyByteBuf(Unpooled.buffer())));
		assertNull(FireworkNetworking.readCloud(new FriendlyByteBuf(Unpooled.buffer())));
	}
}
