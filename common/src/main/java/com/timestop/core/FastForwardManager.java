package com.timestop.core;

import net.minecraft.world.entity.player.Player;

public final class FastForwardManager {
    private FastForwardManager() {}

    public static float localPlayerRate(Player player) {
        if (!player.isAlive()) return 1.0F;
        // Global fast forward already runs both clocks at 100 TPS.
        if (player.level().isClientSide) {
            if (ClientTimeStopManager.isGlobalTimeStopActive()) return 1.0F;
            var bubble = ClientBubbleManager.getDominantBubble(player.getX(),
                    player.getY() + player.getBbHeight() * 0.5, player.getZ());
            return bubble != null && bubble.mode == TimeMode.FAST_FORWARD ? com.timestop.config.TimeStopConfig.COMMON.fastForwardRate.get().floatValue() : 1.0F;
        }
        if (TimeStopManager.isGlobalTimeStopActive()) return 1.0F;
        var bubble = TemporalBubbleManager.getDominantBubble(player.level().dimension(),
                player.getX(), player.getY() + player.getBbHeight() * 0.5, player.getZ());
        return bubble != null && bubble.getMode() == TimeMode.FAST_FORWARD ? com.timestop.config.TimeStopConfig.COMMON.fastForwardRate.get().floatValue() : 1.0F;
    }

    public static float modifyBreakSpeed(Player player, float speed) {
        return speed * localPlayerRate(player);
    }
}
