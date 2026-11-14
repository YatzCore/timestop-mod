package com.timestop.mixin;

import com.timestop.core.ClientTimeStopManager;
import com.timestop.core.TimeMode;
import com.timestop.core.TimeStopManager;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.projectile.Projectile;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public abstract class EntityMixin {

    @org.spongepowered.asm.mixin.injection.ModifyVariable(method="move", at=@At("HEAD"), argsOnly=true)
    private net.minecraft.world.phys.Vec3 timestop$continuousPredictionMovement(net.minecraft.world.phys.Vec3 movement) {
        Entity entity = (Entity)(Object)this;
        return entity instanceof net.minecraft.world.entity.LivingEntity
                && com.timestop.prediction.PredictionTime.controlsLivingMotion(entity)
                ? movement.scale(com.timestop.prediction.PredictionTime.rate(entity)) : movement;
    }

    @org.spongepowered.asm.mixin.injection.ModifyVariable(method="moveRelative", at=@At("HEAD"), argsOnly=true)
    private float timestop$selfMovement(float amount) {
        Entity entity=(Entity)(Object)this;
        return entity instanceof net.minecraft.world.entity.player.Player player
                ? (float)(amount * com.timestop.core.SelfAcceleration.rate(player)) : amount;
    }

    @Inject(method = "remove", at = @At("HEAD"))
    private void timestop$onEntityRemoval(Entity.RemovalReason reason, CallbackInfo ci) {
        Entity entity = (Entity) (Object) this;
        if (reason.shouldDestroy()) com.timestop.core.rewind.TickRecorder.getInstance().recordEntityRemoval(entity);
    }

    @Inject(method = "isPickable", at = @At("HEAD"), cancellable = true)
    private void onIsPickable(CallbackInfoReturnable<Boolean> cir) {
        Entity entity = (Entity) (Object) this;
        boolean timeActive = entity.level().isClientSide ? ClientTimeStopManager.isTimeStopped() : TimeStopManager.isGlobalTimeStopped();
        if (timeActive) {
            TimeMode mode = entity.level().isClientSide ? ClientTimeStopManager.getCurrentMode() : TimeStopManager.getCurrentMode();
            if ((mode == TimeMode.TIME_STOP || mode == TimeMode.SLOW_MOTION || mode == TimeMode.MATRIX || mode == TimeMode.SUPERHOT || mode == TimeMode.DECELERATION_FIELD || (mode == TimeMode.TIME_PREDICTION || mode == TimeMode.COMBAT_SLOW))
                    && (mode != TimeMode.DECELERATION_FIELD || entity instanceof Projectile p && com.timestop.combat.DecelerationFieldManager.isDecelerated(p))) {
                if (entity instanceof Projectile || entity instanceof FallingBlockEntity || entity instanceof PrimedTnt) {
                    if (entity.onGround()) {
                        return;
                    }
                    cir.setReturnValue(true);
                    return;
                }
            }
        }

        if (entity instanceof Projectile p && !entity.onGround() && com.timestop.combat.DecelerationFieldManager.isDecelerated(p)) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "getPickRadius", at = @At("HEAD"), cancellable = true)
    private void onGetPickRadius(CallbackInfoReturnable<Float> cir) {
        Entity entity = (Entity) (Object) this;
        boolean timeActive = entity.level().isClientSide ? ClientTimeStopManager.isTimeStopped() : TimeStopManager.isGlobalTimeStopped();
        if (timeActive) {
            TimeMode mode = entity.level().isClientSide ? ClientTimeStopManager.getCurrentMode() : TimeStopManager.getCurrentMode();
            if ((mode == TimeMode.TIME_STOP || mode == TimeMode.SLOW_MOTION || mode == TimeMode.MATRIX || mode == TimeMode.SUPERHOT || mode == TimeMode.DECELERATION_FIELD || (mode == TimeMode.TIME_PREDICTION || mode == TimeMode.COMBAT_SLOW))
                    && (mode != TimeMode.DECELERATION_FIELD || entity instanceof Projectile p && com.timestop.combat.DecelerationFieldManager.isDecelerated(p))) {
                if (entity instanceof Projectile) {
                    if (entity.onGround()) {
                        return;
                    }
                    // Generously expand projectile raycast pick radius so hitting moving projectiles in bullet-time is fun & responsive
                    cir.setReturnValue(0.45F);
                    return;
                }
            }
        }

        if (entity instanceof Projectile p && !entity.onGround() && com.timestop.combat.DecelerationFieldManager.isDecelerated(p)) {
            cir.setReturnValue(0.45F);
        }
    }

}
