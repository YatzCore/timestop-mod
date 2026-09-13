package com.timestop.mixin;

import com.timestop.core.ClientTimeStopManager;
import com.timestop.core.TimeMode;
import net.minecraft.client.renderer.texture.TextureAtlas;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TextureAtlas.class)
public abstract class TextureAtlasMixin {

    @Inject(method = "cycleAnimationFrames", at = @At("HEAD"), cancellable = true)
    private void onCycleAnimationFrames(CallbackInfo ci) {
        if (ClientTimeStopManager.isTimeStopped() && ClientTimeStopManager.getCurrentMode() == TimeMode.TIME_STOP) {
            ci.cancel(); // Freeze all animated textures (fire on mobs, burning blocks, lava, water) in mid-frame!
        }
    }
}
