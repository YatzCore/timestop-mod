package com.timestop.prediction;

import com.timestop.core.*;
import com.timestop.config.TimeStopConfig;
import net.minecraft.world.entity.Entity;

/** Prediction slows entity time without slowing the server or network clock. */
public final class PredictionTime {
    private PredictionTime() {}
    public static double rate(Entity entity) {
        if (entity.level().isClientSide) {
            if (ClientTimeStopManager.isGlobalTimeStopActive()) {
                if (ClientTimeStopManager.getCurrentMode() == TimeMode.COMBAT_SLOW) return combatRate(entity);
                if (ClientTimeStopManager.getCurrentMode() != TimeMode.TIME_PREDICTION) return 1;
                return ClientTimeStopManager.isEntityExempt(entity) ? 1 : TimeStopConfig.COMMON.predictionRate.get();
            }
            var bubble = ClientBubbleManager.getDominantBubble(entity.getX(), entity.getY() + entity.getBbHeight() * .5, entity.getZ());
            return bubble != null && (bubble.mode == TimeMode.TIME_PREDICTION || bubble.mode == TimeMode.COMBAT_SLOW) ? bubble.getTimeDilationFactor(entity) : 1;
        }
        if (TimeStopManager.isGlobalTimeStopActive()) {
            if (TimeStopManager.getCurrentMode() == TimeMode.COMBAT_SLOW) return combatRate(entity);
            if (TimeStopManager.getCurrentMode() != TimeMode.TIME_PREDICTION) return 1;
            return TimeStopManager.isEntityExempt(entity) ? 1 : TimeStopConfig.COMMON.predictionRate.get();
        }
        var bubble = TemporalBubbleManager.getDominantBubble(entity.level().dimension(), entity.getX(), entity.getY() + entity.getBbHeight() * .5, entity.getZ());
        return bubble != null && (bubble.getMode() == TimeMode.TIME_PREDICTION || bubble.getMode() == TimeMode.COMBAT_SLOW) ? bubble.getTimeDilationFactor(entity) : 1;
    }
    private static double combatRate(Entity entity) {
        return entity instanceof net.minecraft.world.entity.Mob || entity instanceof net.minecraft.world.entity.projectile.Projectile
                ? TimeStopConfig.COMMON.slowMotionRate.get() : 1;
    }
    public static boolean controlsLivingMotion(Entity entity) {
        // Server players already send the movement integrated by their client.
        if (entity instanceof net.minecraft.world.entity.player.Player)
            return entity.level().isClientSide && entity == net.minecraft.client.Minecraft.getInstance().player;
        return !entity.level().isClientSide;
    }
}
