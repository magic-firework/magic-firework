package com.magic.firework.platform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import io.netty.buffer.Unpooled;

import org.junit.jupiter.api.Test;

import com.magic.firework.core.Vec3;
import com.magic.firework.core.eval.FrameStream;

import net.minecraft.network.FriendlyByteBuf;

class CloudProtocolTest {
	@Test
	void roundTripsPoints() {
		FrameStream stream = FrameStream.of(List.of(
				new FrameStream.Point(new Vec3(0.0, 1.0, -0.5), 0.0F, 0xFFFF0000, 1.5F),
				new FrameStream.Point(new Vec3(2.25, -3.0, 0.125), 4.0F, 0xFF00FF00, 0.5F)));

		FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
		CloudProtocol.write(buf, stream);
		FrameStream read = CloudProtocol.read(buf);

		assertEquals(2, read.size());

		FrameStream.Point first = read.point(0);
		// Quantized to 1/512 block, so compare with that tolerance.
		assertEquals(0.0, first.offset().x, 1.0 / 512.0);
		assertEquals(1.0, first.offset().y, 1.0 / 512.0);
		assertEquals(-0.5, first.offset().z, 1.0 / 512.0);
		assertEquals(0.0F, first.time(), 0.0F);
		assertEquals(0xFFFF0000, first.argb());
		assertEquals(1.5F, first.size(), 0.0F);

		FrameStream.Point second = read.point(1);
		assertEquals(2.25, second.offset().x, 1.0 / 512.0);
		assertEquals(4.0F, second.time(), 0.0F);
	}

	@Test
	void roundTripsSparksAndFlashes() {
		FrameStream stream = FrameStream.of(
				List.of(new FrameStream.Point(new Vec3(0.0, 0.0, 0.0), 0.0F, 0xFFFF0000, 1.0F,
						new Vec3(0.25, -0.5, 0.125), 53.0F, 0xFF00FF00, true, true)),
				List.of(new FrameStream.Flash(new Vec3(0.0, 1.0, 0.0), 2.0F, 0xFFFFAA00)));

		FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
		CloudProtocol.write(buf, stream);
		FrameStream read = CloudProtocol.read(buf);

		FrameStream.Point point = read.point(0);
		assertEquals(0.25, point.velocity().x, 1.0 / 4096.0);
		assertEquals(-0.5, point.velocity().y, 1.0 / 4096.0);
		assertEquals(0.125, point.velocity().z, 1.0 / 4096.0);
		assertEquals(53.0F, point.lifetime(), 0.0F);
		assertEquals(0xFF00FF00, point.fadeArgb());
		assertTrue(point.trail());
		assertTrue(point.flicker());

		assertEquals(1, read.flashCount());
		assertEquals(2.0F, read.flash(0).time(), 0.0F);
		assertEquals(0xFFFFAA00, read.flash(0).argb());
	}

	@Test
	void quantizationClampsInsteadOfWrapping() {
		FrameStream stream = FrameStream.of(List.of(
				new FrameStream.Point(new Vec3(1000.0, -1000.0, 0.0), 0.0F, 0xFFFFFFFF, 1.0F)));

		FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
		CloudProtocol.write(buf, stream);
		FrameStream read = CloudProtocol.read(buf);

		assertEquals(Short.MAX_VALUE / 512.0, read.point(0).offset().x, 1.0E-9);
		assertEquals(Short.MIN_VALUE / 512.0, read.point(0).offset().y, 1.0E-9);
	}
}
