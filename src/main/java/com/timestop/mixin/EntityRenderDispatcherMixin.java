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
        if (com.timestop.core.ClientBubbleManager.hasActiveBubbles()) {
            com.timestop.core.ClientBubbleManager.ClientBubble b = com.timestop.core.ClientBubbleManager.getDominantBubble(entity.position());
            if (b != null && b.mode == com.timestop.core.TimeMode.TIME_STOP && !b.canEntityAct(entity)) {
                return 1.0F; // Clamp frame interpolation only for entities inside stasis bubbles
            }
            return partialTicks; // Smooth rendering for entities outside or in slow-mo/matrix/superhot
        }

        if (ClientTimeStopManager.isTimeStopped() 
                && ClientTimeStopManager.getCurrentMode() == com.timestop.core.TimeMode.TIME_STOP 
                && !ClientTimeStopManager.isEntityExempt(entity)) {
            return 1.0F; // Clamp frame interpolation only in TIME_STOP to eliminate jitter
        }
        return partialTicks;
    }
}
