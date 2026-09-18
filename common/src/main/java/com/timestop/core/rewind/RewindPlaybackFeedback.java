package com.timestop.core.rewind;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import javax.annotation.Nullable;
import java.util.Locale;

/** Report real playable history instead of silently shortening the requested duration. */
public final class RewindPlaybackFeedback {
    private RewindPlaybackFeedback() {}
    public static void started(@Nullable Player player, int requestedTicks, int availableTicks, String scope) {
        int playable = requestedTicks <= 0 ? availableTicks : Math.min(requestedTicks, availableTicks);
        var buffer = TickRecorder.getInstance().getTimelineBuffer();
        long usedBytes = buffer.getTotalEstimatedBytes();
        long maxBytes = buffer.getMaxMemoryBytes();
        org.slf4j.LoggerFactory.getLogger("TimeStopRewind").info(
                "Rewind started: scope={}, requested={} ticks, playable={} ticks, retained={} ticks, memoryEvicted={} ticks, memoryUsed={} KB, memoryCap={} KB",
                scope, requestedTicks, playable, buffer.getFrameCount(), buffer.getMemoryEvictedFrames(),
                usedBytes / 1024, maxBytes / 1024);
        if (player != null) {
            String message = String.format(Locale.ROOT, "Rewinding %.1fs of recorded history (requested %.1fs).", playable/20.0, requestedTicks/20.0);
            if (playable < requestedTicks) message += buffer.getMemoryEvictedFrames() > 0
                    ? String.format(Locale.ROOT, " Some history has been trimmed by the memory budget (%d MB used / %d MB cap).",
                        (usedBytes + 1024 * 1024 - 1) / (1024 * 1024), maxBytes / (1024 * 1024))
                    : " The buffer is still filling or earlier history was already rewound.";
            player.displayClientMessage(Component.literal(message), false);
        }
    }
}

