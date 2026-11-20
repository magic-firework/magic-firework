package com.magic.firework.platform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.ArrayList;
import java.util.List;

import com.magic.firework.core.eval.FrameStream;

import org.junit.jupiter.api.Test;

import net.minecraft.world.phys.Vec3;

class ShowServiceBroadcastTest {
	@Test
	void oneEncodingIsSharedByEveryRecipient() {
		List<Integer> recipients = List.of(1, 2, 3, 4, 5, 6, 7, 8);
		List<byte[]> payloads = new ArrayList<>();

		ShowService.broadcast(new Vec3(0.0, 64.0, 0.0), FrameStream.empty(), true, recipients,
				(recipient, payload) -> payloads.add(payload));

		assertEquals(8, payloads.size());
		for (byte[] payload : payloads) {
			assertSame(payloads.get(0), payload, "the burst is encoded once and shared");
		}
	}
}
