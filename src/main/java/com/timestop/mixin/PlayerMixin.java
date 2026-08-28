package com.timestop.mixin;

import com.timestop.core.ClientTimeStopManager;
import com.timestop.core.TimeMode;
import com.timestop.core.TimeStopManager;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemCooldowns;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Player.class)
public abstract class PlayerMixin extends LivingEntity {

    protected PlayerMixin(EntityType<? extends LivingEntity> type, Level level) {
        super(type, level);
    }

    @Inject(method = "aiStep", at = @At("TAIL"))
    private void onMatrixPlayerAiStep(CallbackInfo ci) {
        Player player = (Player) (Object) this;
        if (isAcceleratedExempt(player)) {
            // 1. Weapon Hit Cooldown (Attack recharge):
            // Normal 20 TPS increments attackStrengthTicker by 1 every 50ms.
            // At slow TPS (200-250ms per tick), we advance it by +3 more (total 4 per tick = 20/sec)
            // so weapon attack cooldown recharges at full normal speed!
            this.attackStrengthTicker += 3;
        }

        if (com.timestop.combat.TachyonRuneHandler.isTachyonActive(player)) {
            // Tachyon flurry: double attack strength recharge speed!
            this.attackStrengthTicker += 2;
        }

        if (isAcceleratedExempt(player)) {

            // 2. Item Cooldowns (Ender pearls, shields, chorus fruit):
            ItemCooldowns cooldowns = player.getCooldowns();
            if (cooldowns != null) {
                for (int i = 0; i < 3; i++) {
                    cooldowns.tick();
                }
            }

            // 3. Item Usage (Eating food, drinking potions, drawing bow, loading crossbow):
            // Accelerate useItemRemaining so charging, eating, and shooting complete at full normal speed!
            if (this.isUsingItem() && this.useItem != null && !this.useItem.isEmpty()) {
                for (int i = 0; i < 3; i++) {
                    if (this.useItemRemaining > 0) {
                        this.useItemRemaining--;
                        if (this.useItemRemaining <= 0) {
                            if (!this.useItem.useOnRelease()) {
                                if (!this.level().isClientSide) {
                                    this.completeUsingItem();
                                }
                            }
                            break;
                        }
                    }
                }
            }
        }
    }

    private static boolean isAcceleratedExempt(Player player) {
        if (player.level().isClientSide) {
            if (!ClientTimeStopManager.isTimeStopped() || !ClientTimeStopManager.isEntityExempt(player)) return false;
            TimeMode mode = ClientTimeStopManager.getCurrentMode();
            return mode == TimeMode.MATRIX || mode == TimeMode.SUPERHOT;
        } else {
            if (!TimeStopManager.isTimeStopped(player.level()) || !TimeStopManager.isEntityExempt(player)) return false;
            TimeMode mode = TimeStopManager.getCurrentMode();
            return mode == TimeMode.MATRIX || mode == TimeMode.SUPERHOT;
        }
    }
}
