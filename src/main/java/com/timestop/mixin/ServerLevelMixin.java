package com.timestop.mixin;

import com.timestop.core.TimeMode;
import com.timestop.core.TimeStopManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerLevel.class)
public abstract class ServerLevelMixin {

    @Inject(method = "tickNonPassenger", at = @At("HEAD"), cancellable = true)
    private void onTickNonPassenger(Entity entity, CallbackInfo ci) {
        ServerLevel level = (ServerLevel) (Object) this;
        
        if (com.timestop.core.TemporalBubbleManager.isEntityInStasis(entity)) {
            entity.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
            entity.setOldPosAndRot();
            ci.cancel();
            return;
        }

        // Entity is ticking freely (thawed or outside stasis): immediately discharge any accumulated stasis damage & knockback!
        if (com.timestop.combat.TemporalDamageBuffer.hasRecord(entity.getUUID())) {
            com.timestop.combat.TemporalDamageBuffer.dischargeEntity(level, entity.getUUID());
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
        if (com.timestop.core.TemporalBubbleManager.hasActiveBubbles()) {
            if (com.timestop.core.TemporalBubbleManager.isPositionInStasis(level.dimension(), chunk.getPos().getMiddleBlockX(), 64.0, chunk.getPos().getMiddleBlockZ())) {
                ci.cancel();
            }
        } else if (TimeStopManager.isTimeStopped(level) && TimeStopManager.getCurrentMode() == TimeMode.TIME_STOP) {
            ci.cancel();
        }
    }

    @Inject(method = "advanceWeatherCycle", at = @At("HEAD"), cancellable = true)
    private void onAdvanceWeatherCycle(CallbackInfo ci) {
        ServerLevel level = (ServerLevel) (Object) this;
        // Weather freezes globally only if legacy global time stop is active
        if (!com.timestop.core.TemporalBubbleManager.hasActiveBubbles() && TimeStopManager.isTimeStopped(level) && TimeStopManager.getCurrentMode() == TimeMode.TIME_STOP) {
            ci.cancel();
        }
    }

    @Inject(method = "tickTime", at = @At("HEAD"), cancellable = true)
    private void onTickTime(CallbackInfo ci) {
        ServerLevel level = (ServerLevel) (Object) this;
        // World time freezes globally only if legacy global time stop is active
        if (!com.timestop.core.TemporalBubbleManager.hasActiveBubbles() && TimeStopManager.isTimeStopped(level) && TimeStopManager.getCurrentMode() == TimeMode.TIME_STOP) {
            ci.cancel();
            return;
        }

        // In FAST_FORWARD (100 TPS), rate-limit daylight cycle to normal 20 TPS so the sun doesn't fly across the sky
        if (TimeStopManager.getServerTickMs() < 50L) {
            long ratio = Math.max(1L, 50L / TimeStopManager.getServerTickMs());
            if (level.getGameTime() % ratio != 0) {
                ci.cancel();
            }
        }
    }
}
