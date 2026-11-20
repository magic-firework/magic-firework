package com.magic.firework.platform;

import java.util.ArrayList;
import java.util.List;

import com.magic.firework.config.FireworkLimits;
import com.magic.firework.core.ContentLimits;
import com.magic.firework.core.Vec3;
import com.magic.firework.core.eval.FrameStream;

import io.netty.handler.codec.DecoderException;
import net.minecraft.network.FriendlyByteBuf;

/**
 * Wire format for a baked show: points plus the handful of flashes. Positions are quantized to 1/512
 * block (range ±64), far finer than a particle can show and half the packet of raw doubles; velocities
 * use 1/4096 block/tick, enough for the 0.25-0.5 a vanilla spark starts with. The show id is not sent:
 * the client only replays the baked stream, and the id bought nothing but bytes.
 */
public final class CloudProtocol {
	private static final float QUANT = 512.0F;
	private static final float VELOCITY_QUANT = 4096.0F;
	private static final int FLAG_TRAIL = 1;
	private static final int FLAG_FLICKER = 2;
	/** Hard cap on flashes: at most one per stamp, and a show has at most MAX_STAMPS stamps. */
	private static final int MAX_FLASHES = ContentLimits.MAX_STAMPS;

	private CloudProtocol() {
	}

	public static void write(FriendlyByteBuf buf, FrameStream stream) {
		buf.writeVarInt(stream.size());
		for (FrameStream.Point point : stream.points()) {
			buf.writeShort(quantize(point.offset().x, QUANT));
			buf.writeShort(quantize(point.offset().y, QUANT));
			buf.writeShort(quantize(point.offset().z, QUANT));
			buf.writeFloat(point.time());
			buf.writeInt(point.argb());
			buf.writeFloat(point.size());
			buf.writeShort(quantize(point.velocity().x, VELOCITY_QUANT));
			buf.writeShort(quantize(point.velocity().y, VELOCITY_QUANT));
			buf.writeShort(quantize(point.velocity().z, VELOCITY_QUANT));
			buf.writeShort(Math.min(Math.round(point.lifetime()), Short.MAX_VALUE));
			buf.writeInt(point.fadeArgb());
			buf.writeByte(flags(point));
		}
		buf.writeVarInt(stream.flashCount());
		for (FrameStream.Flash flash : stream.flashes()) {
			buf.writeShort(quantize(flash.offset().x, QUANT));
			buf.writeShort(quantize(flash.offset().y, QUANT));
			buf.writeShort(quantize(flash.offset().z, QUANT));
			buf.writeFloat(flash.time());
			buf.writeInt(flash.argb());
		}
	}

	public static FrameStream read(FriendlyByteBuf buf) {
		int count = readCount(buf, FireworkLimits.maxPointsPerShow, "point");
		List<FrameStream.Point> points = new ArrayList<>(count);
		for (int i = 0; i < count; i++) {
			double x = buf.readShort() / (double) QUANT;
			double y = buf.readShort() / (double) QUANT;
			double z = buf.readShort() / (double) QUANT;
			float time = buf.readFloat();
			int argb = buf.readInt();
			float size = buf.readFloat();
			Vec3 velocity = new Vec3(
					buf.readShort() / (double) VELOCITY_QUANT,
					buf.readShort() / (double) VELOCITY_QUANT,
					buf.readShort() / (double) VELOCITY_QUANT);
			float lifetime = buf.readUnsignedShort();
			int fadeArgb = buf.readInt();
			int flags = buf.readUnsignedByte();
			points.add(new FrameStream.Point(new Vec3(x, y, z), time, argb, size, velocity, lifetime,
					fadeArgb, (flags & FLAG_TRAIL) != 0, (flags & FLAG_FLICKER) != 0));
		}
		int flashCount = readCount(buf, MAX_FLASHES, "flash");
		List<FrameStream.Flash> flashes = new ArrayList<>(flashCount);
		for (int i = 0; i < flashCount; i++) {
			double x = buf.readShort() / (double) QUANT;
			double y = buf.readShort() / (double) QUANT;
			double z = buf.readShort() / (double) QUANT;
			float time = buf.readFloat();
			int argb = buf.readInt();
			flashes.add(new FrameStream.Flash(new Vec3(x, y, z), time, argb));
		}
		return FrameStream.of(points, flashes);
	}

	private static int readCount(FriendlyByteBuf buf, int max, String what) {
		int count = buf.readVarInt();
		if (count < 0 || count > max) {
			// Checked before the list is allocated: a hostile count must not become an OOM.
			throw new DecoderException(what + " count " + count + " is outside 0.." + max);
		}
		return count;
	}

	private static int flags(FrameStream.Point point) {
		int flags = 0;
		if (point.trail()) {
			flags |= FLAG_TRAIL;
		}
		if (point.flicker()) {
			flags |= FLAG_FLICKER;
		}
		return flags;
	}

	private static short quantize(double value, float quant) {
		long scaled = Math.round(value * quant);
		if (scaled > Short.MAX_VALUE) {
			scaled = Short.MAX_VALUE;
		} else if (scaled < Short.MIN_VALUE) {
			scaled = Short.MIN_VALUE;
		}
		return (short) scaled;
	}
}
