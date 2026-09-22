package com.timestop.core.rewind;

import com.timestop.network.ModMessages;
import com.timestop.network.RewindBlocksPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.core.BlockPos;
import java.util.Comparator;

public final class RewindBlockAnimations {
    private RewindBlockAnimations() {}
    public static void send(MinecraftServer server, RewindPlan plan) {
        for (var level : server.getAllLevels()) {
            if (level.players().isEmpty()) continue;
            var candidates = plan.blockTargetStates().entrySet().stream()
                    .filter(e -> e.getKey().dimension().equals(level.dimension()) && !e.getValue().isAir() && !e.getValue().hasBlockEntity())
                    .filter(e -> level.hasChunkAt(BlockPos.of(e.getKey().packedPos())) && level.getBlockState(BlockPos.of(e.getKey().packedPos())).isAir())
                    .map(e -> new RewindBlocksPacket.Entry(BlockPos.of(e.getKey().packedPos()), e.getValue())).toList();
            for (var player : level.players()) {
                var nearby = candidates.stream().filter(e -> e.pos().distToCenterSqr(player.position()) <= 24 * 24)
                        .sorted(Comparator.comparingDouble(e -> e.pos().distToCenterSqr(player.position())))
                        .limit(BlockRebuildMotion.MAX_BLOCKS).toList();
                // Arrives before vanilla block updates, so the rebuilt mesh starts with the animated cells hidden.
                if (!nearby.isEmpty()) ModMessages.sendToPlayer(new RewindBlocksPacket(level.dimension().location(), nearby), player);
            }
        }
    }
}
