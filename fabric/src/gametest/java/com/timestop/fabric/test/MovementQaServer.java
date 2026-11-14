package com.timestop.fabric.test;

import com.timestop.core.PersonalTime;
import com.timestop.item.AbstractWatchItem;
import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Opt-in dedicated-server side of MovementQa: logs each player's synced state whenever it changes. */
public class MovementQaServer implements DedicatedServerModInitializer {
    private final Map<UUID, String> last = new HashMap<>();
    @Override public void onInitializeServer() {
        if (!Boolean.getBoolean("timestop.movementQa")) return;
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            // The QA server stops itself once its test client has come and gone.
            if (!last.isEmpty() && server.getPlayerList().getPlayers().isEmpty()) { last.clear(); server.halt(false); return; }
            for (var player : server.getPlayerList().getPlayers()) {
                String state = "casual=" + PersonalTime.isCasual(player) + " heldMode=" + AbstractWatchItem.getMode(player.getMainHandItem())
                        + " held=" + player.getMainHandItem().getItem() + " personalRate=" + PersonalTime.extraRate(player);
                if (!state.equals(last.put(player.getUUID(), state))) com.timestop.TimeStopMod.LOGGER.info("MOVEMENT_QA_SERVER {} {}", player.getGameProfile().getName(), state);
            }
        });
    }
}
