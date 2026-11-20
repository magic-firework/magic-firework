package com.magic.firework.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.Map;

import com.magic.firework.attribute.AttributeBag;
import com.magic.firework.attribute.Attributes;
import com.magic.firework.config.FireworkLimits;
import com.magic.firework.content.ContentSnapshot;
import com.magic.firework.content.FireworkProfile;
import com.magic.firework.platform.FireworkRuntime;
import com.magic.firework.registry.Definitions;
import com.magic.firework.rocket.RocketSpec;

import org.junit.jupiter.api.Test;

import net.minecraft.SharedConstants;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;

/** Launch validation without a server: profile lookup, overlay resolution and the depth bound. */
class MagicFireworkLaunchTest {
	static {
		// Entity and ItemStack initialize vanilla registries.
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
	}

	private static final ResourceLocation ID = new ResourceLocation("magic-firework", "ring12");

	private static FireworkRuntime runtime(AttributeBag defaults) {
		FireworkProfile profile = new FireworkProfile(ID, null, defaults);
		return new FireworkRuntime(null, new Definitions(Map.of(ID, profile)), ContentSnapshot.empty());
	}

	@Test
	void aKnownProfileResolvesWithTheDepthAndDefaults() {
		AttributeBag defaults = Attributes.SPEED.set(AttributeBag.EMPTY, 0.5F);
		FireworkRuntime runtime = runtime(defaults);

		MagicFireworkEntity.Launch launch = MagicFireworkEntity.resolveLaunch(runtime, RocketSpec.of(ID), 0);

		assertNotNull(launch);
		assertEquals(ID, launch.profile().id());
		assertEquals(0, launch.depth());
		assertEquals(0.5F, Attributes.SPEED.get(launch.base()), 0.0F);
	}

	@Test
	void aPerInstanceOverlayWinsOverTheProfileDefault() {
		FireworkRuntime runtime = runtime(Attributes.SPEED.set(AttributeBag.EMPTY, 0.5F));
		RocketSpec spec = RocketSpec.of(ID, Attributes.SPEED.set(AttributeBag.EMPTY, 1.25F));

		MagicFireworkEntity.Launch launch = MagicFireworkEntity.resolveLaunch(runtime, spec, 0);

		assertEquals(1.25F, Attributes.SPEED.get(launch.base()), 0.0F);
	}

	@Test
	void anUnknownProfileIsRefused() {
		FireworkRuntime runtime = runtime(AttributeBag.EMPTY);

		assertNull(MagicFireworkEntity.resolveLaunch(runtime,
				RocketSpec.of(new ResourceLocation("test", "missing")), 0));
	}

	@Test
	void theDepthBoundIsInclusiveAtTheLimitAndRefusesOneDeeper() {
		FireworkRuntime runtime = runtime(AttributeBag.EMPTY);

		assertNotNull(MagicFireworkEntity.resolveLaunch(runtime, RocketSpec.of(ID), FireworkLimits.maxDepth));
		assertNull(MagicFireworkEntity.resolveLaunch(runtime, RocketSpec.of(ID), FireworkLimits.maxDepth + 1));
		assertNull(MagicFireworkEntity.resolveLaunch(runtime, RocketSpec.of(ID), -1));
	}

	@Test
	void aMissingRuntimeOrSpecIsRefusedInsteadOfThrowing() {
		FireworkRuntime runtime = runtime(AttributeBag.EMPTY);

		assertNull(MagicFireworkEntity.resolveLaunch(null, RocketSpec.of(ID), 0));
		assertNull(MagicFireworkEntity.resolveLaunch(runtime, null, 0));
	}
}
