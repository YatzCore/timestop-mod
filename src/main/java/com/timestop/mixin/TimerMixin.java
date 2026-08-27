package com.timestop.mixin;

import com.timestop.core.ClientTimeStopManager;
import net.minecraft.client.Timer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(Timer.class)
public abstract class TimerMixin {

    @Redirect(
            method = "advanceTime",
            at = @At(value = "FIELD", target = "Lnet/minecraft/client/Timer;msPerTick:F")
    )
    private float redirectMsPerTick(Timer timer) {
        return ClientTimeStopManager.getClientTickMs();
    }
}
