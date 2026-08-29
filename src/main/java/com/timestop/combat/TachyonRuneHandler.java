package com.timestop.combat;

import com.timestop.core.ClientTimeStopManager;
import com.timestop.core.TimeMode;
import com.timestop.core.TimeStopManager;
import com.timestop.item.rune.RuneType;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import com.timestop.TimeStopMod;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = TimeStopMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
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
            if (!TimeStopManager.isGlobalTimeStopped()) return false;
            TimeMode mode = TimeStopManager.getCurrentMode();
            return mode == TimeMode.SLOW_MOTION || mode == TimeMode.MATRIX;
        }
    }

    @SubscribeEvent
    public static void onBreakSpeed(PlayerEvent.BreakSpeed event) {
        Player player = event.getEntity();
        if (isTachyonActive(player)) {
            // 3.0x accelerated block breaking speed
            event.setNewSpeed(event.getOriginalSpeed() * 3.0F);
        }
    }
}
