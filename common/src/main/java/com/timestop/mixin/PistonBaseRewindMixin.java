package com.timestop.mixin;

import com.timestop.core.rewind.RewindExecutor;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PistonBaseBlock.class)
public abstract class PistonBaseRewindMixin {
    @Inject(method = "checkIfExtend", at = @At("HEAD"), cancellable = true)
    private void rewindPower(Level level, BlockPos pos, BlockState state, CallbackInfo ci) {
        if (RewindExecutor.isApplyingPlan()) ci.cancel();
    }
}
