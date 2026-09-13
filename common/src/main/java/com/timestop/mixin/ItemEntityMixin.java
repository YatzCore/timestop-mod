package com.timestop.mixin;

import com.timestop.core.TimeMode;
import com.timestop.core.TimeStopManager;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import javax.annotation.Nullable;
import java.util.UUID;

@Mixin(ItemEntity.class)
public abstract class ItemEntityMixin {

    @Shadow
    private int pickupDelay;

    @Shadow
    @Nullable
    private UUID thrower;

    @Inject(method = "playerTouch", at = @At("HEAD"))
    private void onPlayerTouch(Player player, CallbackInfo ci) {
        ItemEntity item = (ItemEntity) (Object) this;
        // ONLY intervene during full TIME_STOP where item physics and entity ticks are frozen!
        // In SLOW_MOTION, MATRIX, and SUPERHOT, vanilla pickupDelay (40 ticks) naturally counts down.
        if (TimeStopManager.isTimeStopped(item.level()) && TimeStopManager.getCurrentMode() == TimeMode.TIME_STOP) {
            if (this.pickupDelay > 0 && this.pickupDelay != 32767) {
                if (this.thrower == null || !this.thrower.equals(player.getUUID())) {
                    // Block drops, mob drops, chest drops, or items from other entities
                    this.pickupDelay = 0;
                } else if (player.isCrouching()) {
                    // Items thrown by this player with Q: only retrieve if intentionally crouching/sneaking!
                    this.pickupDelay = 0;
                }
            }
        }
    }
}
