package com.timestop.mixin;

import com.timestop.pedestal.PedestalWorldTime;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.FluidState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Suppresses random fluid ticks inside local rewind areas and time-stop stasis. */
@Mixin(FluidState.class)
public abstract class RandomTickFluidStateMixin {
    @Inject(method = "randomTick", at = @At("HEAD"), cancellable = true)
    private void timestop$suppressRandomTick(Level level, BlockPos pos, RandomSource random, CallbackInfo ci) {
        if (level instanceof ServerLevel server && PedestalWorldTime.suppressRandomFluidTick(server, pos)) ci.cancel();
    }
}
