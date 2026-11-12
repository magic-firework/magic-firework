package com.magic.firework.platform;

import java.util.ArrayList;
import java.util.List;

import com.magic.firework.core.Vec3;
import com.magic.firework.core.eval.FrameStream;

import net.minecraft.network.FriendlyByteBuf;

/**
 * Wire format for a baked show: just the points.
 *
 * <p>Positions are quantized to 1/512 block (range ±64 blocks), which is far finer than a
 * particle can show and halves the packet compared to raw doubles. The show id is not sent:
 * the client only replays the baked stream, and the id bought nothing but bytes.
 */
public final class CloudProtocol {
	private static final float QUANT = 512.0F;

	private CloudProtocol() {
	}

	public static void write(FriendlyByteBuf buf, FrameStream stream) {
		buf.writeVarInt(stream.size());
		for (FrameStream.Point point : stream.points()) {
			buf.writeShort(quantize(point.offset().x));
			buf.writeShort(quantize(point.offset().y));
			buf.writeShort(quantize(point.offset().z));
			buf.writeFloat(point.time());
			buf.writeInt(point.argb());
			buf.writeFloat(point.size());
		}
	}

	public static FrameStream read(FriendlyByteBuf buf) {
		int count = buf.readVarInt();
		List<FrameStream.Point> points = new ArrayList<>(Math.max(0, count));
		for (int i = 0; i < count; i++) {
			double x = buf.readShort() / (double) QUANT;
			double y = buf.readShort() / (double) QUANT;
			double z = buf.readShort() / (double) QUANT;
			float time = buf.readFloat();
			int argb = buf.readInt();
			float size = buf.readFloat();
			points.add(new FrameStream.Point(new Vec3(x, y, z), time, argb, size));
		}
		return FrameStream.of(points);
	}

	private static short quantize(double value) {
		long scaled = Math.round(value * QUANT);
		if (scaled > Short.MAX_VALUE) {
			scaled = Short.MAX_VALUE;
		} else if (scaled < Short.MIN_VALUE) {
			scaled = Short.MIN_VALUE;
		}
		return (short) scaled;
	}
}
