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
        if (level instanceof net.minecraft.world.level.Level l) {
            net.minecraft.world.phys.Vec3 blockCenter = new net.minecraft.world.phys.Vec3(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
            boolean inStasis = false;

            if (l.isClientSide) {
                if (com.timestop.core.ClientBubbleManager.hasActiveBubbles() && com.timestop.core.ClientBubbleManager.isPositionInStasis(blockCenter)) {
                    inStasis = true;
                } else if (com.timestop.core.ClientTimeStopManager.isGlobalTimeStopActive() && com.timestop.core.ClientTimeStopManager.getCurrentMode() == TimeMode.TIME_STOP) {
                    inStasis = true;
                }
            } else {
                if (com.timestop.core.TemporalBubbleManager.hasActiveBubbles() && com.timestop.core.TemporalBubbleManager.isPositionInStasis(l.dimension(), blockCenter)) {
                    inStasis = true;
                } else if (TimeStopManager.isGlobalTimeStopActive() && TimeStopManager.getCurrentMode() == TimeMode.TIME_STOP) {
                    inStasis = true;
                }
            }

            if (inStasis && com.timestop.config.TimeStopConfig.COMMON.enableWaterWalkingInStasis.get()) {
                // Allow entities walking on top of frozen liquids to walk across, but don't trap entities submerged inside
                if (context.isAbove(Shapes.block(), pos, true)) {
                    cir.setReturnValue(Shapes.block());
                }
            }
        }
    }
}
