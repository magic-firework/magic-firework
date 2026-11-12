package com.magic.firework.platform;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
