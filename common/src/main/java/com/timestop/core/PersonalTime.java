package com.timestop.core;

import com.timestop.config.TimeStopConfig;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.world.entity.player.Player;

/**
 * A player's personal clock: how much faster their own processes (item use, attack recharge,
 * cooldowns, effects, hunger/healing, breath, burning, freezing, damage immunity, mining) must run
 * on this side, on top of whatever this side's own tick rate already provides.
 *
 * <ul>
 *   <li>Self Acceleration: both clocks run at 20 TPS, so both sides add the full rate.</li>
 *   <li>Your own moving Fast Forward field: the client clock already runs fast, the server clock
 *       does not, so only the server adds the rate.</li>
 *   <li>Global Fast Forward: both clocks already run fast; nothing is added.</li>
 * </ul>
 */
public final class PersonalTime {
    private PersonalTime() {}

    /** Players (server side) who chose Casual mode: harmful effects keep their normal speed. */
    private static final Set<UUID> CASUAL = ConcurrentHashMap.newKeySet();

    public static double extraRate(Player player) {
        double self = SelfAcceleration.rate(player);
        if (self > 1) return self;
        return player.level().isClientSide ? 1 : localFastForwardRate(player);
    }

    /** Server: rate of a moving (watch) Fast Forward field the player is in, unless Fast Forward is global. */
    public static double localFastForwardRate(Player player) {
        if (!player.isAlive() || player.isSpectator() || player.level().isClientSide) return 1;
        if (TimeStopManager.isGlobalTimeStopActive()) return 1;
        var bubble = TemporalBubbleManager.getDominantBubble(player.level().dimension(),
                player.getX(), player.getY() + player.getBbHeight() * 0.5, player.getZ());
        if (bubble == null || bubble.isStationary() || bubble.getMode() != TimeMode.FAST_FORWARD) return 1;
        double rate = TimeStopConfig.COMMON.fastForwardRate.get();
        if (!Double.isFinite(rate)) return 1;
        // Match the client clock in the field, which is capped at 100 TPS (see ClientTimeStopManager.getClientTickMs).
        return 50.0 / Math.max(10.0, 50.0 / TimeStopConfig.clampFastForward(rate));
    }

    /** Casual mode: harmful processes (drowning, burning, freezing, harmful effects, starvation, damage immunity) stay normal. */
    public static boolean isCasual(Player player) {
        if (player.level().isClientSide) return player.isLocalPlayer() && TimeStopConfig.CLIENT.casualAcceleration.get();
        return CASUAL.contains(player.getUUID());
    }

    public static void setCasual(UUID player, boolean casual) {
        if (casual) CASUAL.add(player); else CASUAL.remove(player);
    }

    /**
     * Extra downward velocity for one tick of accelerated falling. Applies only while already falling,
     * so jump height is unchanged; capped so the speed stays below the server's movement check.
     */
    public static double acceleratedFall(double dy, double gravity, double rate) {
        if (rate <= 1 || dy >= 0 || dy <= MAX_FALL_SPEED) return dy;
        return Math.max(MAX_FALL_SPEED, dy - gravity * (rate - 1) * 0.98);
    }

    public static final double MAX_FALL_SPEED = -8.0;
}
