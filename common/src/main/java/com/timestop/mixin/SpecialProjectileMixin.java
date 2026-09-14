package com.timestop.mixin;

import com.timestop.core.ClientTimeStopManager;
import com.timestop.core.TimeMode;
import com.timestop.core.TimeStopManager;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.ShulkerBullet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * ShulkerBullet explicitly overrides isPickable() in vanilla to return false.
 * This mixin enables pickability when temporal stasis or slow-motion is active.
 */
@Mixin(ShulkerBullet.class)
public abstract class SpecialProjectileMixin {

    @Inject(method = "isPickable", at = @At("HEAD"), cancellable = true)
    private void onSpecialIsPickable(CallbackInfoReturnable<Boolean> cir) {
        Entity entity = (Entity) (Object) this;
        boolean timeActive = entity.level().isClientSide ? ClientTimeStopManager.isTimeStopped() : TimeStopManager.isGlobalTimeStopped();
        if (timeActive) {
            TimeMode mode = entity.level().isClientSide ? ClientTimeStopManager.getCurrentMode() : TimeStopManager.getCurrentMode();
            if (mode == TimeMode.TIME_STOP || mode == TimeMode.SLOW_MOTION || mode == TimeMode.MATRIX || mode == TimeMode.SUPERHOT || mode == TimeMode.DECELERATION_FIELD) {
                cir.setReturnValue(true);
                return;
            }
        }

        if (entity instanceof net.minecraft.world.entity.projectile.Projectile p && com.timestop.combat.DecelerationFieldManager.isDecelerated(p)) {
            cir.setReturnValue(true);
        }
    }
}
