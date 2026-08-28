package com.timestop.mixin;

import com.timestop.core.TimeMode;
import com.timestop.core.TimeStopManager;
import net.minecraft.world.entity.projectile.AbstractHurtingProjectile;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Projectile.class)
public abstract class ProjectileMixin {

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void onPreTick(CallbackInfo ci) {
        Projectile projectile = (Projectile) (Object) this;

        // Complete suspended stasis in TIME_STOP mode
        if (TimeStopManager.isTimeStopped(projectile.level()) && TimeStopManager.getCurrentMode() == TimeMode.TIME_STOP) {
            if (projectile.tickCount >= 1) {
                Vec3 vel = projectile.getDeltaMovement();
                if (vel.lengthSqr() <= 1.0E-5 && projectile instanceof AbstractHurtingProjectile hurting) {
                    vel = new Vec3(hurting.xPower, hurting.yPower, hurting.zPower).scale(10.0);
                }
                if (vel.lengthSqr() > 1.0E-5) {
                    TimeStopManager.registerSuspendedProjectile(projectile, vel);
                }
                ci.cancel();
            }
        }
    }
}
