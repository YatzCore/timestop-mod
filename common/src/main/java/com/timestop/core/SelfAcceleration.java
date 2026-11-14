package com.timestop.core;

import com.timestop.config.TimeStopConfig;
import net.minecraft.world.entity.player.Player;

/**
 * Self Acceleration runs the owner's personal clock at the Fast Forward rate: movement, mining,
 * use/place delays, item use, attack recharge, cooldowns, effects and hunger/healing.
 * World clocks, gravity and other entities stay native.
 */
public final class SelfAcceleration {
    private SelfAcceleration() {}

    /** Mining speed on the personal clock (Self Acceleration, or the server half of your own Fast Forward). */
    public static float modifyBreakSpeed(Player player, float speed) {
        double rate = PersonalTime.extraRate(player);
        return rate > 1 ? (float) (speed * rate) : speed;
    }
    public static double rate(Player player) {
        if (!player.isAlive() || player.isSpectator() || player.isPassenger()) return 1;
        boolean active;
        if (player.level().isClientSide) {
            if (ClientTimeStopManager.isGlobalTimeStopActive()) {
                active = ClientTimeStopManager.getCurrentMode()==TimeMode.SELF_ACCELERATION
                        && player.getUUID().equals(ClientTimeStopManager.getInitiatorUuid());
            } else {
                var bubble=ClientBubbleManager.getDominantBubble(player.getX(),player.getY()+player.getBbHeight()*.5,player.getZ());
                active=bubble!=null && !bubble.stationary && bubble.mode==TimeMode.SELF_ACCELERATION
                        && player.getUUID().equals(bubble.ownerUuid);
            }
        } else if (TimeStopManager.isGlobalTimeStopActive()) {
            active=TimeStopManager.getCurrentMode()==TimeMode.SELF_ACCELERATION
                    && player.getUUID().equals(TimeStopManager.getInitiatorUuid());
        } else {
            var bubble=TemporalBubbleManager.getDominantBubble(player.level().dimension(),player.getX(),player.getY()+player.getBbHeight()*.5,player.getZ());
            active=bubble!=null && !bubble.isStationary() && bubble.getMode()==TimeMode.SELF_ACCELERATION
                    && player.getUUID().equals(bubble.getOwnerUuid());
        }
        double speed=TimeStopConfig.COMMON.fastForwardRate.get();
        return active && Double.isFinite(speed) ? TimeStopConfig.clampFastForward(speed) : 1;
    }
}
