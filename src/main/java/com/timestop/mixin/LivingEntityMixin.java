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

        boolean timeActive = entity.level().isClientSide 
                ? com.timestop.core.ClientTimeStopManager.isTimeStopped() 
                : TimeStopManager.isGlobalTimeStopped();

        // Zero Damage Immunity Lockout (Projectiles ONLY in non-stasis modes):
        if (timeActive && source.is(net.minecraft.tags.DamageTypeTags.IS_PROJECTILE)) {
            entity.invulnerableTime = 0;
            entity.hurtTime = 0;
            entity.hurtDuration = 0;
        }
    }
}
