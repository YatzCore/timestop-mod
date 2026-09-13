package com.timestop.fabric.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.timestop.client.renderer.KineticPalmRenderer;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemInHandRenderer.class)
public abstract class ItemInHandRendererMixin {
    @Inject(method = "renderArmWithItem", at = @At("HEAD"))
    private void timestop$onRenderArmWithItem(AbstractClientPlayer player, float partialTicks, float pitch, InteractionHand hand,
                                               float swingProgress, ItemStack stack, float equippedProgress,
                                               PoseStack poseStack, MultiBufferSource buffer, int combinedLight, CallbackInfo ci) {
        KineticPalmRenderer.renderHand(hand, poseStack);
    }
}