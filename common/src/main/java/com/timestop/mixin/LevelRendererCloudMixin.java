package com.timestop.mixin;

import com.timestop.client.WeatherFreezeManager;
import com.timestop.core.ClientTimeStopManager;
import net.minecraft.client.renderer.LevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Freezes cloud drift while time is stopped. Kept apart from LevelRendererMixin because renderers such as
 * Sodium 0.5.x overwrite renderClouds: Mixin only lets a FIELD injector into an overwritten method when its
 * own priority is higher than the overwriting mixin's (1000), and otherwise fails the whole class.
 * Optional (require = 0), so a renderer that no longer reads the tick counter there cannot crash the game.
 */
@Mixin(value = LevelRenderer.class, priority = 1100)
public abstract class LevelRendererCloudMixin {

    @Shadow
    private int ticks;

    @Redirect(
            method = "renderClouds",
            at = @At(value = "FIELD", target = "Lnet/minecraft/client/renderer/LevelRenderer;ticks:I"),
            require = 0
    )
    private int redirectCloudTicks(LevelRenderer renderer) {
        if (ClientTimeStopManager.isTimeStopped()) {
            return WeatherFreezeManager.getEffectiveTicks(this.ticks);
        }
        return this.ticks;
    }
}
