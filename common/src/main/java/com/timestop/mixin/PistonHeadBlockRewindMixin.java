package com.timestop.mixin;

import com.timestop.core.rewind.RewindExecutor;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.piston.PistonHeadBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PistonHeadBlock.class)
public abstract class PistonHeadBlockRewindMixin {
    @Inject(method = "onRemove", at = @At("HEAD"), cancellable = true)
    private void rewindRemove(BlockState state, Level level, BlockPos pos, BlockState next, boolean moving, CallbackInfo ci) {
        if (!level.isClientSide && RewindExecutor.isApplyingPlan()) {
            level.removeBlockEntity(pos);
            ci.cancel();
        }
    }
}
