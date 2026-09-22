package com.timestop.mixin;

import com.timestop.core.rewind.TickRecorder;
import com.timestop.core.rewind.data.BlockEntityDelta;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.piston.PistonMovingBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PistonMovingBlockEntity.class)
public abstract class PistonMotionRewindMixin {
    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private static void rewindMotion(Level level, BlockPos pos, BlockState state, PistonMovingBlockEntity be, CallbackInfo ci) {
        if (level.isClientSide && ((com.timestop.core.ClientTimeStopManager.isGlobalTimeStopActive()
                && com.timestop.core.ClientTimeStopManager.getCurrentMode() == com.timestop.core.TimeMode.REWIND)
                || com.timestop.core.ClientBubbleManager.isRewinding(net.minecraft.world.phys.Vec3.atCenterOf(pos)))) {
            ci.cancel();
            return;
        }
        var recorder = TickRecorder.getInstance();
        if (!level.isClientSide && recorder.isRecording() && recorder.getCurrentFrame() != null) {
            var tag = be.saveWithFullMetadata(level.registryAccess());
            recorder.getCurrentFrame().addBlockEntityDelta(BlockEntityDelta.create(level.dimension(), pos,
                    BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(be.getType()), tag, tag));
        }
    }
}
