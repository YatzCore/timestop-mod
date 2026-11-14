package com.timestop.mixin;

import com.timestop.combat.DecelerationFieldManager;
import com.timestop.combat.ProjectileCombatHelper;
import com.timestop.combat.RuneManager;
import net.minecraft.world.entity.projectile.*;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Continuous native movement/collision steps; other projectile models use their native tick budget. */
@Mixin({AbstractArrow.class, ThrowableProjectile.class, AbstractHurtingProjectile.class, LlamaSpit.class})
public abstract class ProjectileDecelerationMixin {
    @Unique private Vec3 timestop$fullVelocity;
    @Unique private double timestop$stepRate;
    @Unique private double timestop$gravity;

    @Inject(method = "tick", at = @At("HEAD"))
    private void onHeadTick(CallbackInfo ci) {
        timestop$fullVelocity = null;
        Projectile projectile = (Projectile) (Object) this;
        if (!ProjectileCombatHelper.isActiveInFlight(projectile)
                || com.timestop.platform.EntityDataHelper.getPersistentData(projectile).getBoolean("KineticPalmCaptured")
                || com.timestop.platform.EntityDataHelper.getPersistentData(projectile).getBoolean("InStasisOrbit")) return;
        var defender = DecelerationFieldManager.getProtectingPlayer(projectile);
        if (defender != null) RuneManager.evaluateRuneDefense(projectile, defender);
        if (!ProjectileCombatHelper.isActiveInFlight(projectile)
                || com.timestop.platform.EntityDataHelper.getPersistentData(projectile).getBoolean("KineticPalmCaptured")
                || com.timestop.platform.EntityDataHelper.getPersistentData(projectile).getBoolean("InStasisOrbit")) return;
        timestop$stepRate = DecelerationFieldManager.getMovementScale(projectile);
        if (timestop$stepRate >= 1.0) return;
        timestop$fullVelocity = projectile.getDeltaMovement();
        timestop$gravity = projectile.isNoGravity() ? 0 : projectile instanceof ThrowableProjectile
                ? ((ThrowableProjectilePhysicsAccessor) projectile).timestop$gravity()
                : projectile instanceof AbstractArrow ? .05 : projectile instanceof LlamaSpit ? .06 : 0;
        projectile.setDeltaMovement(timestop$fullVelocity.scale(timestop$stepRate));
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void onTailTick(CallbackInfo ci) {
        if (timestop$fullVelocity == null) return;
        Projectile projectile = (Projectile) (Object) this;
        Vec3 original = timestop$fullVelocity;
        timestop$fullVelocity = null;
        if (!ProjectileCombatHelper.isActiveInFlight(projectile)
                || com.timestop.platform.EntityDataHelper.getPersistentData(projectile).getBoolean("KineticPalmCaptured")
                || com.timestop.platform.EntityDataHelper.getPersistentData(projectile).getBoolean("InStasisOrbit")) return;
        double rate = timestop$stepRate;
        if (projectile instanceof AbstractHurtingProjectile hurting) {
            var access = (HurtingProjectilePhysicsAccessor) hurting;
            double inertia = hurting.isInWater() ? .8 : access.timestop$inertia();
            Vec3 acceleration = new Vec3(hurting.xPower, hurting.yPower, hurting.zPower);
            projectile.setDeltaMovement(original.scale(1 + (inertia - 1) * rate).add(acceleration.scale(inertia * rate)));
        } else {
            // Native drag already acted on v * rate. Keep that change, but integrate
            // the native gravity for only the elapsed projectile time, not a whole tick.
            Vec3 change = projectile.getDeltaMovement().subtract(original.scale(rate));
            projectile.setDeltaMovement(original.add(change).add(0, timestop$gravity * (1 - rate), 0));
        }
    }
}
