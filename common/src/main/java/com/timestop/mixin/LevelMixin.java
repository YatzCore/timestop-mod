package com.timestop.mixin;

import com.timestop.core.TimeMode;
import com.timestop.core.TimeStopManager;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import net.minecraft.world.level.block.entity.TickingBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Level.class)
public abstract class LevelMixin {
    @Inject(method = "getBlockEntity", at = @At("RETURN"))
    private void timestop$observeBlockEntity(BlockPos pos, CallbackInfoReturnable<BlockEntity> cir) {
        com.timestop.core.rewind.TickRecorder.getInstance().observeBlockEntity(cir.getReturnValue());
    }


    @Inject(
            method = "setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;II)Z",
            at = @At("HEAD")
    )
    private void timestop$captureBlockChange(
            BlockPos pos,
            BlockState newState,
            int flags,
            int maxUpdateDepth,
            CallbackInfoReturnable<Boolean> cir
    ) {
        Level level = (Level) (Object) this;
        if (level.isClientSide) return;

        BlockState oldState = level.getBlockState(pos);
        if (oldState.equals(newState)) return;

        BlockEntity oldBE = level.getBlockEntity(pos);
        com.timestop.core.rewind.TickRecorder.getInstance().recordBlockChange(level, pos.immutable(), oldState, newState, oldBE, null);
    }

    @Redirect(method = "tickBlockEntities", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/block/entity/TickingBlockEntity;tick()V"))
    private void tickUnlessInStasis(TickingBlockEntity ticker) {
        Level level = (Level) (Object) this;
        BlockPos pos = ticker.getPos();
        if (level.isClientSide && com.timestop.core.ClientBubbleManager.isRewinding(net.minecraft.world.phys.Vec3.atCenterOf(pos))) return;
        if (!level.isClientSide && com.timestop.core.rewind.LocalRewind.contains(level.dimension(),pos)) return;
        boolean stopped = level.isClientSide
                ? (com.timestop.core.ClientTimeStopManager.isGlobalTimeStopActive() && com.timestop.core.ClientTimeStopManager.getCurrentMode() == TimeMode.TIME_STOP)
                    || com.timestop.core.ClientBubbleManager.isPositionInStasis(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5)
                : (TimeStopManager.isGlobalTimeStopActive() && TimeStopManager.getCurrentMode() == TimeMode.TIME_STOP)
                    || com.timestop.core.TemporalBubbleManager.isPositionInStasis(level.dimension(), pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
        if (!level.isClientSide && com.timestop.core.rewind.TickRecorder.getInstance().getTimelineBuffer().isRewinding()) return;
        if (!stopped) {
            if (!level.isClientSide) com.timestop.core.rewind.TickRecorder.getInstance().observeBlockEntity(level.getBlockEntity(pos));
            ticker.tick();
            if (isFastForward(level, pos)) {
                int extraTicks = Math.max(1, (int) Math.round(com.timestop.config.TimeStopConfig.COMMON.fastForwardRate.get())) - 1;
                for (int i = 0; i < extraTicks && !ticker.isRemoved(); i++) {
                    ticker.tick();
                }
            }
        }
    }

    private static boolean isFastForward(Level level, BlockPos pos) {
        double px = pos.getX() + 0.5;
        double py = pos.getY() + 0.5;
        double pz = pos.getZ() + 0.5;
        if (level.isClientSide) {
            if (com.timestop.core.ClientTimeStopManager.isGlobalTimeStopActive()
                    && com.timestop.core.ClientTimeStopManager.getCurrentMode() == TimeMode.FAST_FORWARD) {
                return true;
            }
            if (com.timestop.core.ClientBubbleManager.hasActiveBubbles()) {
                var bubble = com.timestop.core.ClientBubbleManager.getDominantBubble(px, py, pz);
                return bubble != null && bubble.mode == TimeMode.FAST_FORWARD;
            }
            return false;
        } else {
            if (TimeStopManager.isGlobalTimeStopActive()
                    && TimeStopManager.getCurrentMode() == TimeMode.FAST_FORWARD) {
                return true;
            }
            if (com.timestop.core.TemporalBubbleManager.hasActiveBubbles()) {
                var bubble = com.timestop.core.TemporalBubbleManager.getDominantBubble(level.dimension(), px, py, pz);
                return bubble != null && bubble.getMode() == TimeMode.FAST_FORWARD;
            }
            return false;
        }
    }
}
