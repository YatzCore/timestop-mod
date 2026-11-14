package com.timestop.mixin;

import net.minecraft.world.entity.projectile.LargeFireball;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(LargeFireball.class)
public interface PredictionFireballAccessor {
    @Accessor("explosionPower") int timestop$explosionPower();
}
