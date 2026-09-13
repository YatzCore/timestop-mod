package com.timestop.mixin;

import com.timestop.core.TimeMode;
import com.timestop.core.TimeStopManager;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import net.minecraft.world.level.block.entity.TickingBlockEntity;
import net.minecraft.core.BlockPos;

@Mixin(Level.class)
public abstract class LevelMixin {

    @Redirect(method = "tickBlockEntities", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/block/entity/TickingBlockEntity;tick()V"))
    private void tickUnlessInStasis(TickingBlockEntity ticker) {
        Level level = (Level) (Object) this;
        BlockPos pos = ticker.getPos();
        boolean stopped = level.isClientSide
                ? (com.timestop.core.ClientTimeStopManager.isGlobalTimeStopActive() && com.timestop.core.ClientTimeStopManager.getCurrentMode() == TimeMode.TIME_STOP)
                    || com.timestop.core.ClientBubbleManager.isPositionInStasis(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5)
                : (TimeStopManager.isGlobalTimeStopActive() && TimeStopManager.getCurrentMode() == TimeMode.TIME_STOP)
                    || com.timestop.core.TemporalBubbleManager.isPositionInStasis(level.dimension(), pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
        if (!stopped) {
            ticker.tick();
        }
    }
}
