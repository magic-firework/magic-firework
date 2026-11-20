package com.magic.firework.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import io.netty.buffer.Unpooled;

import org.junit.jupiter.api.Test;

import com.magic.firework.core.eval.FrameStream;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.Vec3;

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
	void cloudRoundTripsOriginPointsAndFlicker() {
		FrameStream stream = FrameStream.of(List.of(
				new FrameStream.Point(new com.magic.firework.core.Vec3(1.0, 2.0, 3.0), 4.0F, 0xFFFF0000, 1.0F)));
		FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());

		FireworkNetworking.writeCloud(buf, new Vec3(10.0, 64.0, -10.0), stream, true);
		FireworkNetworking.CloudPacket packet = FireworkNetworking.readCloud(buf);

		assertEquals(new Vec3(10.0, 64.0, -10.0), packet.origin());
		assertEquals(1, packet.stream().size());
		assertTrue(packet.flicker(), "the flicker flag travels with its burst");
	}

	@Test
	void anEncodedCloudRoundTrips() {
		FrameStream stream = FrameStream.of(List.of(
				new FrameStream.Point(new com.magic.firework.core.Vec3(1.0, 2.0, 3.0), 4.0F, 0xFFFF0000, 1.0F)));
		byte[] payload = FireworkNetworking.encodeCloud(new Vec3(10.0, 64.0, -10.0), stream, true);

		FireworkNetworking.CloudPacket packet = FireworkNetworking.readCloud(
				new FriendlyByteBuf(Unpooled.wrappedBuffer(payload)));

		assertEquals(new Vec3(10.0, 64.0, -10.0), packet.origin());
		assertEquals(1, packet.stream().size());
		assertTrue(packet.flicker());
	}

	@Test
	void aCloudWithoutFlickerStaysOff() {
		FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());

		FireworkNetworking.writeCloud(buf, Vec3.ZERO, FrameStream.empty(), false);
		FireworkNetworking.CloudPacket packet = FireworkNetworking.readCloud(buf);

		assertEquals(false, packet.flicker());
	}

	@Test
	void emptyPacketIsDroppedInsteadOfThrowing() {
		assertNull(FireworkNetworking.readProfile(new FriendlyByteBuf(Unpooled.buffer())));
		assertNull(FireworkNetworking.readCloud(new FriendlyByteBuf(Unpooled.buffer())));
	}
}
