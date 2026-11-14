package com.timestop.fabric.mixin;

import com.timestop.combat.TaczProjectileCompat;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** TaCZ raycasts do not call Projectile.canHitEntity. Ignore the last chain victim. */
@Pseudo
@Mixin(targets = "com.tacz.guns.util.EntityUtil", remap = false)
public abstract class TaczEntityUtilMixin {
    @Inject(method = "getHitResult", at = @At("HEAD"), cancellable = true, remap = false)
    private static void timestop$skipPreviousVictim(Projectile bullet, Entity target, Vec3 start, Vec3 end,
                                                   CallbackInfoReturnable<Object> ci) {
        if (TaczProjectileCompat.skipRicochetTarget(bullet, target)) ci.setReturnValue(null);
    }
}
