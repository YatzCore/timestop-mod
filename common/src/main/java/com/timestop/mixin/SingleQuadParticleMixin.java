package com.timestop.mixin;

import com.timestop.core.ClientBubbleManager;
import com.timestop.core.ClientTimeStopManager;
import com.timestop.core.TimeMode;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.particle.SingleQuadParticle;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SingleQuadParticle.class)
public abstract class SingleQuadParticleMixin extends net.minecraft.client.particle.Particle {

    protected SingleQuadParticleMixin(net.minecraft.client.multiplayer.ClientLevel level, double x, double y, double z) {
        super(level, x, y, z);
    }

    @Inject(method = "render", at = @At("HEAD"))
    private void freezeParticleCoords(VertexConsumer buffer, Camera camera, float partialTicks, CallbackInfo ci) {
        if (ClientBubbleManager.hasActiveBubbles()) {
            if (ClientBubbleManager.isPositionInStasis(this.x, this.y, this.z)) {
                this.xo = this.x;
                this.yo = this.y;
                this.zo = this.z;
            }
        } else if (ClientTimeStopManager.isTimeStopped() && ClientTimeStopManager.getCurrentMode() == TimeMode.TIME_STOP) {
            this.xo = this.x;
            this.yo = this.y;
            this.zo = this.z;
        }
    }

    @ModifyVariable(method = "render", at = @At("HEAD"), argsOnly = true)
    private float clampSingleQuadPartialTick(float partialTicks) {
        if (ClientBubbleManager.hasActiveBubbles()) {
            if (ClientBubbleManager.isPositionInStasis(this.x, this.y, this.z)) {
                return 1.0F;
            }
        } else if (ClientTimeStopManager.isTimeStopped() && ClientTimeStopManager.getCurrentMode() == TimeMode.TIME_STOP) {
            return 1.0F;
        }
        return partialTicks;
    }
}
