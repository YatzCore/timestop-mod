package com.timestop.mixin;

import com.timestop.prediction.PredictionTime;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public abstract class PredictionLivingMotionMixin {
    @Unique private Vec3 timestop$predictionVelocity;
    @Unique private double timestop$predictionRate;
    @Inject(method="travel", at=@At("HEAD"))
    private void timestop$beforeTravel(Vec3 input, CallbackInfo ci) {
        var entity = (LivingEntity)(Object)this;
        timestop$predictionVelocity = null;
        if (!PredictionTime.controlsLivingMotion(entity)) return;
        timestop$predictionRate = PredictionTime.rate(entity);
        if (timestop$predictionRate < 1) timestop$predictionVelocity = entity.getDeltaMovement();
    }
    @Inject(method="travel", at=@At("TAIL"))
    private void timestop$afterTravel(Vec3 input, CallbackInfo ci) {
        if (timestop$predictionVelocity == null) return;
        var entity = (LivingEntity)(Object)this;
        Vec3 integrated = timestop$predictionVelocity.lerp(entity.getDeltaMovement(), timestop$predictionRate);
        entity.setDeltaMovement(entity.horizontalCollision ? new Vec3(0, integrated.y, 0) : integrated);
        timestop$predictionVelocity = null;
    }
}
