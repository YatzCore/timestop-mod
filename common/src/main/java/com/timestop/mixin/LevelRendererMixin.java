package com.timestop.mixin;

import com.timestop.client.WeatherFreezeManager;
import com.timestop.core.ClientTimeStopManager;
import com.timestop.core.TimeMode;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
public abstract class LevelRendererMixin {

    @Shadow
    private int ticks;

    @ModifyVariable(method = "renderEntity", at = @At("HEAD"), argsOnly = true)
    private float clampPartialTicksInLevelRenderer(float partialTicks, Entity entity) {
        if (com.timestop.core.ClientBubbleManager.hasActiveBubbles()) {
            com.timestop.core.ClientBubbleManager.ClientBubble b = com.timestop.core.ClientBubbleManager.getDominantBubble(entity.getX(), entity.getY() + entity.getBbHeight() * 0.5, entity.getZ());
            if (b != null) {
                if (b.mode == TimeMode.TIME_STOP && !b.canEntityAct(entity)) {
                    entity.setOldPosAndRot();
                    return 1.0F; // Freeze interpolation strictly for entities inside stasis!
                }
                return partialTicks; // Normal smooth 144+ FPS animation for all other modes!
            }
        }

        if (ClientTimeStopManager.isGlobalTimeStopActive() && !ClientTimeStopManager.isEntityExempt(entity)) {
            if (ClientTimeStopManager.getCurrentMode() == TimeMode.TIME_STOP) {
                entity.setOldPosAndRot();
                return 1.0F;
            }
        }
        return partialTicks;
    }

    @ModifyVariable(method = "renderSky", at = @At("HEAD"), argsOnly = true)
    private float clampPartialTicksInSky(float partialTicks) {
        if (ClientTimeStopManager.isTimeStopped() && ClientTimeStopManager.getCurrentMode() == TimeMode.TIME_STOP) {
            return 1.0F; // Freeze sun, moon, and sky rotation with zero jitter
        }
        return partialTicks;
    }

    @ModifyVariable(method = "renderSnowAndRain", at = @At("HEAD"), argsOnly = true)
    private float modifyRainPartialTick(float partialTick) {
        WeatherFreezeManager.update(this.ticks, partialTick);
        if (ClientTimeStopManager.isTimeStopped()) {
            return WeatherFreezeManager.getEffectivePartialTick(partialTick);
        }
        return partialTick;
    }

    @Redirect(
            method = "renderSnowAndRain",
            at = @At(value = "FIELD", target = "Lnet/minecraft/client/renderer/LevelRenderer;ticks:I")
    )
    private int redirectRainTicks(LevelRenderer renderer) {
        if (ClientTimeStopManager.isTimeStopped()) {
            return WeatherFreezeManager.getEffectiveTicks(this.ticks);
        }
        return this.ticks;
    }

    @ModifyVariable(method = "renderClouds", at = @At("HEAD"), argsOnly = true)
    private float modifyCloudPartialTick(float partialTick) {
        if (ClientTimeStopManager.isTimeStopped()) {
            return WeatherFreezeManager.getEffectivePartialTick(partialTick);
        }
        return partialTick;
    }

    @Redirect(
            method = "renderClouds",
            at = @At(value = "FIELD", target = "Lnet/minecraft/client/renderer/LevelRenderer;ticks:I")
    )
    private int redirectCloudTicks(LevelRenderer renderer) {
        if (ClientTimeStopManager.isTimeStopped()) {
            return WeatherFreezeManager.getEffectiveTicks(this.ticks);
        }
        return this.ticks;
    }

    @Inject(method = "tickRain", at = @At("HEAD"), cancellable = true)
    private void onTickRain(Camera camera, CallbackInfo ci) {
        if (com.timestop.core.ClientBubbleManager.isCameraInsideStasis() 
                || (ClientTimeStopManager.isTimeStopped() && ClientTimeStopManager.getCurrentMode() == TimeMode.TIME_STOP)) {
            ci.cancel();
        }
    }
}
