package com.timestop.mixin;

import com.timestop.core.TimeStopManager;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MinecraftServer.class)
public abstract class MinecraftServerMixin {

    @Shadow private long nextTickTimeNanos;
    @Shadow private long delayedTasksMaxNextTickTimeNanos;

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
