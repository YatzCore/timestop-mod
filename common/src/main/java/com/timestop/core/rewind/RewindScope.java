package com.timestop.core.rewind;

import com.timestop.core.*;
import com.timestop.item.*;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import java.util.*;
import java.util.function.Predicate;

/** Fixed activation sphere. Null scope means an explicitly global rewind. */
public record RewindScope(ResourceKey<Level> dimension, Vec3 center, double radius) {
    public static RewindScope forPlayer(Player player) {
        return player == null ? null : forWatch(player, AbstractWatchItem.findActivationWatch(player));
    }
    public static RewindScope forWatch(Player player, ItemStack watch) {
        boolean global = switch (TimeStopSavedData.get().getWatchScope()) {
            case GLOBAL -> true;
            case SPHERE -> false;
            case WATCH -> AbstractWatchItem.isGlobalScope(watch);
        };
        if (global) return null;
        var bubble = TemporalBubbleManager.getPlayerBubble(player.getUUID());
        if (bubble != null) return new RewindScope(bubble.getDimension(), bubble.getCenter(), bubble.getRadius());
        return new RewindScope(player.level().dimension(), player.position().add(0, player.getBbHeight() * 0.5, 0), AbstractWatchItem.getBubbleRadius(watch));
    }
    public boolean contains(ResourceKey<Level> dim, Vec3 point) {
        if (!dimension.equals(dim)) return false;
        Vec3 world = com.timestop.compat.TemporalWorlds.serverToWorld(dim, point);
        return center.distanceToSqr(world) <= radius * radius;
    }
    public boolean contains(ResourceKey<Level> dim, BlockPos pos) { return contains(dim, Vec3.atCenterOf(pos)); }
    public boolean contains(Entity entity) { return contains(entity.level().dimension(), entity.position()); }
    private boolean contains(RewindPlan.EntitySpawnInfo info) {
        var pos = info.state().getList("Pos", 6);
        return pos.size() == 3 && contains(info.dimension(), new Vec3(pos.getDouble(0), pos.getDouble(1), pos.getDouble(2)));
    }
    private static Entity find(MinecraftServer server, UUID id) {
        for (var level : server.getAllLevels()) { var entity = level.getEntity(id); if (entity != null) return entity; }
        return null;
    }
    private static <K,V> Map<K,V> select(Map<K,V> source, Predicate<Map.Entry<K,V>> predicate) {
        Map<K,V> result = new LinkedHashMap<>();
        source.entrySet().stream().filter(predicate).forEach(e -> result.put(e.getKey(), e.getValue()));
        return result;
    }
    public RewindPlan filter(MinecraftServer server, RewindPlan plan) {
        return filter(server, plan, (UUID) null);
    }

    public RewindPlan filter(MinecraftServer server, RewindPlan plan, java.util.UUID initiatorId) {
        return filter(server, plan, initiatorId != null ? Set.of(initiatorId) : Collections.emptySet());
    }

    public RewindPlan filter(MinecraftServer server, RewindPlan plan, Set<UUID> exemptPlayers) {
        return filter(server, plan, exemptPlayers, null);
    }

    public RewindPlan filter(MinecraftServer server, RewindPlan plan, Set<UUID> exemptPlayers, Set<UUID> fixedSubLevels) {
        return filter(server, plan, exemptPlayers, fixedSubLevels, Set.of());
    }

