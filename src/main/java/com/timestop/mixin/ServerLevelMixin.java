package com.timestop.mixin;

import com.timestop.core.TimeMode;
import com.timestop.core.TimeStopManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerLevel.class)
public abstract class ServerLevelMixin {

    @Inject(method = "tickNonPassenger", at = @At("HEAD"), cancellable = true)
    private void onTickNonPassenger(Entity entity, CallbackInfo ci) {
        ServerLevel level = (ServerLevel) (Object) this;
        // Cancelling ticks is EXCLUSIVELY for TIME_STOP!
        // In SLOW_MOTION and MATRIX, entities tick smoothly with slowed attributes.
        if (TimeStopManager.isTimeStopped(level) 
                && TimeStopManager.getCurrentMode() == TimeMode.TIME_STOP 
                && !TimeStopManager.isEntityExempt(entity)) {
            entity.setOldPosAndRot();
            ci.cancel();
        }
    }

    @Inject(method = "tickPassenger", at = @At("HEAD"), cancellable = true)
    private void onTickPassenger(Entity vehicle, Entity passenger, CallbackInfo ci) {
        ServerLevel level = (ServerLevel) (Object) this;
        if (TimeStopManager.isTimeStopped(level) 
                && TimeStopManager.getCurrentMode() == TimeMode.TIME_STOP 
                && !TimeStopManager.isEntityExempt(passenger)) {
            passenger.setOldPosAndRot();
            ci.cancel();
        }
    }

    @Inject(method = "tickChunk", at = @At("HEAD"), cancellable = true)
    private void onTickChunk(LevelChunk chunk, int randomTickSpeed, CallbackInfo ci) {
        ServerLevel level = (ServerLevel) (Object) this;
        if (TimeStopManager.isTimeStopped(level) && TimeStopManager.getCurrentMode() == TimeMode.TIME_STOP) {
            ci.cancel();
        }
    }

    @Inject(method = "advanceWeatherCycle", at = @At("HEAD"), cancellable = true)
    private void onAdvanceWeatherCycle(CallbackInfo ci) {
        ServerLevel level = (ServerLevel) (Object) this;
        if (TimeStopManager.isTimeStopped(level) && TimeStopManager.getCurrentMode() == TimeMode.TIME_STOP) {
            ci.cancel();
        }
    }

    @Inject(method = "tickTime", at = @At("HEAD"), cancellable = true)
    private void onTickTime(CallbackInfo ci) {
        ServerLevel level = (ServerLevel) (Object) this;
        if (TimeStopManager.isTimeStopped(level) && TimeStopManager.getCurrentMode() == TimeMode.TIME_STOP) {
            ci.cancel();
        }
    }
}
