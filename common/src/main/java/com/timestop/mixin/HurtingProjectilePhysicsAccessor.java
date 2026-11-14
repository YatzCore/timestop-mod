package com.timestop.mixin;

import net.minecraft.world.entity.projectile.AbstractHurtingProjectile;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(AbstractHurtingProjectile.class)
public interface HurtingProjectilePhysicsAccessor {
    @Invoker("getInertia") float timestop$inertia();
}
