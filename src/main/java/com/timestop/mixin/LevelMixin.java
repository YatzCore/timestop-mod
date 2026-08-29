package com.timestop.mixin;

import com.timestop.core.TimeMode;
import com.timestop.core.TimeStopManager;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Level.class)
public abstract class LevelMixin {

    @Inject(method = "tickBlockEntities", at = @At("HEAD"), cancellable = true)
    private void onTickBlockEntities(CallbackInfo ci) {
        Level level = (Level) (Object) this;
        boolean stopped = level.isClientSide
                ? (com.timestop.core.ClientTimeStopManager.isTimeStopped() && com.timestop.core.ClientTimeStopManager.getCurrentMode() == TimeMode.TIME_STOP)
                : (TimeStopManager.isTimeStopped(level) && TimeStopManager.getCurrentMode() == TimeMode.TIME_STOP);
        if (stopped) {
            ci.cancel();
        }
    }
}
