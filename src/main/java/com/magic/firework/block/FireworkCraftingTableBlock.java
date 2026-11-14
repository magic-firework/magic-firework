package com.magic.firework.block;

import com.magic.firework.blockentity.FireworkCraftingTableBlockEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * The firework crafting table. Right-clicking opens the firework editor bound to this block's
 * {@link FireworkCraftingTableBlockEntity}; breaking the block drops the firework left inside.
 */
public class FireworkCraftingTableBlock extends BaseEntityBlock {
	public FireworkCraftingTableBlock() {
		super(BlockBehaviour.Properties.of().strength(2.5F).sound(SoundType.WOOD));
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new FireworkCraftingTableBlockEntity(pos, state);
	}

	@Override
	public RenderShape getRenderShape(BlockState state) {
		// BaseEntityBlock defaults to INVISIBLE (block-entity-only rendering); this block is a
		// plain cube, so render its model like the vanilla crafting table.
		return RenderShape.MODEL;
	}

	@Override
	public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
			BlockHitResult hit) {
		if (!level.isClientSide && level.getBlockEntity(pos) instanceof FireworkCraftingTableBlockEntity table) {
			// Anything left in the old material slots is handed back once, then forgotten.
			table.returnLegacyMaterials(player);
			player.openMenu(table);
			return InteractionResult.CONSUME;
		}
		return InteractionResult.sidedSuccess(level.isClientSide);
	}

	@Override
	public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
		if (!state.is(newState.getBlock())) {
			if (level.getBlockEntity(pos) instanceof FireworkCraftingTableBlockEntity table
					&& level instanceof ServerLevel) {
				Containers.dropContents(level, pos, table);
			}
			super.onRemove(state, level, pos, newState, movedByPiston);
		}
	}
}
