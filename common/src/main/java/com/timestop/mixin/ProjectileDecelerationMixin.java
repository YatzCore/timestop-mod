package com.timestop.mixin;

import com.timestop.combat.DecelerationFieldManager;
import com.timestop.core.TimeMode;
import com.timestop.core.TimeStopManager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.AbstractHurtingProjectile;
import net.minecraft.world.entity.projectile.LlamaSpit;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ShulkerBullet;
import net.minecraft.world.entity.projectile.ThrowableProjectile;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Directly targets the concrete projectile subclasses that implement tick() movement:
 * AbstractArrow, ThrowableProjectile, AbstractHurtingProjectile, ShulkerBullet, and LlamaSpit.
 *
 * Scales per-tick displacement to 20% (80% slowdown) while inside the deceleration field,
 * preserving natural parabolic trajectory and true velocity upon exit.
 */
@Mixin({AbstractArrow.class, ThrowableProjectile.class, AbstractHurtingProjectile.class, ShulkerBullet.class, LlamaSpit.class})
public abstract class ProjectileDecelerationMixin {

    @Unique
    private Vec3 timestop$preTickVelocity = null;

    @Inject(method = "tick", at = @At("HEAD"))
    private void onHeadTick(CallbackInfo ci) {
        Projectile projectile = (Projectile) (Object) this;
        if (!projectile.isAlive() || projectile.onGround()) {
            this.timestop$preTickVelocity = null;
            return;
        }

        // Complete TIME_STOP is handled via stasis cancellation, not continuous deceleration
        if (TimeStopManager.isGlobalTimeStopped() && TimeStopManager.getCurrentMode() == TimeMode.TIME_STOP) {
            this.timestop$preTickVelocity = null;
            return;
        }

        Player protecting = DecelerationFieldManager.getProtectingPlayer(projectile);
        if (protecting != null) {
            // Evaluate automated rune defense (auto-parry, auto-snatch, auto-phase)
            com.timestop.combat.RuneManager.evaluateRuneDefense(projectile, protecting);

            if (!projectile.isAlive() || projectile.getOwner() == protecting) {
                this.timestop$preTickVelocity = null;
                return;
            }

            Vec3 vel = projectile.getDeltaMovement();
            if (vel.lengthSqr() > 1.0E-5) {
                this.timestop$preTickVelocity = vel;
                // Scale displacement for this tick's movement/collision to 20% (80% slowdown)
                projectile.setDeltaMovement(vel.scale(0.2D));
            } else {
                this.timestop$preTickVelocity = null;
            }
        } else {
            this.timestop$preTickVelocity = null;
        }
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void onTailTick(CallbackInfo ci) {
        if (this.timestop$preTickVelocity != null) {
            Projectile projectile = (Projectile) (Object) this;
            Vec3 orig = this.timestop$preTickVelocity;
            this.timestop$preTickVelocity = null;

            if (!projectile.isAlive() || projectile.onGround()) {
                return;
            }

            // In water, drag is 0.6, in air 0.99
            double drag = projectile.isInWater() ? 0.6D : 0.99D;
            double gravity = (projectile instanceof AbstractArrow) ? 0.05D : 0.03D;
            if (projectile.isNoGravity()) {
                gravity = 0.0D;
            }

            // Step true velocity by 1 tick at 20% time rate
            double newX = orig.x * (1.0D - (1.0D - drag) * 0.2D);
            double newY = (orig.y * (1.0D - (1.0D - drag) * 0.2D)) - (gravity * 0.2D);
            double newZ = orig.z * (1.0D - (1.0D - drag) * 0.2D);

            projectile.setDeltaMovement(new Vec3(newX, newY, newZ));
        }
    }
}
