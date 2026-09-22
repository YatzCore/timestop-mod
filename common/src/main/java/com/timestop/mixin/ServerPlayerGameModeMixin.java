package com.timestop.mixin;

import com.timestop.core.rewind.RewindPlayerInteraction;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerPlayerGameMode;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(ServerPlayerGameMode.class)
public abstract class ServerPlayerGameModeMixin implements RewindPlayerInteraction {
    @Shadow protected ServerLevel level;
    @Shadow @Final protected ServerPlayer player;
    @Shadow private boolean isDestroyingBlock;
    @Shadow private boolean hasDelayedDestroy;
    @Shadow private BlockPos destroyPos;
    @Shadow private BlockPos delayedDestroyPos;
    @Shadow private int lastSentState;

    @Override
    public void timestop$resetBreaking() {
        if (isDestroyingBlock) level.destroyBlockProgress(player.getId(), destroyPos, -1);
        if (hasDelayedDestroy) level.destroyBlockProgress(player.getId(), delayedDestroyPos, -1);
        isDestroyingBlock = false;
        hasDelayedDestroy = false;
        lastSentState = -1;
    }
}
