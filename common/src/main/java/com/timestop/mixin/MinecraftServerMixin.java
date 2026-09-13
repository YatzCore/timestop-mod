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

    @Shadow private long nextTickTime;
    @Shadow private long delayedTasksMaxNextTickTime;

    @Inject(
        method = "runServer",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/server/MinecraftServer;waitUntilNextTick()V")
    )
    private void adjustTimeStopTickDelay(CallbackInfo ci) {
        long cur = net.minecraft.Util.getMillis();
        if (!TimeStopManager.isGlobalTimeStopActive()) {
            if (this.nextTickTime > cur + 50L) {
                this.nextTickTime = cur + 50L;
                this.delayedTasksMaxNextTickTime = this.nextTickTime;
            }
            return;
        }
        long targetTickMs = TimeStopManager.getServerTickMs();
        if (targetTickMs > 50L) {
            long delta = targetTickMs - 50L;
            this.nextTickTime += delta;
        }
        if (this.nextTickTime > cur + targetTickMs) {
            this.nextTickTime = cur + targetTickMs;
        }
        this.delayedTasksMaxNextTickTime = this.nextTickTime;
    }
}
