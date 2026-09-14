package com.timestop.mixin;

import com.timestop.core.TimeMode;
import com.timestop.core.TimeStopManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerLevel.class)
public abstract class ServerLevelMixin {
    @Unique
    private final java.util.Map<Entity, Float> timestop$tickProgress = new java.util.WeakHashMap<>();
    @Unique
    private boolean timestop$extraTick;

    @Inject(method = "tickNonPassenger", at = @At("HEAD"), cancellable = true)
    private void onTickNonPassenger(Entity entity, CallbackInfo ci) {
        ServerLevel level = (ServerLevel) (Object) this;
        if (entity instanceof net.minecraft.world.entity.projectile.Projectile projectile) {
            com.timestop.combat.KineticPalmManager.interceptIncoming(projectile);
            com.timestop.combat.OrbitalProjectileManager.interceptIncoming(projectile);
            if (com.timestop.platform.EntityDataHelper.getPersistentData(projectile).getBoolean("KineticPalmCaptured")) {
                com.timestop.combat.KineticPalmManager.tickCaptured(projectile);
                ci.cancel();
                return;
            }
        }
        if (entity instanceof net.minecraft.world.entity.projectile.Projectile
                && (com.timestop.platform.EntityDataHelper.getPersistentData(entity).getBoolean("KineticPalmCaptured")
                    || com.timestop.platform.EntityDataHelper.getPersistentData(entity).getBoolean("InStasisOrbit"))) {
            entity.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
            entity.setOldPosAndRot();
            ci.cancel();
            return;
        }
        
        if (com.timestop.core.TemporalBubbleManager.isEntityInStasis(entity)) {
            if (entity instanceof net.minecraft.world.entity.projectile.Projectile p) {
                net.minecraft.world.phys.Vec3 vel = p.getDeltaMovement();
                if (vel.lengthSqr() > 1.0E-5 && !TimeStopManager.isProjectileSuspended(p)) {
                    TimeStopManager.registerSuspendedProjectile(p, vel);
                }
            }
            entity.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
            entity.setOldPosAndRot();
            ci.cancel();
            return;
        }

        if (!timestop$extraTick) {
            float rate = timestop$localTickRate(entity);
            if (rate < 1.0F) {
                float progress = timestop$tickProgress.getOrDefault(entity, 0.0F) + rate;
                timestop$tickProgress.put(entity, progress >= 1.0F ? progress - 1.0F : progress);
                if (progress < 1.0F) {
                    entity.setOldPosAndRot();
                    ci.cancel();
                    return;
                }
            } else {
                timestop$tickProgress.remove(entity);
            }
        }

        // Entity is ticking freely (thawed or outside stasis): immediately discharge any accumulated stasis damage & knockback!
        if (com.timestop.combat.TemporalDamageBuffer.hasRecord(entity.getUUID())) {
            com.timestop.combat.TemporalDamageBuffer.dischargeEntity(level, entity.getUUID());
        }
    }

    @Unique
    private float timestop$localTickRate(Entity entity) {
        if (TimeStopManager.getServerTickMs() != 50L) return 1.0F;
        if (TimeStopManager.isGlobalTimeStopActive()) return 1.0F;
        var bubble = com.timestop.core.TemporalBubbleManager.getDominantBubble(entity.level().dimension(),
                entity.getX(), entity.getY() + entity.getBbHeight() * 0.5, entity.getZ());
        if (bubble == null) return 1.0F;
        TimeMode bMode = bubble.getMode();
        if (bMode == TimeMode.SLOW_MOTION || bMode == TimeMode.MATRIX || bMode == TimeMode.SUPERHOT) {
            return 1.0F;
        }
        return bubble.getTimeDilationFactor(entity);
    }

    @Inject(method = "tickNonPassenger", at = @At("TAIL"))
    private void timestop$fastForwardEntity(Entity entity, CallbackInfo ci) {
        if (timestop$extraTick) return;
        int extraTicks = (int) timestop$localTickRate(entity) - 1;
        if (extraTicks <= 0) return;
        timestop$extraTick = true;
        try {
            for (int i = 0; i < extraTicks && entity.isAlive(); i++) {
                ((ServerLevel) (Object) this).tickNonPassenger(entity);
            }
        } finally {
            timestop$extraTick = false;
        }
    }

    @Inject(method = "tickPassenger", at = @At("HEAD"), cancellable = true)
    private void onTickPassenger(Entity vehicle, Entity passenger, CallbackInfo ci) {
        ServerLevel level = (ServerLevel) (Object) this;
        
        if (com.timestop.core.TemporalBubbleManager.isEntityInStasis(passenger)) {
            passenger.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
            passenger.setOldPosAndRot();
            ci.cancel();
            return;
        }

        if (com.timestop.combat.TemporalDamageBuffer.hasRecord(passenger.getUUID())) {
            com.timestop.combat.TemporalDamageBuffer.dischargeEntity(level, passenger.getUUID());
        }
    }

    @Inject(method = "tickChunk", at = @At("HEAD"), cancellable = true)
    private void onTickChunk(LevelChunk chunk, int randomTickSpeed, CallbackInfo ci) {
        ServerLevel level = (ServerLevel) (Object) this;
        if (TimeStopManager.isGlobalTimeStopActive() && TimeStopManager.getCurrentMode() == TimeMode.TIME_STOP) {
            ci.cancel();
            return;
        }
        if (com.timestop.core.TemporalBubbleManager.hasActiveBubbles()) {
            if (com.timestop.core.TemporalBubbleManager.doesBubbleIntersectChunk(level.dimension(), chunk.getPos().x, chunk.getPos().z, level.getMinBuildHeight(), level.getMaxBuildHeight())) {
                ci.cancel();
            }
        }
    }

    @Inject(method = "advanceWeatherCycle", at = @At("HEAD"), cancellable = true)
    private void onAdvanceWeatherCycle(CallbackInfo ci) {
        ServerLevel level = (ServerLevel) (Object) this;
        // Weather freezes globally only if global time stop is active
        if (TimeStopManager.isGlobalTimeStopActive() && TimeStopManager.getCurrentMode() == TimeMode.TIME_STOP) {
            ci.cancel();
        }
    }

    @Inject(method = "tickTime", at = @At("HEAD"), cancellable = true)
    private void onTickTime(CallbackInfo ci) {
        ServerLevel level = (ServerLevel) (Object) this;
        // World time freezes globally only if global time stop is active
        if (TimeStopManager.isGlobalTimeStopActive() && TimeStopManager.getCurrentMode() == TimeMode.TIME_STOP) {
            ci.cancel();
            return;
        }

    }
}
