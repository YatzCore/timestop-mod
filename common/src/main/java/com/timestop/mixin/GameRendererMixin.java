package com.timestop.mixin;

import com.timestop.client.DeadEyeClient;
import com.timestop.core.ClientBubbleManager;
import com.timestop.core.ClientTimeStopManager;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import javax.annotation.Nullable;

@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {

    @Inject(method = "checkEntityPostEffect", at = @At("HEAD"), cancellable = true)
    private void timestop$onCheckEntityPostEffect(@Nullable Entity entity, CallbackInfo ci) {
        if (ClientTimeStopManager.isTimeStopped() || ClientBubbleManager.hasActiveBubbles() || DeadEyeClient.clientAiming || ClientTimeStopManager.isShaderActive()) {
            ci.cancel();
        }
    }
}
