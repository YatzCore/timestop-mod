package com.timestop.mixin;

import com.timestop.combat.TemporalDamageBuffer;
import com.timestop.core.TimeMode;
import com.timestop.core.TimeStopManager;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {

    @Inject(method = "hurt", at = @At("HEAD"), cancellable = true)
    private void onHurt(DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        LivingEntity entity = (LivingEntity) (Object) this;
        // Damage accumulation and hit suspension is EXCLUSIVELY for TIME_STOP mode!
        // In SLOW_MOTION, MATRIX, and FAST_FORWARD, hits register immediately.
        if (!entity.level().isClientSide 
                && TimeStopManager.isTimeStopped(entity.level()) 
                && TimeStopManager.getCurrentMode() == TimeMode.TIME_STOP 
                && !TimeStopManager.isEntityExempt(entity)) {
            TemporalDamageBuffer.recordHit(entity, amount, source);
            cir.setReturnValue(false);
        }
    }
}
