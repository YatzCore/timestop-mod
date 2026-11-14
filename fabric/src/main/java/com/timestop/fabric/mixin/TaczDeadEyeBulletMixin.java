package com.timestop.fabric.mixin;

import com.timestop.combat.TaczProjectileCompat;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.*;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "com.tacz.guns.entity.EntityKineticBullet", remap = false)
public abstract class TaczDeadEyeBulletMixin implements com.timestop.combat.ProjectileDecelerationHook, com.timestop.prediction.NativePredictionPhysics {
    @Unique private boolean timestop$handledImpact;
    @Unique private Vec3 timestop$fullVelocity;
    @Unique private double timestop$stepRate;
    @Shadow private float gravity;
    @Shadow private float friction;

    @Override public double timestop$predictionGravity() { return gravity; }
    @Override public double timestop$predictionDrag() { return friction; }

    @Inject(method = {"tick", "m_8119_", "method_5773"}, at = @At("HEAD"), cancellable = true, remap = false)
    private void beforeNativeTick(CallbackInfo ci) {
        timestop$handledImpact = false;
        timestop$fullVelocity = null;
        Projectile bullet = (Projectile) (Object) this;
        com.timestop.combat.TaczPrecision.guide(bullet);
        if (TaczProjectileCompat.beforeTick(bullet)) { ci.cancel(); return; }
        timestop$stepRate = com.timestop.combat.DecelerationFieldManager.getMovementScale(bullet);
        if (timestop$stepRate < 1.0) {
            timestop$fullVelocity = bullet.getDeltaMovement();
            bullet.setDeltaMovement(timestop$fullVelocity.scale(timestop$stepRate));
        }
    }

    @Inject(method = {"tick", "m_8119_", "method_5773"}, at = @At("TAIL"), remap = false)
    private void restoreNativeVelocity(CallbackInfo ci) {
        Projectile bullet = (Projectile) (Object) this;
        if (timestop$fullVelocity == null || !bullet.isAlive() || timestop$handledImpact) return;
        double drag = bullet.isInWater() ? 0.4 : friction;
        double fall = bullet.isInWater() ? gravity * 0.6 : gravity;
        bullet.setDeltaMovement(timestop$fullVelocity.scale(1 - drag * timestop$stepRate).add(0, -fall * timestop$stepRate, 0));
        timestop$fullVelocity = null;
    }

    @Inject(method = "onBulletTick", at = @At("HEAD"), remap = false)
    private void guideMarkedBullet(CallbackInfo ci) {
        com.timestop.combat.TaczPrecision.guide((Projectile) (Object) this);
    }

    @Inject(method = "onHitEntity", at = @At("HEAD"), cancellable = true, remap = false)
    private void entityImpact(@Coerce EntityHitResult hit, Vec3 start, Vec3 end, CallbackInfo ci) {
        timestop$handledImpact = TaczProjectileCompat.beforeImpact((Projectile) (Object) this, hit);
        if (timestop$handledImpact) ci.cancel();
    }

    @Inject(method = "onHitEntity", at = @At("TAIL"), remap = false)
    private void afterNativeEntityImpact(@Coerce EntityHitResult hit, Vec3 start, Vec3 end, CallbackInfo ci) {
        timestop$handledImpact = TaczProjectileCompat.afterEntityImpact((Projectile) (Object) this, hit);
    }

    @Inject(method = "onHitBlock", at = @At("HEAD"), cancellable = true, remap = false)
    private void blockImpact(BlockHitResult hit, Vec3 start, Vec3 end, CallbackInfo ci) {
        if (hit.getType() == HitResult.Type.MISS) return;
        timestop$handledImpact = TaczProjectileCompat.beforeImpact((Projectile) (Object) this, hit);
        if (timestop$handledImpact) ci.cancel();
    }

    @Inject(method = "onBulletTick", at = @At(value = "INVOKE", target = "Lcom/tacz/guns/entity/EntityKineticBullet;onHitEntity", shift = At.Shift.AFTER), cancellable = true, remap = false)
    private void preserveCancelledImpact(CallbackInfo ci) {
        if (timestop$handledImpact) ci.cancel();
    }

    @Inject(method = {"tick", "m_8119_", "method_5773"}, at = @At(value = "INVOKE", target = "Lcom/tacz/guns/entity/EntityKineticBullet;onBulletTick()V", shift = At.Shift.AFTER), cancellable = true, remap = false)
    private void stopHandledMovement(CallbackInfo ci) {
        if (timestop$handledImpact || !((Projectile) (Object) this).isAlive()) ci.cancel();
    }
}
