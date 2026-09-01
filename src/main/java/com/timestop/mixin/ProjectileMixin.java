package com.timestop.mixin;

import com.timestop.core.TimeMode;
import com.timestop.core.TimeStopManager;
import net.minecraft.world.entity.projectile.AbstractHurtingProjectile;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
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

        Level level = projectile.level();

        if (level.isClientSide) {
            if (com.timestop.client.ClientOrbitalHandler.isOrbiting(projectile.getId())) {
                ci.cancel();
                return;
            }
            if (com.timestop.core.ClientBubbleManager.hasActiveBubbles()) {
                if (com.timestop.core.ClientBubbleManager.isPositionInStasis(projectile.position())) {
                    ci.cancel();
                }
                return;
            }
            if (com.timestop.core.ClientTimeStopManager.isTimeStopped() && com.timestop.core.ClientTimeStopManager.getCurrentMode() == TimeMode.TIME_STOP) {
                ci.cancel();
            }
            return;
        }

        // Complete suspended stasis while captured in orbit
        if (projectile.getPersistentData().getBoolean("InStasisOrbit")) {
            ci.cancel();
            return;
        }

        boolean isStasis = false;
        if (com.timestop.core.TemporalBubbleManager.hasActiveBubbles()) {
            com.timestop.core.TemporalBubble dominant = com.timestop.core.TemporalBubbleManager.getDominantBubble(level.dimension(), projectile.position());
            if (dominant != null && dominant.getMode() == TimeMode.TIME_STOP) {
                isStasis = true;
            }
        } else if (TimeStopManager.isTimeStopped(level) && TimeStopManager.getCurrentMode() == TimeMode.TIME_STOP) {
            isStasis = true;
        }

        // Complete suspended stasis in TIME_STOP mode
        if (isStasis) {
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
        } else {
            // Projectile is not in stasis: if it was previously suspended, resume it immediately!
            if (TimeStopManager.isProjectileSuspended(projectile) && level instanceof net.minecraft.server.level.ServerLevel sl) {
                TimeStopManager.resumeSingleProjectile(sl, projectile);
            }
        }
    }
}
