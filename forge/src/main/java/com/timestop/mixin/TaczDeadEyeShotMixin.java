package com.timestop.mixin;

import com.timestop.combat.TaczPrecision;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/** Scope native scheduled rounds without depending on compiler-generated lambda names. */
@Pseudo
@Mixin(targets = "com.tacz.guns.item.ModernKineticGunScriptAPI", remap = false)
public abstract class TaczDeadEyeShotMixin {
    @Shadow private Supplier<Float> pitchSupplier;
    @Shadow private ItemStack itemStack;

    @ModifyArg(method = "shootOnce", at = @At(value = "INVOKE",
            target = "Lcom/tacz/guns/util/CycleTaskHelper;addCycleTask(Ljava/util/function/BooleanSupplier;JI)V"),
            index = 0, remap = false)
    private BooleanSupplier timestop$scopeRound(BooleanSupplier round) {
        return TaczPrecision.wrapShot(round, pitchSupplier, itemStack);
    }
}
