package com.timestop.mixin;

import com.timestop.core.SelfAcceleration;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import javax.annotation.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Self Acceleration: shortens the client-side delays between placing/using and between breaking blocks. */
@Mixin(Minecraft.class)
public abstract class SelfAccelerationClientMixin {
    @Shadow private int rightClickDelay;
    @Shadow @Nullable public LocalPlayer player;
    @Shadow @Nullable public MultiPlayerGameMode gameMode;
    @Unique private double timestop$delayCredit;

    @Inject(method = "tick", at = @At("TAIL"))
    private void timestop$selfAcceleratedDelays(CallbackInfo ci) {
        double rate = this.player == null ? 1 : com.timestop.core.PersonalTime.extraRate(this.player);
        if (rate <= 1) { this.timestop$delayCredit = 0; return; }
        this.timestop$delayCredit += rate - 1;
        int extra = (int) this.timestop$delayCredit;
        this.timestop$delayCredit -= extra;
        if (extra <= 0) return;
        this.rightClickDelay = Math.max(0, this.rightClickDelay - extra);
        if (this.gameMode != null) {
            MultiPlayerGameModeAccessor access = (MultiPlayerGameModeAccessor) this.gameMode;
            access.timestop$setDestroyDelay(Math.max(0, access.timestop$getDestroyDelay() - extra));
        }
    }
}
