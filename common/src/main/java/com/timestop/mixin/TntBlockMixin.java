package com.timestop.mixin;

import com.timestop.core.rewind.RewindExecutor;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.TntBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TntBlock.class)
public abstract class TntBlockMixin {
    @Inject(method = "onPlace", at = @At("HEAD"), cancellable = true)
    private void timestop$restoreWithoutIgniting(BlockState state, Level level, BlockPos pos, BlockState previous, boolean moving, CallbackInfo ci) {
        if (!level.isClientSide && RewindExecutor.isApplyingPlan()) ci.cancel();
    }

    @Inject(method = "neighborChanged", at = @At("HEAD"), cancellable = true)
    private void timestop$ignoreRollbackPower(BlockState state, Level level, BlockPos pos, Block block, BlockPos source, boolean moving, CallbackInfo ci) {
        if (!level.isClientSide && RewindExecutor.isApplyingPlan()) ci.cancel();
    }
}
