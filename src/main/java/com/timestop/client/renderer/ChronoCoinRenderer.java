package com.timestop.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.timestop.entity.ChronoCoinEntity;
import com.timestop.item.ModItems;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

public class ChronoCoinRenderer extends EntityRenderer<ChronoCoinEntity> {

    private final ItemRenderer itemRenderer;
    private final ItemStack coinStack;

    public ChronoCoinRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.itemRenderer = context.getItemRenderer();
        this.coinStack = new ItemStack(ModItems.CHRONO_COIN.get());
    }

    @Override
    public void render(ChronoCoinEntity entity, float entityYaw, float partialTicks, PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        poseStack.pushPose();
        poseStack.translate(0.0, 0.15, 0.0);

        float age = entity.tickCount + partialTicks;
        // High-speed tumble spin on yaw and pitch
        poseStack.mulPose(Axis.YP.rotationDegrees(age * 36.0F));
        poseStack.mulPose(Axis.XP.rotationDegrees(age * 22.0F));
        poseStack.scale(0.95F, 0.95F, 0.95F);

        // Render gleaming golden coin with maximum fullbright light so it shines brilliantly
        this.itemRenderer.renderStatic(this.coinStack, ItemDisplayContext.GROUND, 15728880, OverlayTexture.NO_OVERLAY, poseStack, buffer, entity.level(), entity.getId());

        poseStack.popPose();
        super.render(entity, entityYaw, partialTicks, poseStack, buffer, packedLight);
    }

    @Override
    public ResourceLocation getTextureLocation(ChronoCoinEntity entity) {
        return new ResourceLocation("minecraft", "textures/item/gold_nugget.png");
    }
}
