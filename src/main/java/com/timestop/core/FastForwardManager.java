package com.timestop.core;

import com.timestop.TimeStopMod;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = TimeStopMod.MOD_ID)
public final class FastForwardManager {
    private FastForwardManager() {}

    public static float localPlayerRate(Player player) {
        if (!player.isAlive()) return 1.0F;
        // Global fast forward already runs both clocks at 100 TPS.
        if (player.level().isClientSide) {
            if (ClientTimeStopManager.isGlobalTimeStopActive()) return 1.0F;
            var bubble = ClientBubbleManager.getDominantBubble(player.getX(),
                    player.getY() + player.getBbHeight() * 0.5, player.getZ());
            return bubble != null && bubble.mode == TimeMode.FAST_FORWARD ? 5.0F : 1.0F;
        }
        if (TimeStopManager.isGlobalTimeStopActive()) return 1.0F;
        var bubble = TemporalBubbleManager.getDominantBubble(player.level().dimension(),
                player.getX(), player.getY() + player.getBbHeight() * 0.5, player.getZ());
        return bubble != null && bubble.getMode() == TimeMode.FAST_FORWARD ? 5.0F : 1.0F;
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onBreakSpeed(PlayerEvent.BreakSpeed event) {
        event.setNewSpeed(event.getNewSpeed() * localPlayerRate(event.getEntity()));
    }
}
