package com.timestop.mixin;

import com.timestop.core.ClientTimeStopManager;
import net.minecraft.client.DeltaTracker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(DeltaTracker.Timer.class)
public abstract class DeltaTrackerTimerMixin {

    @Redirect(
            method = "advanceGameTime",
            at = @At(
                    value = "INVOKE",
                    target = "Lit/unimi/dsi/fastutil/floats/FloatUnaryOperator;apply(F)F",
                    remap = false
            )
    )
    private float redirectTargetMspt(it.unimi.dsi.fastutil.floats.FloatUnaryOperator operator, float defaultMspt) {
        return ClientTimeStopManager.isGlobalTimeStopActive()
                ? ClientTimeStopManager.getClientTickMs() : operator.apply(defaultMspt);
    }
}
