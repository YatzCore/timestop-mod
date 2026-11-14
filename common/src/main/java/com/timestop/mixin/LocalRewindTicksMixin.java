package com.timestop.mixin;

import com.timestop.core.rewind.LocalRewind;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.material.Fluid;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerLevel.class)
public abstract class LocalRewindTicksMixin {
    @Inject(method = "tickBlock", at = @At("HEAD"), cancellable = true)
    private void deferBlock(BlockPos pos, Block block, CallbackInfo ci) {
        var level=(ServerLevel)(Object)this;
        if(LocalRewind.contains(level.dimension(),pos) || com.timestop.core.TemporalBubbleManager.isPositionInStasis(level.dimension(), net.minecraft.world.phys.Vec3.atCenterOf(pos))) { level.scheduleTick(pos,block,1); ci.cancel(); }
    }

    @Inject(method = "tickFluid", at = @At("HEAD"), cancellable = true)
    private void deferFluid(BlockPos pos, Fluid fluid, CallbackInfo ci) {
        var level=(ServerLevel)(Object)this;
        if(LocalRewind.contains(level.dimension(),pos) || com.timestop.core.TemporalBubbleManager.isPositionInStasis(level.dimension(), net.minecraft.world.phys.Vec3.atCenterOf(pos))) { level.scheduleTick(pos,fluid,1); ci.cancel(); }
    }
    // Random block/fluid ticks are handled by RandomTickBlockStateMixin / RandomTickFluidStateMixin.
    // They used to be @Redirects on ServerLevel.tickChunk, which crashed with Lithium
    // (alloc.chunk_random redirects the same two calls; two redirects cannot share a call site).
}
