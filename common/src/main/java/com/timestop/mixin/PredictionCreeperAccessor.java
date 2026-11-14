package com.timestop.mixin;

import net.minecraft.world.entity.monster.Creeper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Creeper.class)
public interface PredictionCreeperAccessor {
    @Accessor("swell") int timestop$swell();
    @Accessor("maxSwell") int timestop$maxSwell();
    @Accessor("explosionRadius") int timestop$explosionRadius();
}
