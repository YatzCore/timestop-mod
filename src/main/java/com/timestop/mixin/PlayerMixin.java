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

    @Inject(method = "aiStep", at = @At("HEAD"), cancellable = true)
    private void onPrePlayerAiStep(CallbackInfo ci) {
        Player player = (Player) (Object) this;
        boolean inStasis = false;
        if (player.level().isClientSide) {
            if (com.timestop.core.ClientBubbleManager.hasActiveBubbles()) {
                com.timestop.core.ClientBubbleManager.ClientBubble b = com.timestop.core.ClientBubbleManager.getDominantBubble(player.position());
                if (b != null && b.mode == TimeMode.TIME_STOP && !b.canEntityAct(player)) {
                    inStasis = true;
                }
            } else if (ClientTimeStopManager.isTimeStopped() && ClientTimeStopManager.getCurrentMode() == TimeMode.TIME_STOP && !ClientTimeStopManager.isEntityExempt(player)) {
                inStasis = true;
            }
        } else {
            if (com.timestop.core.TemporalBubbleManager.hasActiveBubbles()) {
                com.timestop.core.TemporalBubble b = com.timestop.core.TemporalBubbleManager.getDominantBubble(player.level().dimension(), player.position());
                if (b != null && b.getMode() == TimeMode.TIME_STOP && !b.canEntityAct(player)) {
                    inStasis = true;
                }
            } else if (TimeStopManager.isTimeStopped(player.level()) && TimeStopManager.getCurrentMode() == TimeMode.TIME_STOP && !TimeStopManager.isEntityExempt(player)) {
                inStasis = true;
            }
        }

        if (inStasis) {
            player.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
            player.setOldPosAndRot();
            ci.cancel();
        }
    }

    @Inject(method = "aiStep", at = @At("TAIL"))
    private void onMatrixPlayerAiStep(CallbackInfo ci) {
        Player player = (Player) (Object) this;
        TimeMode mode = player.level().isClientSide ? ClientTimeStopManager.getCurrentMode() : TimeStopManager.getCurrentMode();

        if (isAcceleratedExempt(player)) {
            // Dynamic compensation: only add extra ticks if the engine is running slower than 20 TPS!
            // At 50ms (20 TPS), extraTicks is 0. At 200ms (5 TPS), extraTicks is 3 (total 4 per tick = 20/sec).
            float tickMs = player.level().isClientSide ? ClientTimeStopManager.getClientTickMs() : TimeStopManager.getServerTickMs();
            int extraTicks = Math.max(0, Math.round((tickMs - 50.0F) / 50.0F));

            if (extraTicks > 0) {
                // In MATRIX mode, MATRIX_ATTACK_MOD already provides +300% attack speed attribute.
                // Do not duplicate with attackStrengthTicker advance.
                if (mode != TimeMode.MATRIX) {
                    this.attackStrengthTicker += extraTicks;
                }

                // 2. Item Cooldowns:
                ItemCooldowns cooldowns = player.getCooldowns();
                if (cooldowns != null) {
                    for (int i = 0; i < extraTicks; i++) {
                        cooldowns.tick();
                    }
                }

                // 3. Item Usage:
                if (this.isUsingItem() && this.useItem != null && !this.useItem.isEmpty()) {
                    for (int i = 0; i < extraTicks; i++) {
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

        if (com.timestop.combat.TachyonRuneHandler.isTachyonActive(player)) {
            // Tachyon flurry: attack recharge boost
            this.attackStrengthTicker += 1;
        }

        // In full Time Stop, allow exempt player to collect floating items around them (mined blocks, mob drops)
        if (!player.level().isClientSide && TimeStopManager.isTimeStopped(player.level()) 
                && TimeStopManager.getCurrentMode() == TimeMode.TIME_STOP 
                && TimeStopManager.isEntityExempt(player)) {
            net.minecraft.world.phys.AABB reachBox = player.getBoundingBox().inflate(1.2D, 1.2D, 1.2D);
            for (net.minecraft.world.entity.item.ItemEntity item : player.level().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, reachBox)) {
                if (!item.isRemoved()) {
                    // Do not vacuum up items the player just threw with Q unless crouching
                    if (item.getOwner() != player || player.isCrouching()) {
                        item.playerTouch(player);
                    }
                }
            }
        }
    }

    private static boolean isAcceleratedExempt(Player player) {
        if (player.level().isClientSide) {
            if (!ClientTimeStopManager.isTimeStopped() || !ClientTimeStopManager.isEntityExempt(player)) return false;
            TimeMode mode = ClientTimeStopManager.getCurrentMode();
            return mode == TimeMode.MATRIX || mode == TimeMode.SLOW_MOTION;
        } else {
            if (com.timestop.core.TemporalBubbleManager.hasActiveBubbles()) {
                com.timestop.core.TemporalBubble b = com.timestop.core.TemporalBubbleManager.getDominantBubble(player.level().dimension(), player.position());
                if (b == null || !b.canEntityAct(player)) return false;
                return b.getMode() == TimeMode.MATRIX || b.getMode() == TimeMode.SLOW_MOTION;
            }
            if (!TimeStopManager.isTimeStopped(player.level()) || !TimeStopManager.isEntityExempt(player)) return false;
            TimeMode mode = TimeStopManager.getCurrentMode();
            return mode == TimeMode.MATRIX || mode == TimeMode.SLOW_MOTION;
        }
    }
}
