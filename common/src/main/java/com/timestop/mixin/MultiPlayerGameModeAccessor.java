package com.timestop.mixin;

import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(MultiPlayerGameMode.class)
public interface MultiPlayerGameModeAccessor {
    @Accessor("destroyDelay") int timestop$getDestroyDelay();
    @Accessor("destroyDelay") void timestop$setDestroyDelay(int delay);
}
