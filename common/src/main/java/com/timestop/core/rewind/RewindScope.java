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
        var tier = watch.getItem() instanceof AbstractWatchItem item ? item.getTier() : WatchTier.CREATIVE;
        return new RewindScope(player.level().dimension(), player.position().add(0, player.getBbHeight() * 0.5, 0), tier.getBubbleRadius());
    }
    public boolean contains(ResourceKey<Level> dim, Vec3 point) {
        return dimension.equals(dim) && center.distanceToSqr(point) <= radius * radius;
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
        Predicate<Map.Entry<RewindPlan.BlockKey, ?>> block = e -> contains(e.getKey().dimension(), BlockPos.of(e.getKey().packedPos()));
        var targets = select(plan.entityTargetStates(), e -> {
            Entity current = find(server, e.getKey());
            return contains(e.getValue()) && (current == null || contains(current));
        });
        Set<UUID> remove = new HashSet<>();
        for (UUID id : plan.entitiesToRemove()) { Entity current = find(server, id); if (current != null && contains(current)) remove.add(id); }
        return new RewindPlan(plan.frameCount(), select(plan.blockTargetStates(), block::test),
                select(plan.blockEntityTargetNbts(), block::test), select(plan.standaloneBeTargetNbts(), block::test),
                remove, select(plan.entitiesToRespawn(), e -> targets.containsKey(e.getKey())), targets,
                select(plan.playerTargetStates(), e -> {
                    if (exemptPlayers != null && exemptPlayers.contains(e.getKey())) return true;
                    var p = e.getValue(); var current = server.getPlayerList().getPlayer(e.getKey());
                    return current != null && contains(current) && contains(p.dimension(), new Vec3(p.posX(), p.posY(), p.posZ()));
                }), null, // Daylight and weather are dimension-wide, never local.
                plan.explosions().stream().filter(e -> contains(e.dimension(), new Vec3(e.x(), e.y(), e.z()))).toList());
    }
}
