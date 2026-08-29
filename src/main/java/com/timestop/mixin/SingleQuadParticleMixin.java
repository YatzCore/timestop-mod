package com.timestop.mixin;

import com.timestop.core.ClientTimeStopManager;
import com.timestop.core.TimeMode;
import net.minecraft.client.particle.SingleQuadParticle;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(SingleQuadParticle.class)
public abstract class SingleQuadParticleMixin {

    @ModifyVariable(method = "render", at = @At("HEAD"), argsOnly = true)
    private float clampSingleQuadPartialTick(float partialTicks) {
        if (ClientTimeStopManager.isTimeStopped() && ClientTimeStopManager.getCurrentMode() == TimeMode.TIME_STOP) {
            return 1.0F; // Clamps partial ticks to 1.0F so Mth.lerp(1.0F, xo, x) evaluates strictly to this.x, eliminating all trembling and jumping
        }
        return partialTicks;
    }
}
