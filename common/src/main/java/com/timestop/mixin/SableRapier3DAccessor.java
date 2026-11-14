package com.timestop.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Access to stock collision records; no native library changes. */
@Pseudo
@Mixin(targets = "dev.ryanhcode.sable.physics.impl.rapier.Rapier3D", remap = false)
public interface SableRapier3DAccessor {
    @Invoker(value = "clearCollisions", remap = false)
    static double[] timestop$clearCollisions(long scene) { throw new AssertionError(); }
}
