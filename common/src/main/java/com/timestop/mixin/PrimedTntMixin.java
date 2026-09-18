package com.timestop.mixin;

import com.timestop.core.ClientTimeStopManager;
import com.timestop.core.TimeMode;
import net.minecraft.world.entity.item.PrimedTnt;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PrimedTnt.class)
public abstract class PrimedTntMixin {
    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void timestop$freezeClientFuse(CallbackInfo ci) {
        PrimedTnt tnt = (PrimedTnt) (Object) this;
        if (tnt.level().isClientSide && ((ClientTimeStopManager.isGlobalTimeStopActive()
                && ClientTimeStopManager.getCurrentMode() == TimeMode.REWIND)
                || com.timestop.core.ClientBubbleManager.isRewinding(tnt.position()))) {
            tnt.setOldPosAndRot();
            ci.cancel();
        }
    }
}
