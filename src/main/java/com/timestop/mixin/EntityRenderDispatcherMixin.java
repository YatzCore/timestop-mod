package com.timestop.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.timestop.core.ClientTimeStopManager;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(EntityRenderDispatcher.class)
public abstract class EntityRenderDispatcherMixin {

    @ModifyVariable(method = "render", at = @At("HEAD"), argsOnly = true, ordinal = 1)
    private float clampPartialTicksForFrozenEntities(float partialTicks, Entity entity) {
        if (ClientTimeStopManager.isTimeStopped() && !ClientTimeStopManager.isEntityExempt(entity)) {
            return 1.0F; // Clamp frame interpolation to eliminate jitter
        }
        return partialTicks;
    }
}
