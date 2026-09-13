package com.timestop.mixin;

import com.timestop.core.ClientTimeStopManager;
import com.timestop.core.TimeMode;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientLevel.class)
public abstract class ClientLevelMixin {

    @Inject(method = "tickNonPassenger", at = @At("HEAD"), cancellable = true)
    private void onTickNonPassenger(Entity entity, CallbackInfo ci) {
        if (com.timestop.platform.EntityDataHelper.getPersistentData(entity).getBoolean("KineticPalmCaptured")) {
            entity.setOldPosAndRot();
            var data = com.timestop.platform.EntityDataHelper.getPersistentData(entity);
            var target = new net.minecraft.world.phys.Vec3(data.getDouble("NeoTargetX"),
                    data.getDouble("NeoTargetY"), data.getDouble("NeoTargetZ"));
            entity.setPos(entity.position().lerp(target, 0.65));
            entity.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
            data.putInt("NeoWakeAge", data.getInt("NeoWakeAge") + 1);
            ci.cancel();
            return;
        }
        if (entity instanceof net.minecraft.world.entity.projectile.Projectile
                && (com.timestop.platform.EntityDataHelper.getPersistentData(entity).getBoolean("KineticPalmCaptured")
                    || com.timestop.client.ClientOrbitalHandler.isOrbiting(entity.getId()))) {
            entity.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
            entity.setOldPosAndRot();
            ci.cancel();
            return;
        }
        if (com.timestop.core.ClientBubbleManager.hasActiveBubbles()) {
            com.timestop.core.ClientBubbleManager.ClientBubble bubble = com.timestop.core.ClientBubbleManager.getDominantBubble(entity.getX(), entity.getY() + entity.getBbHeight() * 0.5, entity.getZ());
            if (bubble != null) {
                if (bubble.mode == TimeMode.TIME_STOP && !bubble.canEntityAct(entity)) {
                    entity.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
                    entity.setOldPosAndRot();
                    ci.cancel();
                    return;
                }
            } else if (!com.timestop.core.ClientTimeStopManager.isGlobalTimeStopActive()) {
                return;
            }
        }

        if (com.timestop.core.ClientTimeStopManager.isGlobalTimeStopActive() 
                && com.timestop.core.ClientTimeStopManager.getCurrentMode() == TimeMode.TIME_STOP 
                && !com.timestop.core.ClientTimeStopManager.isEntityExempt(entity)) {
            entity.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
            entity.setOldPosAndRot();
            ci.cancel();
        }
    }

    @Inject(method = "tickPassenger", at = @At("HEAD"), cancellable = true)
    private void onTickPassenger(Entity vehicle, Entity passenger, CallbackInfo ci) {
        if (com.timestop.core.ClientBubbleManager.hasActiveBubbles()) {
            com.timestop.core.ClientBubbleManager.ClientBubble bubble = com.timestop.core.ClientBubbleManager.getDominantBubble(passenger.getX(), passenger.getY() + passenger.getBbHeight() * 0.5, passenger.getZ());
            if (bubble != null) {
                if (bubble.mode == TimeMode.TIME_STOP && !bubble.canEntityAct(passenger)) {
                    passenger.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
                    passenger.setOldPosAndRot();
                    ci.cancel();
                    return;
                }
            } else if (!com.timestop.core.ClientTimeStopManager.isGlobalTimeStopActive()) {
                return;
            }
        }

        if (com.timestop.core.ClientTimeStopManager.isGlobalTimeStopActive() 
                && com.timestop.core.ClientTimeStopManager.getCurrentMode() == TimeMode.TIME_STOP 
                && !com.timestop.core.ClientTimeStopManager.isEntityExempt(passenger)) {
            passenger.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
            passenger.setOldPosAndRot();
            ci.cancel();
        }
    }

    @Inject(method = "tickTime", at = @At("HEAD"), cancellable = true)
    private void onTickTime(CallbackInfo ci) {
        if (ClientTimeStopManager.isGlobalTimeStopActive() && ClientTimeStopManager.getCurrentMode() == TimeMode.TIME_STOP) {
            ci.cancel(); // Freeze sun/moon progression in global TIME_STOP
            return;
        }

    }

    @Inject(method = "animateTick", at = @At("HEAD"), cancellable = true)
    private void onAnimateTick(int posX, int posY, int posZ, CallbackInfo ci) {
        if (com.timestop.core.ClientBubbleManager.hasActiveBubbles()) {
            if (com.timestop.core.ClientBubbleManager.isPositionInStasis(posX + 0.5, posY + 0.5, posZ + 0.5)) {
                ci.cancel();
                return;
            }
        }
        if (ClientTimeStopManager.isGlobalTimeStopActive() && ClientTimeStopManager.getCurrentMode() == TimeMode.TIME_STOP) {
            ci.cancel();
        }
    }
}
