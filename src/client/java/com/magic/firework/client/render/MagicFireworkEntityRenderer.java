package com.magic.firework.client.render;

import com.magic.firework.entity.MagicFireworkEntity;
import com.magic.firework.registry.ModRegistries;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/** Draws the rocket as its item; keeps the entity model-free for now. */
public class MagicFireworkEntityRenderer extends EntityRenderer<MagicFireworkEntity> {
	private static final ResourceLocation TEXTURE = new ResourceLocation("minecraft", "textures/item/firework_rocket.png");

	private final ItemRenderer itemRenderer;
	private final ItemStack stack = new ItemStack(ModRegistries.MAGIC_FIREWORK_ITEM);

	public MagicFireworkEntityRenderer(EntityRendererProvider.Context context) {
		super(context);
		this.itemRenderer = context.getItemRenderer();
	}

	@Override
	public ResourceLocation getTextureLocation(MagicFireworkEntity entity) {
		return TEXTURE;
	}

	@Override
	public void render(MagicFireworkEntity entity, float entityYaw, float partialTick, PoseStack poseStack,
			MultiBufferSource buffer, int packedLight) {
		poseStack.pushPose();
		poseStack.translate(0.0, 0.25, 0.0);
		this.itemRenderer.renderStatic(stack, ItemDisplayContext.GROUND, packedLight, OverlayTexture.NO_OVERLAY,
				poseStack, buffer, entity.level(), entity.getId());
		poseStack.popPose();
		super.render(entity, entityYaw, partialTick, poseStack, buffer, packedLight);
	}
}
