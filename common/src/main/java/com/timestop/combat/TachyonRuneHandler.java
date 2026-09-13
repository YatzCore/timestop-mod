package com.timestop.combat;

import com.timestop.core.ClientTimeStopManager;
import com.timestop.core.TimeMode;
import com.timestop.core.TimeStopManager;
import com.timestop.item.rune.RuneType;
import net.minecraft.world.entity.player.Player;

public class TachyonRuneHandler {

    /**
     * Strictly checks if the Tachyon Rune is active:
     * - Player must have RuneType.TACHYON socketed
     * - Current active time mode must be SLOW_MOTION or MATRIX
     */
    public static boolean isTachyonActive(Player player) {
        if (player == null) return false;
        RuneType rune = RuneManager.getSocketedRuneType(player);
        if (rune != RuneType.TACHYON) return false;

        if (player.level().isClientSide) {
            if (!ClientTimeStopManager.isTimeStopped()) return false;
            TimeMode mode = ClientTimeStopManager.getCurrentMode();
            return mode == TimeMode.SLOW_MOTION || mode == TimeMode.MATRIX;
        } else {
            com.timestop.core.TemporalBubble bubble = com.timestop.core.TemporalBubbleManager.getDominantBubble(
                    player.level().dimension(), player.position());
            if (!TimeStopManager.isGlobalTimeStopActive() && bubble == null) return false;
            TimeMode mode = TimeStopManager.isGlobalTimeStopActive() ? TimeStopManager.getCurrentMode() : bubble.getMode();
            return mode == TimeMode.SLOW_MOTION || mode == TimeMode.MATRIX;
        }
    }

    public static float modifyBreakSpeed(Player player, float originalSpeed) {
        if (isTachyonActive(player)) {
            return originalSpeed * 3.0F;
        }
        return originalSpeed;
    }
}