package com.timestop.core.rewind.data;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.storage.ServerLevelData;

import java.util.*;

/**
 * Represents the complete set of world, entity, player, and environmental deltas for a single tick.
 */
public class TickFrame {
    public record ExplosionMoment(net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension,
                                  double x, double y, double z, float radius) {}
    private final List<ExplosionMoment> explosions = new ArrayList<>();
    public List<ExplosionMoment> getExplosions() { return Collections.unmodifiableList(explosions); }
    public void addExplosion(ExplosionMoment explosion) {
        if (!sealed && explosions.size() < 32) {
            explosions.add(explosion);
            estimatedMemoryBytes += 64;
        }
    }

    private Object historyIdentity = new Object();
    public boolean sameInterval(TickFrame other) { return other != null && historyIdentity == other.historyIdentity; }
    private final long gameTime;
    private long dayTime;
    private boolean raining;
    private boolean thundering;
    private int rainTime;
    private int thunderTime;
    private float rainLevel;
    private float thunderLevel;

    private final List<Object> blockChanges = new ArrayList<>();
    public List<Object> getBlockChanges() { return Collections.unmodifiableList(blockChanges); }

    private final List<BlockDelta> blockDeltas = new ArrayList<>();
    private final List<BlockEntityDelta> blockEntityDeltas = new ArrayList<>();
    private final List<EntityDelta> entityDeltas = new ArrayList<>();
    private final Map<UUID, PlayerDelta> playerDeltas = new HashMap<>();
    private final Map<UUID, SubLevelSnapshot> subLevelSnapshots = new HashMap<>();
    private final Map<UUID, Set<UUID>> subLevelDependencies = new HashMap<>();
    private final Map<UUID, Set<com.timestop.core.rewind.RewindPlan.BlockKey>> subLevelBlockDependencies = new HashMap<>();

    /** World-side counterparts of native assembly/disassembly must rewind with their ship. */
    public synchronized void addSubLevelBlockDependency(UUID body, com.timestop.core.rewind.RewindPlan.BlockKey block) {
        if (!sealed && body != null && subLevelBlockDependencies.computeIfAbsent(body, ignored -> new HashSet<>()).add(block))
            estimatedMemoryBytes += 112;
    }
    public Map<UUID, Set<com.timestop.core.rewind.RewindPlan.BlockKey>> getSubLevelBlockDependencies() {
        Map<UUID, Set<com.timestop.core.rewind.RewindPlan.BlockKey>> result = new HashMap<>();
        subLevelBlockDependencies.forEach((id, blocks) -> result.put(id, Set.copyOf(blocks)));
        return Collections.unmodifiableMap(result);
    }

    /** Transient structural relationships must survive Sable clearing its split notification. */
    public synchronized void addSubLevelDependency(UUID first, UUID second) {
        if (sealed || first == null || second == null || first.equals(second)) return;
        if (subLevelDependencies.computeIfAbsent(first, ignored -> new HashSet<>()).add(second)) estimatedMemoryBytes += 96;
        if (subLevelDependencies.computeIfAbsent(second, ignored -> new HashSet<>()).add(first)) estimatedMemoryBytes += 96;
    }
    public Map<UUID, Set<UUID>> getSubLevelDependencies() {
        Map<UUID, Set<UUID>> result = new HashMap<>();
        subLevelDependencies.forEach((id, links) -> result.put(id, Set.copyOf(links)));
        return Collections.unmodifiableMap(result);
    }

    private boolean sealed = false;
    private long estimatedMemoryBytes = 64;

    public TickFrame(long gameTime) {
        this.gameTime = gameTime;
    }

    public void captureEnvironment(ServerLevel level) {
        this.dayTime = level.getDayTime();
        this.raining = level.isRaining();
        this.thundering = level.isThundering();
        this.rainLevel = level.getRainLevel(1.0F);
        this.thunderLevel = level.getThunderLevel(1.0F);

        if (level.getLevelData() instanceof ServerLevelData serverData) {
            this.rainTime = serverData.getRainTime();
            this.thunderTime = serverData.getThunderTime();
        }
    }

    public synchronized void addBlockDelta(BlockDelta delta) {
        if (sealed) return;
        blockDeltas.add(delta);
        blockChanges.add(delta);
        estimatedMemoryBytes += delta.estimateMemoryBytes();
    }

    public synchronized void addBlockEntityDelta(BlockEntityDelta delta) {
        if (sealed) return;
        blockEntityDeltas.add(delta);
        blockChanges.add(delta);
        estimatedMemoryBytes += delta.estimateMemoryBytes();
    }

