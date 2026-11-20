package com.magic.firework.platform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import io.netty.buffer.Unpooled;
import io.netty.handler.codec.DecoderException;

import org.junit.jupiter.api.Test;

import com.magic.firework.config.FireworkLimits;
import com.magic.firework.core.ContentLimits;
import com.magic.firework.core.Vec3;
import com.magic.firework.core.eval.FrameStream;

import net.minecraft.network.FriendlyByteBuf;

/**
 * The cloud packet is client-bound data from a server the client does not control: every
 * length prefix is checked before a list is allocated.
 */
class CloudProtocolReadLimitTest {
	private static FrameStream points(int count) {
		List<FrameStream.Point> points = new ArrayList<>(count);
		for (int i = 0; i < count; i++) {
			points.add(new FrameStream.Point(Vec3.ZERO, 0.0F, 0xFFFFFFFF, 1.0F));
		}
		return FrameStream.of(points);
	}

	private static FrameStream flashes(int count) {
		List<FrameStream.Flash> flashes = new ArrayList<>(count);
		for (int i = 0; i < count; i++) {
			flashes.add(new FrameStream.Flash(Vec3.ZERO, i, 0xFFFFFFFF));
		}
		return FrameStream.of(List.of(), flashes);
	}

	private static FriendlyByteBuf buffer() {
		return new FriendlyByteBuf(Unpooled.buffer());
	}

	@Test
	void aHostilePointCountIsRejectedBeforeAnyAllocation() {
		FriendlyByteBuf buf = buffer();
		buf.writeVarInt(Integer.MAX_VALUE);

		assertTimeoutPreemptively(Duration.ofMillis(200),
				() -> assertThrows(DecoderException.class, () -> CloudProtocol.read(buf)));
	}

	@Test
	void aNegativePointCountIsRejected() {
		FriendlyByteBuf buf = buffer();
		buf.writeVarInt(-1);

		assertThrows(DecoderException.class, () -> CloudProtocol.read(buf));
	}

	@Test
	void aHostileFlashCountIsRejected() {
		FriendlyByteBuf buf = buffer();
		buf.writeVarInt(0);
		buf.writeVarInt(Integer.MAX_VALUE);

		assertTimeoutPreemptively(Duration.ofMillis(200),
				() -> assertThrows(DecoderException.class, () -> CloudProtocol.read(buf)));
	}

	@Test
	void thePointCapStillRoundTrips() {
		FrameStream stream = points(FireworkLimits.maxPointsPerShow);
		FriendlyByteBuf buf = buffer();
		CloudProtocol.write(buf, stream);

		assertEquals(FireworkLimits.maxPointsPerShow, CloudProtocol.read(buf).size());
	}

	@Test
	void onePointOverTheCapIsRejected() {
		FriendlyByteBuf buf = buffer();
		CloudProtocol.write(buf, points(FireworkLimits.maxPointsPerShow + 1));

		assertThrows(DecoderException.class, () -> CloudProtocol.read(buf));
	}

	@Test
	void theFlashCapIsEnforced() {
		FriendlyByteBuf buf = buffer();
		CloudProtocol.write(buf, flashes(ContentLimits.MAX_STAMPS + 1));

		assertThrows(DecoderException.class, () -> CloudProtocol.read(buf));
	}
}
