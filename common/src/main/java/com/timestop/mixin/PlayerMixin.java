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
                timestop$advanceItemUse(extraTicks);
            }
        }

        if (com.timestop.combat.TachyonRuneHandler.isTachyonActive(player)) {
            // Tachyon flurry: attack recharge boost
            this.attackStrengthTicker += 1;
        }

        // Personal clock: Self Acceleration, and the server half of your own Fast Forward field.
        double selfRate = com.timestop.core.PersonalTime.extraRate(player);
        if (selfRate > 1.0) {
            this.timestop$selfTickCredit += selfRate - 1.0;
            int extra = (int) this.timestop$selfTickCredit;
            this.timestop$selfTickCredit -= extra;
            if (extra > 0) timestop$runPersonalTicks(player, extra);
        } else {
            this.timestop$selfTickCredit = 0;
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

    @org.spongepowered.asm.mixin.Unique
    private double timestop$selfTickCredit;
    @org.spongepowered.asm.mixin.Unique
    private int timestop$personalTicks;

    /**
     * Extra personal ticks: attack recharge, item cooldowns, item use (eating, drinking, bows,
     * crossbows, shields), status effects, hunger/natural healing and starvation, breath and
     * drowning, burning, freezing and damage immunity. Movement, falling, mining and use/place
     * delays are scaled at their own call sites. In Casual mode the harmful parts keep normal speed.
     */
    @org.spongepowered.asm.mixin.Unique
    private void timestop$runPersonalTicks(Player player, int extra) {
        boolean casual = com.timestop.core.PersonalTime.isCasual(player);
        boolean server = !player.level().isClientSide;
        this.attackStrengthTicker += extra;
        ItemCooldowns cooldowns = player.getCooldowns();
        for (int i = 0; i < extra && player.isAlive(); i++) {
            this.timestop$personalTicks++;
            if (cooldowns != null) cooldowns.tick();
            if (casual) timestop$tickHelpfulEffects(server);
            else this.tickEffects();
            // Starvation is harmful: in Casual mode, skip extra hunger ticks while starving.
            if (server && !(casual && player.getFoodData().getFoodLevel() <= 0)) player.getFoodData().tick(player);
            if (server) timestop$personalBreath(player, casual);
            if (server && !casual) timestop$personalBurning(player);
            if (server) timestop$personalFreezing(player, casual);
            if (server && !casual && this.invulnerableTime > 0) this.invulnerableTime--;
        }
        timestop$advanceItemUse(extra);
    }

    /** Casual mode: only beneficial and neutral effects run faster; harmful ones keep normal speed. */
    @org.spongepowered.asm.mixin.Unique
    private void timestop$tickHelpfulEffects(boolean server) {
        for (var instance : new java.util.ArrayList<>(this.getActiveEffects())) {
            if (instance.getEffect().getCategory() == net.minecraft.world.effect.MobEffectCategory.HARMFUL) continue;
            if (!instance.tick(this, () -> this.onEffectUpdated(instance, true, null)) && server) {
                this.removeEffect(instance.getEffect());
            }
        }
    }

    /** Breath: drowning underwater (harmful), recovering air above water (helpful). Mirrors LivingEntity.baseTick. */
    @org.spongepowered.asm.mixin.Unique
    private void timestop$personalBreath(Player player, boolean casual) {
        if (player.getAbilities().invulnerable) return;
        boolean underwater = player.isEyeInFluid(net.minecraft.tags.FluidTags.WATER)
                && !player.level().getBlockState(net.minecraft.core.BlockPos.containing(player.getX(), player.getEyeY(), player.getZ()))
                        .is(net.minecraft.world.level.block.Blocks.BUBBLE_COLUMN);
        if (underwater) {
            if (casual || player.canBreatheUnderwater() || net.minecraft.world.effect.MobEffectUtil.hasWaterBreathing(player)) return;
            player.setAirSupply(this.decreaseAirSupply(player.getAirSupply()));
            if (player.getAirSupply() == -20) {
                player.setAirSupply(0);
                player.hurt(player.damageSources().drown(), 2.0F);
            }
        } else if (player.getAirSupply() < player.getMaxAirSupply()) {
            player.setAirSupply(this.increaseAirSupply(player.getAirSupply()));
        }
    }

    /** Burning: fire damage every 20 personal ticks, and the fire burns out sooner. Mirrors Entity.baseTick. */
    @org.spongepowered.asm.mixin.Unique
    private void timestop$personalBurning(Player player) {
        int fire = player.getRemainingFireTicks();
        if (fire <= 0 || player.fireImmune()) return;
        if (fire % 20 == 0 && !player.isInLava() && !player.getAbilities().invulnerable) player.hurt(player.damageSources().onFire(), 1.0F);
        player.setRemainingFireTicks(fire - 1);
    }

    /** Powder snow: freezing builds up (harmful) or thaws (helpful) faster. Mirrors LivingEntity.aiStep. */
    @org.spongepowered.asm.mixin.Unique
    private void timestop$personalFreezing(Player player, boolean casual) {
        int frozen = player.getTicksFrozen();
        if (player.isInPowderSnow && player.canFreeze()) {
            if (casual) return;
            player.setTicksFrozen(Math.min(player.getTicksRequiredToFreeze(), frozen + 1));
        } else {
            player.setTicksFrozen(Math.max(0, frozen - 2));
        }
        if (!casual && this.timestop$personalTicks % 40 == 0 && player.isFullyFrozen() && player.canFreeze()
                && !player.getAbilities().invulnerable) {
            player.hurt(player.damageSources().freeze(), 1.0F);
        }
    }

    /** Self Acceleration: falling speeds up with your personal clock (jump height is unchanged). */
    @Inject(method = "travel", at = @At("TAIL"))
    private void timestop$acceleratedFall(net.minecraft.world.phys.Vec3 input, CallbackInfo ci) {
        Player player = (Player) (Object) this;
        double rate = com.timestop.core.SelfAcceleration.rate(player);
        if (rate <= 1 || player.onGround() || player.isInWater() || player.isInLava() || player.getAbilities().flying
                || player.isFallFlying() || player.onClimbable() || player.isNoGravity() || player.isPassenger()
                || player.hasEffect(net.minecraft.world.effect.MobEffects.LEVITATION)) return;
        var motion = player.getDeltaMovement();
        double gravity = motion.y <= 0 && player.hasEffect(net.minecraft.world.effect.MobEffects.SLOW_FALLING) ? 0.01 : 0.08;
        double dy = com.timestop.core.PersonalTime.acceleratedFall(motion.y, gravity, rate);
        if (dy != motion.y) player.setDeltaMovement(motion.x, dy, motion.z);
    }

    @org.spongepowered.asm.mixin.Unique
    private void timestop$advanceItemUse(int ticks) {
        if (!this.isUsingItem() || this.useItem == null || this.useItem.isEmpty()) return;
        for (int i = 0; i < ticks; i++) {
            if (this.useItemRemaining > 0) {
                this.useItemRemaining--;
                if (this.useItemRemaining <= 0) {
                    if (!this.useItem.useOnRelease() && !this.level().isClientSide) {
                        this.completeUsingItem();
                    }
                    break;
                }
            }
        }
    }

    private static boolean isAcceleratedExempt(Player player) {
        if (player.level().isClientSide) {
            if (!ClientTimeStopManager.isTimeStopped() || !ClientTimeStopManager.isEntityExempt(player)) return false;
            TimeMode mode = ClientTimeStopManager.getCurrentMode();
            return mode == TimeMode.TIME_PREDICTION || mode == TimeMode.MATRIX || mode == TimeMode.SLOW_MOTION || mode == TimeMode.SUPERHOT;
        } else {
            if (com.timestop.core.TemporalBubbleManager.hasActiveBubbles()) {
                com.timestop.core.TemporalBubble b = com.timestop.core.TemporalBubbleManager.getDominantBubble(player.level().dimension(), player.position());
                if (b == null || !b.canEntityAct(player)) return false;
                return b.getMode() == TimeMode.TIME_PREDICTION || b.getMode() == TimeMode.MATRIX || b.getMode() == TimeMode.SLOW_MOTION || b.getMode() == TimeMode.SUPERHOT;
            }
            if (!TimeStopManager.isTimeStopped(player.level()) || !TimeStopManager.isEntityExempt(player)) return false;
            TimeMode mode = TimeStopManager.getCurrentMode();
            return mode == TimeMode.TIME_PREDICTION || mode == TimeMode.MATRIX || mode == TimeMode.SLOW_MOTION || mode == TimeMode.SUPERHOT;
        }
    }

    // Mining speed (Tachyon rune, Self Acceleration) is applied per loader: Fabric hooks Player.getDestroySpeed,
    // Forge hooks Player.getDigSpeed (which block breaking calls there; a getDestroySpeed hook never reaches it).
}
