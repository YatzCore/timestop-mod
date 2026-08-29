package com.timestop.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.timestop.core.ClientTimeStopManager;
import com.timestop.core.TimeMode;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererMixin<T extends LivingEntity, M extends EntityModel<T>> {

    @Shadow
    protected M model;

    private static final ResourceLocation CRYSTAL_RED_TEXTURE = new ResourceLocation("timestop", "textures/entity/superhot_crystal_red.png");

    @Inject(method = "getRenderType", at = @At("HEAD"), cancellable = true)
    private void onGetRenderType(T entity, boolean bodyVisible, boolean translucent, boolean outline, CallbackInfoReturnable<RenderType> cir) {
        if (ClientTimeStopManager.isTimeStopped() && ClientTimeStopManager.getCurrentMode() == TimeMode.SUPERHOT) {
            if (entity != Minecraft.getInstance().player) {
                // Completely replace entity texture with solid crystal red - original texture is NOT visible!
                cir.setReturnValue(this.model.renderType(CRYSTAL_RED_TEXTURE));
            }
        }
    }

    @ModifyVariable(
            method = "render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At("HEAD"),
            argsOnly = true,
            ordinal = 0
    )
    private int boostEnemyLightInSuperhot(int packedLight, LivingEntity entity) {
        if (ClientTimeStopManager.isTimeStopped() && ClientTimeStopManager.getCurrentMode() == TimeMode.SUPERHOT) {
            if (entity != Minecraft.getInstance().player) {
                // Full bright light level (15728880 = LightTexture.pack(15, 15)) so crystal red enemies glow
                return 15728880;
            }
        }
        return packedLight;
    }

    @Redirect(
            method = "render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/entity/layers/RenderLayer;render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/world/entity/Entity;FFFFFF)V")
    )
    @SuppressWarnings("unchecked")
    private void conditionallyRenderLayer(RenderLayer<T, M> layer, PoseStack poseStack, MultiBufferSource buffer, int packedLight, Entity entity, float limbSwing, float limbSwingAmount, float partialTicks, float ageInTicks, float netHeadYaw, float headPitch) {
        if (ClientTimeStopManager.isTimeStopped() && ClientTimeStopManager.getCurrentMode() == TimeMode.SUPERHOT) {
            if (entity != Minecraft.getInstance().player) {
                // Suppress armor, clothing, and eye layers so enemy remains 100% solid crystal red
                return;
            }
        }
        layer.render(poseStack, buffer, packedLight, (T) entity, limbSwing, limbSwingAmount, partialTicks, ageInTicks, netHeadYaw, headPitch);
    }

    @Redirect(
            method = "render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/entity/LivingEntityRenderer;getOverlayCoords(Lnet/minecraft/world/entity/LivingEntity;F)I")
    )
    private int redirectOverlayCoords(LivingEntity entity, float whiteOverlayProgress) {
        if (ClientTimeStopManager.isTimeStopped() && ClientTimeStopManager.getCurrentMode() == TimeMode.SUPERHOT) {
            if (entity != Minecraft.getInstance().player) {
                // In SUPERHOT, crystal enemies are already solid crystal red.
                // Suppressing the vanilla red hurt overlay prevents the chromatic key from being corrupted,
                // ensuring the mob NEVER flickers or flashes white when damaged!
                return net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY;
            }
        }
        return LivingEntityRenderer.getOverlayCoords(entity, whiteOverlayProgress);
    }
}
