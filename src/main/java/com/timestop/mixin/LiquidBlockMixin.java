package com.timestop.mixin;

import com.timestop.core.TimeMode;
import com.timestop.core.TimeStopManager;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LiquidBlock.class)
public abstract class LiquidBlockMixin {

    @Inject(method = "getCollisionShape", at = @At("HEAD"), cancellable = true)
    private void onGetCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context, CallbackInfoReturnable<VoxelShape> cir) {
        boolean active;
        TimeMode mode;
        if (level instanceof net.minecraft.world.level.Level l && l.isClientSide) {
            active = com.timestop.core.ClientTimeStopManager.isTimeStopped();
            mode = com.timestop.core.ClientTimeStopManager.getCurrentMode();
        } else {
            active = TimeStopManager.isGlobalTimeStopped();
            mode = TimeStopManager.getCurrentMode();
        }
        if (active && mode == TimeMode.TIME_STOP) {
            cir.setReturnValue(Shapes.block());
        }
    }
}
