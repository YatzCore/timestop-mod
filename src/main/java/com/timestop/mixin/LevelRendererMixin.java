package com.timestop.mixin;

import com.timestop.core.ClientTimeStopManager;
import com.timestop.core.TimeMode;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(LevelRenderer.class)
public abstract class LevelRendererMixin {

    @ModifyVariable(method = "renderEntity", at = @At("HEAD"), argsOnly = true)
    private float clampPartialTicksInLevelRenderer(float partialTicks, Entity entity) {
        if (ClientTimeStopManager.isTimeStopped() && !ClientTimeStopManager.isEntityExempt(entity)) {
            if (ClientTimeStopManager.getCurrentMode() == TimeMode.TIME_STOP) {
                entity.setOldPosAndRot();
                return 1.0F; // Freeze interpolation to exact position
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
}
