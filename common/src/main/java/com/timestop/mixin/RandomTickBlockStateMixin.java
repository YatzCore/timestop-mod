package com.timestop.mixin;

import com.timestop.pedestal.PedestalWorldTime;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Applies local rewind / stasis / pedestal time rates to random block ticks.
 * Hooked on the callee (not ServerLevel.tickChunk) so it composes with mods such as
 * Lithium that redirect the randomTick call inside tickChunk.
 */
@Mixin(BlockBehaviour.BlockStateBase.class)
public abstract class RandomTickBlockStateMixin {
    @Inject(method = "randomTick", at = @At("HEAD"), cancellable = true)
    private void timestop$scaleRandomTick(ServerLevel level, BlockPos pos, RandomSource random, CallbackInfo ci) {
        if (PedestalWorldTime.interceptRandomBlockTick((BlockState) (Object) this, level, pos, random)) ci.cancel();
    }
}