    public RewindPlan filter(MinecraftServer server, RewindPlan plan, Set<UUID> exemptPlayers, Set<UUID> fixedSubLevels,
                             Set<RewindPlan.BlockKey> fixedCounterparts) {
        Map<UUID, com.timestop.core.rewind.data.SubLevelSnapshot> subLevels;
        if (fixedSubLevels != null) {
            subLevels = select(plan.subLevelTargetStates(), e -> fixedSubLevels.contains(e.getKey()));
        } else {
            subLevels = select(plan.subLevelTargetStates(), e -> {
                var target = e.getValue();
                Vec3 targetCom = new Vec3(target.centerOfMass().x, target.centerOfMass().y, target.centerOfMass().z);
                boolean targetIn = contains(target.dimension(), targetCom);
                var level = server.getLevel(target.dimension());
                boolean currentIn = false;
                if (level != null) {
                    var liveCom = com.timestop.compat.TemporalWorlds.bridge().getSubLevelCenterOfMassWorld(level, target.bodyId());
                    if (liveCom.isPresent()) {
                        currentIn = contains(target.dimension(), liveCom.get());
                    } else {
                        var lastLive = com.timestop.compat.TemporalWorlds.bridge().getLastLiveCenterOfMassWorld(level, target.bodyId());
                        currentIn = lastLive.map(pos -> contains(target.dimension(), pos)).orElse(false);
                    }
                } else {
                    currentIn = false;
                }
                return targetIn && currentIn;
            });

            // Selecting a world counterpart also selects the ship that supplied/received it.
            plan.subLevelBlockDependencies().forEach((id, blocks) -> {
                if (blocks.stream().anyMatch(key -> contains(key.dimension(), BlockPos.of(key.packedPos())))) {
                    var target = plan.subLevelTargetStates().get(id);
                    if (target != null) subLevels.put(id, target);
                }
            });
            // Connected assembly closure: expand selected subLevels transitively through live and historical joints
            Queue<UUID> queue = new ArrayDeque<>(subLevels.keySet());
            while (!queue.isEmpty()) {
                UUID current = queue.poll();
                Set<UUID> neighbors = new HashSet<>(com.timestop.compat.TemporalWorlds.bridge().getConnectedSubLevels(current));
                var counterparts = plan.subLevelBlockDependencies().getOrDefault(current, Set.of());
                if (!counterparts.isEmpty()) plan.subLevelBlockDependencies().forEach((id, blocks) -> {
                    if (!Collections.disjoint(counterparts, blocks)) neighbors.add(id);
                });
                var currentTarget = plan.subLevelTargetStates().get(current);
                if (currentTarget != null && currentTarget.connectedBodyIds() != null) {
                    neighbors.addAll(currentTarget.connectedBodyIds());
                }
                if (plan.historicalConnectedSubLevels() != null) {
                    var hist = plan.historicalConnectedSubLevels().get(current);
                    if (hist != null) neighbors.addAll(hist);
                }
                for (UUID neighbor : neighbors) {
                    if (!subLevels.containsKey(neighbor)) {
                        var targetState = plan.subLevelTargetStates().get(neighbor);
                        if (targetState != null) {
                            subLevels.put(neighbor, targetState);
                            queue.add(neighbor);
                        }
                    }
                }
            }
        }

        Set<RewindPlan.BlockKey> counterparts = new HashSet<>(fixedCounterparts);
        subLevels.keySet().forEach(id -> counterparts.addAll(plan.subLevelBlockDependencies().getOrDefault(id, Set.of())));
        Predicate<Map.Entry<RewindPlan.BlockKey, ?>> block = e -> {
            if (counterparts.contains(e.getKey())) return true;
            BlockPos bpos = BlockPos.of(e.getKey().packedPos());
            var level = server.getLevel(e.getKey().dimension());
            if (level != null) {
                for (var subLevelSnap : subLevels.values()) {
                    if (subLevelSnap.dimension().equals(e.getKey().dimension()) &&
                            com.timestop.compat.TemporalWorlds.bridge().isSubLevelPlotBlock(level, bpos, subLevelSnap)) {
                        return true;
                    }
                }
                UUID currentSub = com.timestop.compat.TemporalWorlds.bridge().getContainingSubLevelId(level, bpos);
                if (currentSub != null) {
                    return subLevels.containsKey(currentSub);
                }
            }
            return contains(e.getKey().dimension(), bpos);
        };

        var targets = select(plan.entityTargetStates(), e -> {
            Entity current = find(server, e.getKey());
            if (current != null) {
                UUID currentSub = com.timestop.compat.TemporalWorlds.bridge().getContainingSubLevelId(current.level(), current.blockPosition());
                if (currentSub != null) return subLevels.containsKey(currentSub);
            }
            return contains(e.getValue()) && (current == null || contains(current));
        });

        Set<UUID> remove = new HashSet<>();
        for (UUID id : plan.entitiesToRemove()) {
            Entity current = find(server, id);
            if (current != null) {
                UUID currentSub = com.timestop.compat.TemporalWorlds.bridge().getContainingSubLevelId(current.level(), current.blockPosition());
                if (currentSub != null) {
                    if (subLevels.containsKey(currentSub)) remove.add(id);
                    continue;
                }
                if (contains(current)) remove.add(id);
            }
        }
        return new RewindPlan(plan.frameCount(), select(plan.blockTargetStates(), block::test),
                select(plan.blockEntityTargetNbts(), block::test), select(plan.standaloneBeTargetNbts(), block::test),
                remove, select(plan.entitiesToRespawn(), e -> targets.containsKey(e.getKey())), targets,
                select(plan.playerTargetStates(), e -> {
                    if (exemptPlayers != null && exemptPlayers.contains(e.getKey())) return true;
                    var p = e.getValue(); var current = server.getPlayerList().getPlayer(e.getKey());
                    return current != null && contains(current) && contains(p.dimension(), new Vec3(p.posX(), p.posY(), p.posZ()));
                }), subLevels, null, // Daylight and weather are dimension-wide, never local.
                plan.explosions().stream().filter(e -> contains(e.dimension(), new Vec3(e.x(), e.y(), e.z()))).toList(),
                select(plan.historicalConnectedSubLevels(), e -> subLevels.containsKey(e.getKey())),
                select(plan.subLevelBlockDependencies(), e -> subLevels.containsKey(e.getKey())));
    }
}
