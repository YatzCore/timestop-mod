package com.timestop.mixin;

import com.timestop.client.TranspositionRenderer;
import com.timestop.combat.TranspositionManager;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Client-only outline hooks must not load Minecraft's renderer on a dedicated server. */
@Mixin(Entity.class)
public abstract class EntityOutlineMixin {
    @Inject(method = "isCurrentlyGlowing", at = @At("HEAD"), cancellable = true)
    private void onIsCurrentlyGlowing(CallbackInfoReturnable<Boolean> cir) {
        Entity entity = (Entity) (Object) this;
        var player = Minecraft.getInstance().player;
        if (entity.level().isClientSide() && player != null && TranspositionManager.hasTranspositionRune(player)
                && TranspositionRenderer.isTargetOutlined(entity)) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "getTeamColor", at = @At("HEAD"), cancellable = true)
    private void onGetTeamColor(CallbackInfoReturnable<Integer> cir) {
        Entity entity = (Entity) (Object) this;
        var player = Minecraft.getInstance().player;
        if (entity.level().isClientSide() && player != null && TranspositionManager.hasTranspositionRune(player)) {
            int color = TranspositionRenderer.getTargetOutlineColor(entity);
            if (color != -1) cir.setReturnValue(color);
        }
    }
}
