package com.magic.firework.gametest;

import java.util.List;

import com.magic.firework.core.eval.Plan;
import com.magic.firework.core.show.EventSpec;
import com.magic.firework.effect.LightLedger;
import com.magic.firework.effect.LightSources;
import com.magic.firework.platform.ScheduledEffects;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * Runtime smoke in a real server world: the light block is actually set and removed, and a dimension
 * unload drops both the live block and the queued effects. The restart case serializes the ledger,
 * deserializes it into a fresh instance and recovers against the block the "crash" left behind; the
 * on-disk bytes are the same ones a real kill would leave, which is the only part a test cannot do.
 */
public final class MagicFireworkGameTest implements FabricGameTest {
	private static final BlockPos CENTER = new BlockPos(1, 1, 1);

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void clearingTheLightsRemovesTheBlock(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos pos = helper.absolutePos(CENTER);
		helper.setBlock(CENTER, Blocks.AIR);
		LightSources lights = new LightSources();

		lights.light(level, pos, 100);

		helper.assertBlockPresent(Blocks.LIGHT, CENTER);
		lights.clear();
		helper.assertBlockNotPresent(Blocks.LIGHT, CENTER);
		helper.succeed();
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void unloadingADimensionDropsItsLightAndPendingEffects(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos pos = helper.absolutePos(CENTER);
		helper.setBlock(CENTER, Blocks.AIR);
		LightSources lights = new LightSources();
		ScheduledEffects effects = new ScheduledEffects(lights);
		Vec3 origin = new Vec3(pos.getX(), pos.getY(), pos.getZ());
		effects.schedule(level, origin, List.of(new Plan.ScheduledEvent(EventSpec.Kind.LIGHT, 0.0F,
				com.magic.firework.core.Vec3.ZERO, null, 20.0, 0.0)), level.getGameTime());

		effects.tick(level.getServer());

		helper.assertBlockPresent(Blocks.LIGHT, CENTER);
		effects.onDimensionUnload(level.dimension().location());
		lights.onDimensionUnload(level.dimension());
		helper.assertBlockNotPresent(Blocks.LIGHT, CENTER);
		helper.assertTrue(effects.size() == 0, "pending effects stay after the dimension unload");
		helper.assertTrue(lights.placedCount(level.dimension()) == 0, "light records stay after the unload");
		helper.succeed();
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void aRestartRecoversTheLightsTheCrashLeftBehind(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos pos = helper.absolutePos(CENTER);
		helper.setBlock(CENTER, Blocks.AIR);
		LightSources placing = new LightSources();
		placing.bind(level.getServer());
		placing.light(level, pos, 100);
		helper.assertBlockPresent(Blocks.LIGHT, CENTER);

		// What survives a crash: the serialized ledger, not the in-memory placement map.
		CompoundTag persisted = LightLedger.get(level.getServer()).save(new CompoundTag());
		LightLedger reloaded = LightLedger.fromTag(persisted);
		helper.assertTrue(!reloaded.positions(level.dimension()).isEmpty(), "the ledger did not persist the light");

		LightSources restarted = new LightSources();
		int removed = restarted.recover(level, reloaded);

		helper.assertTrue(removed == 1, "the restart should remove exactly one leftover light");
		helper.assertBlockNotPresent(Blocks.LIGHT, CENTER);
		helper.assertTrue(reloaded.isEmpty(), "recovery must consume the ledger");
		LightLedger.get(level.getServer()).clear();
		helper.succeed();
	}
}
