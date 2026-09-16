package com.timestop.neoforge.mixin;

import com.timestop.combat.TaczPrecision;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.function.Supplier;

@Pseudo
@Mixin(targets = "com.tacz.guns.item.ModernKineticGunScriptAPI", remap = false)
public abstract class TaczDeadEyeShotMixin {
    @Shadow private Supplier<Float> pitchSupplier;
    @Shadow private ItemStack itemStack;

    @Inject(method = "lambda$shootOnce$2", at = @At("HEAD"), remap = false)
    private void beginShot(CallbackInfoReturnable<Boolean> ci) {
        TaczPrecision.begin(pitchSupplier);
    }

    @Inject(method = "lambda$shootOnce$2", at = @At("RETURN"), remap = false)
    private void finishShot(CallbackInfoReturnable<Boolean> ci) {
        TaczPrecision.end(ci.getReturnValueZ(), itemStack);
    }
}
