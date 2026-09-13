package com.timestop.fabric.mixin;

import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.HitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Projectile.class)
public abstract class FabricProjectileImpactMixin {
    @Inject(method = "onHit", at = @At("HEAD"), cancellable = true)
    private void timestop$onHit(HitResult hitResult, CallbackInfo ci) {
        Projectile projectile = (Projectile) (Object) this;
        if (com.timestop.combat.OrbitalProjectileManager.onProjectileImpact(projectile, hitResult)) {
            ci.cancel();
            return;
        }
        com.timestop.combat.VoltaicRicochetHandler.onProjectileImpact(projectile, hitResult);
        com.timestop.combat.VolatileStasisHandler.onProjectileImpact(projectile);
        if (com.timestop.combat.KineticPalmManager.onDroppedProjectileImpact(projectile)) {
            ci.cancel();
            return;
        }
    }
}