package com.timestop.mixin;

import com.timestop.core.TimeStopManager;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MinecraftServer.class)
public abstract class MinecraftServerMixin implements com.timestop.core.IMinecraftServerTimeStop {

    @Inject(method = "tickServer", at = @At("HEAD"))
    private void timestop$recordServerTick(java.util.function.BooleanSupplier hasTimeLeft, CallbackInfo ci) {
        com.timestop.core.rewind.TickRecorder.getInstance().serverTick((MinecraftServer) (Object) this);
    }

    @Shadow private long nextTickTimeNanos;
    @Shadow private long delayedTasksMaxNextTickTimeNanos;
    @Inject(method = "tickServer", at = @At("TAIL"))
    private void timestop$predictionTick(java.util.function.BooleanSupplier hasTimeLeft, CallbackInfo ci) {
        com.timestop.prediction.PredictionTeleports.tick((MinecraftServer) (Object) this);
        com.timestop.prediction.PredictionServer.tick((MinecraftServer) (Object) this);
    }


    @Inject(method = "stopServer", at = @At("HEAD"))
    private void timestop$clearPrediction(CallbackInfo ci) {
        com.timestop.prediction.PredictionServer.clear();
    }

    @Override
    public void timestop$wakeServerTick() {
        long cur = net.minecraft.Util.getNanos();
        long targetTickMs = TimeStopManager.getServerTickMs() * 1_000_000L;
        if (this.nextTickTimeNanos > cur + targetTickMs) {
            this.nextTickTimeNanos = cur + targetTickMs;
            this.delayedTasksMaxNextTickTimeNanos = this.nextTickTimeNanos;
        }
    }

    @Inject(
        method = "runServer",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/server/MinecraftServer;waitUntilNextTick()V")
    )
    private void adjustTimeStopTickDelay(CallbackInfo ci) {
        long targetTickMs = TimeStopManager.getServerTickMs();
        if (targetTickMs > 50L) {
            long delta = targetTickMs - 50L;
            long deltaNanos = delta * 1_000_000L;
            this.nextTickTimeNanos += deltaNanos;
            this.delayedTasksMaxNextTickTimeNanos = this.nextTickTimeNanos;
        } else if (targetTickMs < 50L) {
            long curNanos = net.minecraft.Util.getNanos();
            long targetNanos = targetTickMs * 1_000_000L;
            if (this.nextTickTimeNanos > curNanos + targetNanos) {
                this.nextTickTimeNanos = curNanos + targetNanos;
            }
            this.delayedTasksMaxNextTickTimeNanos = this.nextTickTimeNanos;
        }
    }
}
