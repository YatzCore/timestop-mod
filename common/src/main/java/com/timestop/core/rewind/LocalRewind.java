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
    private record Job(UUID owner, UUID visual, RewindScope scope, Deque<TickFrame> frames, int duration, Runnable completed,
                       Set<UUID> fixedSubLevels, Set<RewindPlan.BlockKey> fixedCounterparts) {}
    private static final Map<UUID,Double> playbackCredit = new HashMap<>();
    private static final Map<UUID,Job> jobs = new LinkedHashMap<>();

    public static long getActivePlaybackMemoryBytes() {
        if (jobs.isEmpty()) return 0;
        long bytes = 0;
        var retained = new ArrayList<TickFrame>();
        for (Job job : jobs.values()) {
            bytes += 128 + job.frames.size() * 16L + job.fixedSubLevels.size() * 32L + job.fixedCounterparts.size() * 64L;
            retained.addAll(job.frames);
        }
        return bytes + TickRecorder.getInstance().getTimelineBuffer().additionalRetainedBytes(retained);
    }

    public static void enforceMemoryBudget() {
        var buffer = TickRecorder.getInstance().getTimelineBuffer();
        while (!jobs.isEmpty() && buffer.getTotalEstimatedBytes() > buffer.getMaxMemoryBytes()) {
            stop(jobs.values().iterator().next(), "history memory limit reached");
        }
    }

    public static boolean overlaps(RewindScope scope) {
        return jobs.values().stream().anyMatch(j -> j.scope.dimension().equals(scope.dimension())
                && j.scope.center().distanceToSqr(scope.center()) <= Math.pow(j.scope.radius()+scope.radius(),2));
    }
    public static boolean hasActive() { return !jobs.isEmpty(); }
    public static boolean containsSubLevel(UUID bodyId) {
        return jobs.values().stream().anyMatch(job -> job.fixedSubLevels.contains(bodyId));
    }
    public static boolean ownsAny(RewindPlan plan) {
        if (plan.subLevelTargetStates().keySet().stream().anyMatch(LocalRewind::containsSubLevel)) return true;
        var blocks = new HashSet<>(plan.blockTargetStates().keySet());
        blocks.addAll(plan.blockEntityTargetNbts().keySet());
        blocks.addAll(plan.standaloneBeTargetNbts().keySet());
        plan.subLevelBlockDependencies().values().forEach(blocks::addAll);
        return jobs.values().stream().anyMatch(job -> !Collections.disjoint(blocks, job.fixedCounterparts));
    }
    public static boolean isActive(UUID player) { return jobs.containsKey(player); }
    public static boolean contains(Entity entity) {
        if (jobs.isEmpty()) return false;
        var bodyId = com.timestop.compat.TemporalWorlds.bridge().getContainingSubLevelId(entity.level(), entity.blockPosition());
        return (bodyId != null && containsSubLevel(bodyId)) || jobs.values().stream().anyMatch(j -> j.scope.contains(entity));
    }
    public static boolean contains(ResourceKey<Level> dimension, BlockPos pos) {
        if (jobs.isEmpty()) return false;
        var key = new RewindPlan.BlockKey(dimension, pos.asLong());
        if (jobs.values().stream().anyMatch(job -> job.fixedCounterparts.contains(key))) return true;
        var server = com.timestop.platform.Services.PLATFORM.getCurrentServer();
        var level = server == null ? null : server.getLevel(dimension);
        if (level != null) {
            var bodyId = com.timestop.compat.TemporalWorlds.bridge().getContainingSubLevelId(level, pos);
            if (bodyId != null && containsSubLevel(bodyId)) return true;
        }
        return jobs.values().stream().anyMatch(j -> j.scope.contains(dimension,pos));
    }

    public static boolean start(ServerPlayer player, RewindScope scope, int ticks, Runnable completed) {
        if (TickRecorder.getInstance().getTimelineBuffer().isRewinding()) return false;
        var old = TemporalBubbleManager.getPlayerBubble(player.getUUID());
        if (old != null) TemporalBubbleManager.stopBubble(player.serverLevel(),old);
        var recorder=TickRecorder.getInstance(); recorder.finishFrame(player.server);
        var frames=recorder.getTimelineBuffer().getFramesForRewind(Math.max(1,ticks));
        if(frames.isEmpty()) {
            player.displayClientMessage(net.minecraft.network.chat.Component.literal("§c[TimeStop] Rewind buffer is empty. History records as the world runs."),false);
            return false;
        }

        var initialPlan = RewindExecutor.buildPlan(new ArrayList<>(frames));
        var filteredInitial = scope.filter(player.server, initialPlan, player.getUUID());
        Set<UUID> fixedSubLevels = Collections.unmodifiableSet(new HashSet<>(filteredInitial.subLevelTargetStates().keySet()));
        Set<RewindPlan.BlockKey> counterpartSet = new HashSet<>();
        filteredInitial.subLevelBlockDependencies().values().forEach(counterpartSet::addAll);
        Set<RewindPlan.BlockKey> fixedCounterparts = Set.copyOf(counterpartSet);
        try {
            com.timestop.compat.TemporalWorlds.bridge().validateRewind(player.server, filteredInitial);
        } catch (IllegalStateException invalid) {
            player.displayClientMessage(net.minecraft.network.chat.Component.literal("Rewind could not start: " + invalid.getMessage()), true);
            return false;
        }

        for (var job : jobs.values()) {
            if (!Collections.disjoint(job.fixedCounterparts, fixedCounterparts)
                    || fixedCounterparts.stream().anyMatch(key -> job.scope.contains(key.dimension(), BlockPos.of(key.packedPos())))
                    || job.fixedCounterparts.stream().anyMatch(key -> scope.contains(key.dimension(), BlockPos.of(key.packedPos())))) {
                player.displayClientMessage(net.minecraft.network.chat.Component.literal("A rewind already owns an assembly transfer location."), true);
                return false;
            }
            if (job.scope.dimension().equals(scope.dimension())
                    && job.scope.center().distanceToSqr(scope.center()) <= Math.pow(job.scope.radius()+scope.radius(),2)) {
                player.displayClientMessage(net.minecraft.network.chat.Component.literal("A rewind already overlaps this bubble."),true);
                return false;
            }
            for (UUID bodyId : fixedSubLevels) {
                if (job.fixedSubLevels.contains(bodyId)) {
                    player.displayClientMessage(net.minecraft.network.chat.Component.literal("A rewind is already restoring a connected ship in this assembly."), true);
                    return false;
                }
            }
        }

        RewindPlaybackFeedback.started(player, ticks, frames.size(), "bubble");
        Deque<TickFrame> framesDeque = new ArrayDeque<>(frames);
        var job=new Job(player.getUUID(),UUID.randomUUID(),scope,framesDeque,framesDeque.size(),completed,fixedSubLevels,fixedCounterparts);
        jobs.put(player.getUUID(),job);
        if (recorder.getTimelineBuffer().getTotalEstimatedBytes() > recorder.getTimelineBuffer().getMaxMemoryBytes()) {
            jobs.remove(player.getUUID());
            player.displayClientMessage(net.minecraft.network.chat.Component.literal("Rewind could not start within the history memory limit."), true);
            return false;
        }
        sync(job,null);
        return true;
    }
    public static void tick(MinecraftServer server) {
        for(var job:new ArrayList<>(jobs.values())) {
            var owner=server.getPlayerList().getPlayer(job.owner);
            if(owner==null || !owner.isAlive() || !owner.level().dimension().equals(job.scope.dimension())) { stop(job, "owner unavailable or changed dimension"); continue; }
            try {
                double credit=playbackCredit.getOrDefault(job.owner,0.0)+com.timestop.config.TimeStopConfig.COMMON.rewindSpeed.get();
                int steps=(int)credit;playbackCredit.put(job.owner,credit-steps);
                if(steps==0) continue;
                var frames=new ArrayList<TickFrame>();
                while(steps-->0 && !job.frames.isEmpty()) frames.add(job.frames.removeFirst());
                var rawPlan=RewindExecutor.buildPlan(frames);
                var plan=job.scope.filter(server,rawPlan,Set.of(job.owner),job.fixedSubLevels,job.fixedCounterparts); // Preserve initiator and enforce fixed membership
                if (owner != null && com.timestop.item.AbstractWatchItem.isPlayerRewindExempt(owner)) plan = plan.withoutPlayer(job.owner);
                RewindExecutor.applyPlan(server,plan,com.timestop.config.TimeStopConfig.COMMON.rollbackPlayerInventory.get());
                TickRecorder.getInstance().getTimelineBuffer().consumeFrames(frames,plan);
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
        jobs.remove(job.owner);playbackCredit.remove(job.owner);
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
