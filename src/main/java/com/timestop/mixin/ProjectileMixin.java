package com.timestop.mixin;

import com.timestop.core.TimeMode;
import com.timestop.core.TimeStopManager;
import net.minecraft.world.entity.projectile.Projectile;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Projectile.class)
public abstract class ProjectileMixin {

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void onTick(CallbackInfo ci) {
        Projectile projectile = (Projectile) (Object) this;
        if (!TimeStopManager.isTimeStopped(projectile.level())) {
            return;
        }

        // Suspended stasis is EXCLUSIVELY for TIME_STOP!
        // In SLOW_MOTION and MATRIX, the 5 TPS engine tick rate naturally makes projectiles fly in 0.25x slow motion without stopping.
        if (TimeStopManager.getCurrentMode() == TimeMode.TIME_STOP) {
            if (projectile.tickCount >= 1) {
                if (projectile.getDeltaMovement().lengthSqr() > 1.0E-5) {
                    TimeStopManager.registerSuspendedProjectile(projectile, projectile.getDeltaMovement());
                }
                ci.cancel();
            }
        }
    }
}
