package com.timestop.core.rewind;

import com.timestop.core.*;
import com.timestop.core.rewind.data.TickFrame;
import com.timestop.item.WatchTier;
import com.timestop.network.*;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import java.util.*;

/** Local playback leaves the rest of the server ticking and recording normally. */
public final class LocalRewind {
    private record Job(UUID owner, UUID visual, RewindScope scope, Deque<TickFrame> frames, int duration, Runnable completed) {}
    private static final Map<UUID,Job> jobs = new LinkedHashMap<>();

    public static long getActivePlaybackMemoryBytes() {
        long bytes = 0;
        for (Job job : jobs.values()) {
            bytes += 128 + job.frames.size() * 16L;
        }
        return bytes;
    }

    public static boolean overlaps(RewindScope scope) {
        return jobs.values().stream().anyMatch(j -> j.scope.dimension().equals(scope.dimension())
                && j.scope.center().distanceToSqr(scope.center()) <= Math.pow(j.scope.radius()+scope.radius(),2));
    }
    public static boolean hasActive() { return !jobs.isEmpty(); }
    public static boolean isActive(UUID player) { return jobs.containsKey(player); }
    public static boolean contains(Entity entity) { return jobs.values().stream().anyMatch(j -> j.scope.contains(entity)); }
    public static boolean contains(ResourceKey<Level> dimension, BlockPos pos) { return jobs.values().stream().anyMatch(j -> j.scope.contains(dimension,pos)); }

    public static boolean start(ServerPlayer player, RewindScope scope, int ticks, Runnable completed) {
        if (TickRecorder.getInstance().getTimelineBuffer().isRewinding()) return false;
        for (var job : jobs.values()) if (job.scope.dimension().equals(scope.dimension())
                && job.scope.center().distanceToSqr(scope.center()) <= Math.pow(job.scope.radius()+scope.radius(),2)) {
            player.displayClientMessage(net.minecraft.network.chat.Component.literal("A rewind already overlaps this bubble."),true);
            return false;
        }
        var old = TemporalBubbleManager.getPlayerBubble(player.getUUID());
        if (old != null) TemporalBubbleManager.stopBubble(player.serverLevel(),old);
        var recorder=TickRecorder.getInstance(); recorder.finishFrame(player.server);
        var frames=recorder.getTimelineBuffer().getFramesForRewind(Math.max(1,ticks));
        if(frames.isEmpty()) {
            player.displayClientMessage(net.minecraft.network.chat.Component.literal("§c[TimeStop] Rewind buffer is empty. History records as the world runs."),false);
            return false;
        }
        RewindPlaybackFeedback.started(player, ticks, frames.size(), "bubble");
        Deque<TickFrame> framesDeque = new ArrayDeque<>(frames);
        var job=new Job(player.getUUID(),UUID.randomUUID(),scope,framesDeque,framesDeque.size(),completed);
        jobs.put(player.getUUID(),job);
        sync(job,null);
        return true;
    }
    public static void tick(MinecraftServer server) {
        for(var job:new ArrayList<>(jobs.values())) {
            var owner=server.getPlayerList().getPlayer(job.owner);
            if(owner==null || !owner.isAlive() || !owner.level().dimension().equals(job.scope.dimension())) { stop(job, "owner unavailable or changed dimension"); continue; }
            try {
                var frame=job.frames.removeFirst();
                var rawPlan=RewindExecutor.buildPlan(List.of(frame));
                var plan=job.scope.filter(server,rawPlan,job.owner); // Preserve initiator so owner doesn't freeze at bubble edge
                RewindExecutor.applyPlan(server,plan,com.timestop.config.TimeStopConfig.COMMON.rollbackPlayerInventory.get());
                TickRecorder.getInstance().getTimelineBuffer().consumeFrames(List.of(frame),plan);
                if(job.frames.isEmpty()) {
                    owner.displayClientMessage(net.minecraft.network.chat.Component.literal("§e[TimeStop] Rewind complete: all recorded history restored."), true);
                    stop(job, "recorded history completed");
                }
                else if(job.frames.size()%20==0) {
                    owner.displayClientMessage(net.minecraft.network.chat.Component.literal(String.format(java.util.Locale.ROOT,
                            "Rewind: %.1fs remaining", job.frames.size()/20.0)),true);
                    sync(job,null);
                }
                else if(job.frames.size()%4==0) sync(job,null);
            } catch(Exception error) {
                org.slf4j.LoggerFactory.getLogger("TimeStopRewind").error("Local rewind failed",error);
                stop(job, "playback error; see log");
            }
        }
    }
    public static boolean cancel(UUID owner) { var job=jobs.get(owner); if(job==null)return false; stop(job, "cancelled"); return true; }
    public static void clear() { for(var job:new ArrayList<>(jobs.values()))stop(job, "cleared"); }
    private static void stop(Job job, String reason) {
        org.slf4j.LoggerFactory.getLogger("TimeStopRewind").info("Bubble rewind ended: owner={}, played={} ticks, remaining={} ticks, reason={}",
                job.owner, job.duration-job.frames.size(), job.frames.size(), reason);
        jobs.remove(job.owner);
        ModMessages.sendToClients(TemporalBubbleSyncPacket.remove(job.visual));
        job.completed.run();
    }
    public static void syncTo(ServerPlayer player) { for(var job:jobs.values())sync(job,player); }
    private static void sync(Job job,ServerPlayer player) {
        var c=job.scope.center();
        var packet=new TemporalBubbleSyncPacket(TemporalBubbleSyncPacket.Action.CREATE_OR_UPDATE,job.visual,job.owner,
                job.scope.dimension().location().toString(),c.x,c.y,c.z,job.scope.radius(),TimeMode.REWIND,
                job.frames.size(),job.duration,WatchTier.CREATIVE,Set.of());
        if(player==null)ModMessages.sendToClients(packet);else ModMessages.sendToPlayer(packet,player);
    }
}

