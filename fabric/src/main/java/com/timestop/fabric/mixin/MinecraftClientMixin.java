package com.timestop.fabric.mixin;

import com.timestop.client.ClientInteractionHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Minecraft.class)
public abstract class MinecraftClientMixin {

    @Inject(method = "startAttack", at = @At("HEAD"), cancellable = true)
    private void timestop$startAttack(CallbackInfoReturnable<Boolean> cir) {
        Minecraft mc = (Minecraft) (Object) this;
        if (ClientInteractionHandler.onAttackKey(mc)) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "startUseItem", at = @At("HEAD"), cancellable = true)
    private void timestop$startUseItem(CallbackInfo ci) {
        Minecraft mc = (Minecraft) (Object) this;
        if (ClientInteractionHandler.onUseItemKey(mc, InteractionHand.MAIN_HAND)) {
            ci.cancel();
        }
    }
}
