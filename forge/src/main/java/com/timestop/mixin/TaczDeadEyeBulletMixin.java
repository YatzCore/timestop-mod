package com.timestop.mixin;

import com.timestop.combat.TaczProjectileCompat;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.*;
import net.minecraftforge.event.ForgeEventFactory;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "com.tacz.guns.entity.EntityKineticBullet", remap = false)
public abstract class TaczDeadEyeBulletMixin {
    @Unique private boolean timestop$handledImpact;
    @Unique private Vec3 timestop$fullVelocity;
    @Shadow private float gravity;
    @Shadow private float friction;

    @Inject(method = {"tick", "m_8119_"}, at = @At("HEAD"), cancellable = true, remap = false)
    private void beforeNativeTick(CallbackInfo ci) {
        timestop$handledImpact = false;
        Projectile bullet = (Projectile) (Object) this;
        timestop$fullVelocity = null;
        com.timestop.combat.TaczPrecision.guide(bullet);
        if (TaczProjectileCompat.beforeTick(bullet)) { ci.cancel(); return; }
        if (com.timestop.combat.DecelerationFieldManager.isDecelerated(bullet)) {
            timestop$fullVelocity = bullet.getDeltaMovement();
            bullet.setDeltaMovement(timestop$fullVelocity.scale(0.2));
        }
    }

    @Inject(method = {"tick", "m_8119_"}, at = @At("TAIL"), remap = false)
    private void restoreNativeVelocity(CallbackInfo ci) {
        Projectile bullet = (Projectile) (Object) this;
        if (timestop$fullVelocity == null || !bullet.isAlive() || timestop$handledImpact) return;
        double drag = bullet.isInWater() ? 0.4 : friction;
        double fall = bullet.isInWater() ? gravity * 0.6 : gravity;
        bullet.setDeltaMovement(timestop$fullVelocity.scale(1 - drag * 0.2).add(0, -fall * 0.2, 0));
        timestop$fullVelocity = null;
    }

    @Inject(method = "onHitEntity", at = @At("HEAD"), cancellable = true, remap = false)
    private void entityImpact(@Coerce EntityHitResult hit, Vec3 start, Vec3 end, CallbackInfo ci) {
        if (com.timestop.combat.ProjectileStasisSweep.beforeImpact((Projectile) (Object) this, hit.getLocation())) {
            timestop$handledImpact = true; ci.cancel(); return;
        }
        timestop$handledImpact = ForgeEventFactory.onProjectileImpact((Projectile) (Object) this, hit);
        if (timestop$handledImpact) ci.cancel();
    }

    @Inject(method = "onHitBlock", at = @At("HEAD"), cancellable = true, remap = false)
    private void blockImpact(BlockHitResult hit, Vec3 start, Vec3 end, CallbackInfo ci) {
        if (hit.getType() == HitResult.Type.MISS) return;
        if (com.timestop.combat.ProjectileStasisSweep.beforeImpact((Projectile) (Object) this, hit.getLocation())) {
            timestop$handledImpact = true; ci.cancel(); return;
        }
        timestop$handledImpact = ForgeEventFactory.onProjectileImpact((Projectile) (Object) this, hit);
        if (timestop$handledImpact) ci.cancel();
    }

    @Inject(method = "onBulletTick", at = @At(value = "INVOKE", target = "Lcom/tacz/guns/entity/EntityKineticBullet;onHitEntity(Lcom/tacz/guns/util/TacHitResult;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;)V", shift = At.Shift.AFTER), cancellable = true, remap = false)
    private void preserveCancelledImpact(CallbackInfo ci) {
        if (timestop$handledImpact) ci.cancel();
    }

    @Inject(method = {"tick", "m_8119_"}, at = @At(value = "INVOKE", target = "Lcom/tacz/guns/entity/EntityKineticBullet;onBulletTick()V", shift = At.Shift.AFTER), cancellable = true, remap = false)
    private void stopHandledMovement(CallbackInfo ci) {
        Projectile bullet = (Projectile) (Object) this;
        if (timestop$handledImpact || !bullet.isAlive()
                || com.timestop.combat.ProjectileStasisSweep.beforeImpact(bullet, bullet.position().add(bullet.getDeltaMovement()))) ci.cancel();
    }
}
