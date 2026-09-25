package com.timestop.mixin;

import com.timestop.combat.TemporalDamageBuffer;
import com.timestop.core.TimeMode;
import com.timestop.core.TimeStopManager;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {

    @Shadow protected abstract void actuallyHurt(DamageSource damageSource, float f);
    @Shadow protected float lastHurt;

    @Inject(method = "hurt", at = @At("HEAD"), cancellable = true)
    private void onHurt(DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        LivingEntity entity = (LivingEntity) (Object) this;

        if (entity instanceof net.minecraft.server.level.ServerPlayer player) {
            if (com.timestop.combat.RewindRuneManager.isPlayerInvulnerable(player)) {
                cir.setReturnValue(false);
                return;
            }
        }

        boolean isStasis = !entity.level().isClientSide && com.timestop.core.TemporalBubbleManager.isEntityInStasis(entity);

        // Damage accumulation and hit suspension is EXCLUSIVELY for TIME_STOP stasis!
        if (isStasis) {
            entity.invulnerableTime = 0;
            entity.hurtTime = 0;
            entity.hurtDuration = 0;

            TemporalDamageBuffer.recordHit(entity, amount, source);
            // Broadcast entity hurt event (tilt + red flash) so client receives immediate visual feedback
            entity.level().broadcastEntityEvent(entity, (byte) 2);
            cir.setReturnValue(true);
            return;
        }

        float rate = timestop$getEntityRate(entity);
        if (rate < 1.0F && rate > 0.0F) {
            if (source.is(net.minecraft.tags.DamageTypeTags.IS_PROJECTILE)) {
                entity.invulnerableTime = 0;
                entity.hurtTime = 0;
                entity.hurtDuration = 0;
            } else {
                int lockout = Math.max(1, Math.round(10 * rate));
                if (entity.invulnerableTime > lockout && !source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_COOLDOWN)) {
                    if (amount <= this.lastHurt) {
                        cir.setReturnValue(false);
                        return;
                    }
                    this.actuallyHurt(source, amount - this.lastHurt);
                    this.lastHurt = amount;
                    cir.setReturnValue(true);
                    return;
                }
            }
        }
    }

    @Inject(method = "hurt", at = @At("TAIL"))
    private void onHurtPost(DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        LivingEntity entity = (LivingEntity) (Object) this;

        if (cir.getReturnValue()) {
            float rate = timestop$getEntityRate(entity);
            if (rate < 1.0F && rate > 0.0F) {
                int scaledInvuln = Math.max(2, Math.round(20 * rate));
                int scaledHurt = Math.max(1, Math.round(10 * rate));
                entity.invulnerableTime = scaledInvuln;
                entity.hurtDuration = scaledHurt;
                entity.hurtTime = scaledHurt;
            }
        }

        boolean isProj = source.is(net.minecraft.tags.DamageTypeTags.IS_PROJECTILE)
                || (source.getDirectEntity() instanceof net.minecraft.world.entity.projectile.Projectile);

        if (isProj) {
            // Remove vertical kinetic energy accumulation from bullet impacts so mobs never launch into stratosphere
            net.minecraft.world.phys.Vec3 vel = entity.getDeltaMovement();
            double clampedY = Math.min(vel.y, 0.08); // Stay grounded / slight flinch, never launch upward

            // Also clamp excessive horizontal bullet knockback if multiple rapid shots hit
            double horizLen = Math.sqrt(vel.x * vel.x + vel.z * vel.z);
            double maxHoriz = 0.45;
            double clampedX = vel.x;
            double clampedZ = vel.z;
            if (horizLen > maxHoriz) {
                clampedX = (vel.x / horizLen) * maxHoriz;
                clampedZ = (vel.z / horizLen) * maxHoriz;
            }

            entity.setDeltaMovement(clampedX, clampedY, clampedZ);
        }
    }

    @Inject(method = "handleEntityEvent", at = @At("TAIL"))
    private void onHandleEntityEvent(byte id, CallbackInfo ci) {
        if (id == 2) {
            LivingEntity entity = (LivingEntity) (Object) this;
            float rate = timestop$getEntityRate(entity);
            if (rate < 1.0F && rate > 0.0F) {
                int scaledHurt = Math.max(1, Math.round(10 * rate));
                entity.hurtDuration = scaledHurt;
                entity.hurtTime = scaledHurt;
            }
        }
    }

    @ModifyVariable(
            method = "lerpTo",
            at = @At("HEAD"),
            argsOnly = true,
            ordinal = 0
    )
    private int timestop$scaleLerpSteps(int steps) {
        LivingEntity entity = (LivingEntity) (Object) this;
        if (entity.level().isClientSide) {
            float rate = timestop$getEntityRate(entity);
            if (rate < 1.0F && rate > 0.0F) {
                int interval = (int) Math.ceil(1.0F / rate);
                return Math.max(steps, interval + 2);
            }
        }
        return steps;
    }

    @ModifyVariable(
            method = "lerpHeadTo",
            at = @At("HEAD"),
            argsOnly = true,
            ordinal = 0
    )
    private int timestop$scaleLerpHeadSteps(int steps) {
        LivingEntity entity = (LivingEntity) (Object) this;
        if (entity.level().isClientSide) {
            float rate = timestop$getEntityRate(entity);
            if (rate < 1.0F && rate > 0.0F) {
                int interval = (int) Math.ceil(1.0F / rate);
                return Math.max(steps, interval + 2);
            }
        }
        return steps;
    }

    @Unique
    private static float timestop$getEntityRate(LivingEntity entity) {
        if (entity.level().isClientSide) {
            if (com.timestop.core.ClientBubbleManager.hasActiveBubbles()) {
                var b = com.timestop.core.ClientBubbleManager.getDominantBubble(
                        entity.getX(), entity.getY() + entity.getBbHeight() * 0.5, entity.getZ());
                if (b != null) return b.getTimeDilationFactor(entity);
            }
            if (com.timestop.core.ClientTimeStopManager.isGlobalTimeStopActive()) {
                if (com.timestop.core.ClientTimeStopManager.isEntityExempt(entity)) return 1.0F;
                TimeMode mode = com.timestop.core.ClientTimeStopManager.getCurrentMode();
                if (mode == TimeMode.SLOW_MOTION) return com.timestop.config.TimeStopConfig.COMMON.slowMotionRate.get().floatValue();
                if (mode == TimeMode.MATRIX) return com.timestop.config.TimeStopConfig.COMMON.matrixRate.get().floatValue();
                if (mode == TimeMode.TIME_STOP) return 0.0F;
            }
            return 1.0F;
        } else {
            if (com.timestop.core.TemporalBubbleManager.hasActiveBubbles()) {
                var b = com.timestop.core.TemporalBubbleManager.getDominantBubble(
                        entity.level().dimension(), entity.getX(), entity.getY() + entity.getBbHeight() * 0.5, entity.getZ());
                if (b != null) return b.getTimeDilationFactor(entity);
            }
            if (TimeStopManager.isGlobalTimeStopActive()) {
                if (TimeStopManager.isEntityExempt(entity)) return 1.0F;
                TimeMode mode = TimeStopManager.getCurrentMode();
                if (mode == TimeMode.SLOW_MOTION) return com.timestop.config.TimeStopConfig.COMMON.slowMotionRate.get().floatValue();
                if (mode == TimeMode.MATRIX) return com.timestop.config.TimeStopConfig.COMMON.matrixRate.get().floatValue();
                if (mode == TimeMode.TIME_STOP) return 0.0F;
            }
            return 1.0F;
        }
    }

    @Inject(method = "checkTotemDeathProtection", at = @At("HEAD"), cancellable = true)
    private void onCheckTotemDeathProtection(DamageSource damageSource, CallbackInfoReturnable<Boolean> cir) {
        LivingEntity entity = (LivingEntity) (Object) this;
        if (entity instanceof net.minecraft.server.level.ServerPlayer player) {
            if (com.timestop.combat.RewindRuneManager.tryTriggerDeathRewind(player, damageSource)) {
                cir.setReturnValue(true);
            }
        }
    }

    @Inject(method = "die", at = @At("HEAD"), cancellable = true)
    private void onDie(DamageSource damageSource, CallbackInfo ci) {
        LivingEntity entity = (LivingEntity) (Object) this;
        if (entity instanceof net.minecraft.server.level.ServerPlayer player) {
            if (com.timestop.combat.RewindRuneManager.tryTriggerDeathRewind(player, damageSource)) {
                ci.cancel();
            }
        }
    }
}
