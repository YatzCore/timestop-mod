package com.timestop.mixin;

import com.timestop.core.rewind.TickRecorder;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Native assembly writes chunks directly, bypassing vanilla block recording. */
@Pseudo
@Mixin(targets = "dev.ryanhcode.sable.api.SubLevelAssemblyHelper", remap = false)
public abstract class SableAssemblyHistoryMixin {
    @Inject(method = "moveBlocks", at = @At("RETURN"), remap = false)
    private static void timestop$seedMovedInventories(ServerLevel level, SubLevelAssemblyHelper.AssemblyTransform transform,
                                                    Iterable<BlockPos> blocks, CallbackInfo ci) {
        var recorder = TickRecorder.getInstance();
        if (!recorder.isRecording()) return;
        var destination = transform.getLevel();
        for (var from : blocks) {
            var be = destination.getBlockEntity(transform.apply(from));
            // Native movement loads NBT after creating the BE. Its construction-time baseline is empty/stale.
            if (be != null) recorder.observeBlockEntityExplicit(be, be.saveWithFullMetadata(destination.registryAccess()));
        }
    }

    @Inject(method = "moveBlocks", at = @At("HEAD"), remap = false)
    private static void timestop$recordMove(ServerLevel level, SubLevelAssemblyHelper.AssemblyTransform transform,
                                          Iterable<BlockPos> blocks, CallbackInfo ci) {
        var recorder = TickRecorder.getInstance();
        if (!recorder.isRecording()) return;
        var destination = transform.getLevel();
        for (var from : blocks) {
            var to = transform.apply(from);
            var sourceBody = Sable.HELPER.getContaining(level, from);
            var targetBody = Sable.HELPER.getContaining(destination, to);
            if (sourceBody == null) {
                recorder.recordNativeBlockMove(level, from, Blocks.AIR.defaultBlockState());
                if (targetBody != null) recorder.recordSubLevelBlockDependency(level, targetBody.getUniqueId(), from);
            }
            if (targetBody == null) {
                recorder.recordNativeBlockMove(destination, to, transform.apply(level.getBlockState(from)));
                if (sourceBody != null) recorder.recordSubLevelBlockDependency(destination, sourceBody.getUniqueId(), to);
            }
            if (sourceBody != null && targetBody != null)
                recorder.recordSubLevelDependency(level, sourceBody.getUniqueId(), targetBody.getUniqueId());
        }
    }
}
