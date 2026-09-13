package com.timestop.mixin;

import com.timestop.core.ClientTimeStopManager;
import com.timestop.core.TimeMode;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientLevel.class)
public abstract class ClientLevelMixin {
    @Unique
    private final java.util.Map<Entity, Float> timestop$projectileProgress = new java.util.WeakHashMap<>();
    @Unique
    private boolean timestop$extraProjectileTick;

    @Unique
    private float timestop$projectileRate(Entity entity) {
        if (!(entity instanceof net.minecraft.world.entity.projectile.Projectile)
                || ClientTimeStopManager.isGlobalTimeStopActive()) return 1.0F;
        var bubble = com.timestop.core.ClientBubbleManager.getDominantBubble(
                entity.getX(), entity.getY() + entity.getBbHeight() * 0.5, entity.getZ());
        return bubble == null ? 1.0F : bubble.getTimeDilationFactor(entity);
    }

    @Inject(method = "tickNonPassenger", at = @At("HEAD"), cancellable = true)
    private void onTickNonPassenger(Entity entity, CallbackInfo ci) {
        if (entity.getPersistentData().getBoolean("KineticPalmCaptured")) {
            entity.setOldPosAndRot();
            var data = entity.getPersistentData();
            var target = new net.minecraft.world.phys.Vec3(data.getDouble("NeoTargetX"),
                    data.getDouble("NeoTargetY"), data.getDouble("NeoTargetZ"));
            entity.setPos(entity.position().lerp(target, 0.65));
            entity.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
            data.putInt("NeoWakeAge", data.getInt("NeoWakeAge") + 1);
            ci.cancel();
            return;
        }
        if (entity instanceof net.minecraft.world.entity.projectile.Projectile
                && (entity.getPersistentData().getBoolean("KineticPalmCaptured")
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
            return;
        }
        if (!timestop$extraProjectileTick) {
            float rate = timestop$projectileRate(entity);
            if (rate < 1.0F) {
                float progress = timestop$projectileProgress.getOrDefault(entity, 0.0F) + rate;
                timestop$projectileProgress.put(entity, progress >= 1.0F ? progress - 1.0F : progress);
                if (progress < 1.0F) {
                    entity.setOldPosAndRot();
                    ci.cancel();
                }
            } else {
                timestop$projectileProgress.remove(entity);
            }
        }
    }

    @Inject(method = "tickNonPassenger", at = @At("TAIL"))
    private void timestop$fastProjectiles(Entity entity, CallbackInfo ci) {
        if (timestop$extraProjectileTick) return;
        int extraTicks = (int) timestop$projectileRate(entity) - 1;
        if (extraTicks <= 0) return;
        double oldX = entity.xo, oldY = entity.yo, oldZ = entity.zo;
        timestop$extraProjectileTick = true;
        try {
            for (int i = 0; i < extraTicks && entity.isAlive(); i++) {
                ((ClientLevel) (Object) this).tickNonPassenger(entity);
            }
        } finally {
            timestop$extraProjectileTick = false;
            // Render the complete frame's movement, not only the last sub-tick.
            entity.xo = entity.xOld = oldX;
            entity.yo = entity.yOld = oldY;
            entity.zo = entity.zOld = oldZ;
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