    public synchronized void addEntityDelta(EntityDelta delta) {
        if (sealed) return;
        entityDeltas.add(delta);
        estimatedMemoryBytes += delta.estimateMemoryBytes();
    }

    public synchronized void putPlayerDelta(PlayerDelta delta) {
        if (sealed) return;
        playerDeltas.put(delta.playerUuid(), delta);
        estimatedMemoryBytes += delta.estimateMemoryBytes();
    }

    public synchronized void putSubLevelSnapshot(SubLevelSnapshot snapshot) {
        if (sealed) return;
        subLevelSnapshots.put(snapshot.bodyId(), snapshot);
        estimatedMemoryBytes += snapshot.estimateMemoryBytes();
    }

    /** Preserve untouched history when a sphere consumes only part of this interval. */
    public TickFrame without(com.timestop.core.rewind.RewindPlan plan) {
        TickFrame copy = new TickFrame(gameTime);
        copy.historyIdentity = historyIdentity;
        copy.dayTime=dayTime; copy.raining=raining; copy.thundering=thundering;
        copy.rainTime=rainTime; copy.thunderTime=thunderTime; copy.rainLevel=rainLevel; copy.thunderLevel=thunderLevel;
        var blocks = new java.util.HashSet<>(plan.blockTargetStates().keySet());
        blocks.addAll(plan.blockEntityTargetNbts().keySet()); blocks.addAll(plan.standaloneBeTargetNbts().keySet());
        for (Object change : blockChanges) {
            if (change instanceof BlockDelta d && !blocks.contains(new com.timestop.core.rewind.RewindPlan.BlockKey(d.dimension(),d.packedPos()))) copy.addBlockDelta(d);
            if (change instanceof BlockEntityDelta d && !blocks.contains(new com.timestop.core.rewind.RewindPlan.BlockKey(d.dimension(),d.packedPos()))) copy.addBlockEntityDelta(d);
        }
        for (var delta : entityDeltas) if (!plan.entitiesToRemove().contains(delta.entityId()) && !plan.entityTargetStates().containsKey(delta.entityId())) copy.addEntityDelta(delta);
        playerDeltas.forEach((id, delta) -> { if (!plan.playerTargetStates().containsKey(id)) copy.putPlayerDelta(delta); });
        subLevelSnapshots.forEach((id, snap) -> { if (!plan.subLevelTargetStates().containsKey(id)) copy.putSubLevelSnapshot(snap); });
        subLevelDependencies.forEach((id, links) -> links.forEach(other -> {
            if (!plan.subLevelTargetStates().containsKey(id) && !plan.subLevelTargetStates().containsKey(other)) copy.addSubLevelDependency(id, other);
        }));
        subLevelBlockDependencies.forEach((id, linkedBlocks) -> linkedBlocks.forEach(block -> {
            if (!plan.subLevelTargetStates().containsKey(id) && !blocks.contains(block)) copy.addSubLevelBlockDependency(id, block);
        }));
        for (var explosion : explosions) if (!plan.explosions().contains(explosion)) copy.addExplosion(explosion);
        copy.seal();
        return copy;
    }

    public synchronized void seal() {
        this.sealed = true;
    }

    public boolean isSealed() {
        return sealed;
    }

    public boolean isEmpty() {
        return blockDeltas.isEmpty() && blockEntityDeltas.isEmpty() && entityDeltas.isEmpty() && playerDeltas.isEmpty() && subLevelSnapshots.isEmpty() && subLevelDependencies.isEmpty() && subLevelBlockDependencies.isEmpty();
    }

    public long getGameTime() { return gameTime; }
    public long getDayTime() { return dayTime; }
    public boolean isRaining() { return raining; }
    public boolean isThundering() { return thundering; }
    public int getRainTime() { return rainTime; }
    public int getThunderTime() { return thunderTime; }
    public float getRainLevel() { return rainLevel; }
    public float getThunderLevel() { return thunderLevel; }

    public List<BlockDelta> getBlockDeltas() { return Collections.unmodifiableList(blockDeltas); }
    public List<BlockEntityDelta> getBlockEntityDeltas() { return Collections.unmodifiableList(blockEntityDeltas); }
    public List<EntityDelta> getEntityDeltas() { return Collections.unmodifiableList(entityDeltas); }
    public Map<UUID, PlayerDelta> getPlayerDeltas() { return Collections.unmodifiableMap(playerDeltas); }
    public Map<UUID, SubLevelSnapshot> getSubLevelSnapshots() { return Collections.unmodifiableMap(subLevelSnapshots); }

    public long getEstimatedMemoryBytes() { return estimatedMemoryBytes; }
}
