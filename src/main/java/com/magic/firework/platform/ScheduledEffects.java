package com.magic.firework.platform;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import com.magic.firework.core.eval.Plan;
import com.magic.firework.core.show.EventSpec;
import com.magic.firework.effect.LightSources;
import com.magic.firework.effect.Sounds;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

/**
 * Server-side scheduler for the non-visual part of a baked plan (sounds, temporary lights).
 *
 * <p>One instance per runtime: the pending list is dropped with the server, so a stopped or
 * switched world cannot leave its {@link ServerLevel} references behind.
 */
public final class ScheduledEffects {
	private final LightSources lights;
	private final List<Pending> pending = new ArrayList<>();

	public ScheduledEffects(LightSources lights) {
		this.lights = lights;
	}

	public void schedule(ServerLevel level, Vec3 origin, List<Plan.ScheduledEvent> events, long now) {
		if (events == null || events.isEmpty()) {
			return;
		}
		for (Plan.ScheduledEvent event : events) {
			pending.add(new Pending(level, now + (long) event.time(), event, origin));
		}
	}

	public void tick(MinecraftServer server) {
		if (pending.isEmpty()) {
			return;
		}
		for (Iterator<Pending> iterator = pending.iterator(); iterator.hasNext();) {
			Pending entry = iterator.next();
			if (entry.level().getGameTime() < entry.at()) {
				continue;
			}
			iterator.remove();
			entry.play(lights);
		}
	}

	public void clear() {
		pending.clear();
	}

	public int size() {
		return pending.size();
	}

	private record Pending(ServerLevel level, long at, Plan.ScheduledEvent event, Vec3 origin) {
		private void play(LightSources lights) {
			Vec3 position = origin.add(toMinecraft(event.at()));
			if (event.kind() == EventSpec.Kind.SOUND && event.target() != null) {
				ResourceLocation sound = ResourceLocation.tryParse(event.target().toString());
				if (sound != null) {
					Sounds.play(level, position, sound, (float) event.a(), (float) event.b());
				}
			} else if (event.kind() == EventSpec.Kind.LIGHT) {
				BlockPos pos = BlockPos.containing(position);
				lights.light(level, pos, (int) event.a());
			}
		}
	}

	/** Core maths and Minecraft maths are separate types on purpose; convert at the seam. */
	private static Vec3 toMinecraft(com.magic.firework.core.Vec3 value) {
		return new Vec3(value.x, value.y, value.z);
	}
}
