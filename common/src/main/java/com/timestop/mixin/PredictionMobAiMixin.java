package com.timestop.mixin;

import com.timestop.prediction.PredictionTime;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Mob.class)
public abstract class PredictionMobAiMixin {
    @Unique private double timestop$predictionAiTime;
    @Inject(method="serverAiStep", at=@At("HEAD"), cancellable=true)
    private void timestop$predictionAiStep(CallbackInfo ci) {
        double rate = PredictionTime.rate((Mob)(Object)this);
        if (rate >= 1) { timestop$predictionAiTime = 0; return; }
        timestop$predictionAiTime += rate;
        if (timestop$predictionAiTime < 1 - 1e-8) {
            // Slow decision/attack timers, but steer and animate continuously between those decisions.
            // Cancelling the entire AI step also froze navigation/look/jump controls for three ticks.
            Mob mob=(Mob)(Object)this;
            mob.getNavigation().tick();
            mob.getMoveControl().tick();
            mob.getLookControl().tick();
            mob.getJumpControl().tick();
            ci.cancel();
        }
        else timestop$predictionAiTime -= 1;
    }
}
