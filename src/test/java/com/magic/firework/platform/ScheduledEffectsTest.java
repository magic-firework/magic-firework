package com.magic.firework.platform;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import com.magic.firework.config.FireworkLimits;
import com.magic.firework.core.eval.Plan;
import com.magic.firework.core.show.EventSpec;
import com.magic.firework.effect.LightSources;

import org.junit.jupiter.api.Test;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

class ScheduledEffectsTest {
	private static final ResourceLocation OVERWORLD = new ResourceLocation("test", "overworld");
	private static final ResourceLocation NETHER = new ResourceLocation("test", "nether");

	private static Plan.ScheduledEvent event(float time) {
		return new Plan.ScheduledEvent(EventSpec.Kind.SOUND, time, com.magic.firework.core.Vec3.ZERO, null, 1.0, 1.0);
	}

	@Test
	void thePendingQueueHasAHardCap() {
		ScheduledEffects effects = new ScheduledEffects(new LightSources());

		for (int i = 0; i < FireworkLimits.maxPendingEffects + 5; i++) {
			effects.schedule(OVERWORLD, Vec3.ZERO, List.of(event(0.0F)), 0L);
		}

		assertEquals(FireworkLimits.maxPendingEffects, effects.size());
		assertEquals(5, effects.dropped());
	}

	@Test
	void unloadingADimensionDropsOnlyItsEntries() {
		ScheduledEffects effects = new ScheduledEffects(new LightSources());
		effects.schedule(OVERWORLD, Vec3.ZERO, List.of(event(0.0F)), 0L);
		effects.schedule(NETHER, Vec3.ZERO, List.of(event(0.0F)), 0L);

		effects.onDimensionUnload(OVERWORLD);
		assertEquals(1, effects.size());

		effects.onDimensionUnload(NETHER);
		assertEquals(0, effects.size());
	}
}
