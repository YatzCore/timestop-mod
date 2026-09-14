package com.timestop.mixin;

import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractArrow.class)
public abstract class AbstractArrowMixin {

    /**
     * Prevents high-velocity / reflected arrows from bouncing and jittering repeatedly when lodged in the ground.
     * In vanilla, high-velocity arrows slightly overshoot the block boundary, causing shouldFall() to alternate
     * between true and false every second. We expand the collision check so resting arrows stay firmly anchored.
     */
    @Inject(method = "shouldFall", at = @At("HEAD"), cancellable = true)
    private void onShouldFall(CallbackInfoReturnable<Boolean> cir) {
        AbstractArrow arrow = (AbstractArrow) (Object) this;
        AABB box = arrow.getBoundingBox().inflate(0.25D);
        if (!arrow.level().noCollision(box)) {
            cir.setReturnValue(false);
        }
    }
}
