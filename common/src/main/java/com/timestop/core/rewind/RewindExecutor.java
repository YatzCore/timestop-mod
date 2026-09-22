package com.timestop.core.rewind;

import com.timestop.core.rewind.data.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundGameEventPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.util.*;

/**
 * Compiles reverse deltas and applies world, entity, player, and environment restorations safely.
 */
public class RewindExecutor {
    private static int applicationDepth;
    public static boolean isApplyingPlan() { return applicationDepth > 0; }

    private static final Logger LOGGER = LoggerFactory.getLogger("TimeStopRewind");

    public record RewindResult(
            boolean success,
            int ticksRewound,
            int blocksRestored,
            int blockEntitiesRestored,
            int entitiesRestored,
            int entitiesRemoved,
            int playersRestored,
            List<String> warnings
    ) {
        public Component toComponent() {
            if (success) {
                return Component.literal(String.format(
                        "§d[TimeStop] Rewind complete! §7(%.1fs, %d ticks)\n" +
                                "§a✔ Blocks: %d §7| §aEntities: %d §7| §cRemoved: %d §7| §bPlayers: %d",
                        ticksRewound / 20.0, ticksRewound,
                        blocksRestored, entitiesRestored, entitiesRemoved, playersRestored
                ) + (warnings.isEmpty() ? "" : "\n\u00a7eSkipped " + warnings.size() + " unsupported entity restorations."));
            } else {
                return Component.literal("§c[TimeStop] Rewind failed: " + (warnings.isEmpty() ? "Unknown error" : warnings.get(0)));
            }
        }
    }

    public static RewindPlan buildPlan(List<TickFrame> frames) {
        Set<UUID> entitiesToRemove = new HashSet<>();
        Map<UUID, RewindPlan.EntitySpawnInfo> entitiesToRespawn = new HashMap<>();
        Map<UUID, RewindPlan.EntitySpawnInfo> entityTargetStates = new HashMap<>();
        Map<RewindPlan.BlockKey, BlockState> blockTargetStates = new LinkedHashMap<>();
        Map<RewindPlan.BlockKey, CompoundTag> blockEntityTargetNbts = new LinkedHashMap<>();
        Map<RewindPlan.BlockKey, CompoundTag> standaloneBeTargetNbts = new LinkedHashMap<>();
        Map<UUID, PlayerDelta> playerTargetStates = new HashMap<>();

        // Process frames newest to oldest: using put() ensures the OLDEST state wins
        for (TickFrame frame : frames) {
            List<EntityDelta> deltas = frame.getEntityDeltas();
            for (int i = deltas.size() - 1; i >= 0; i--) {
                EntityDelta delta = deltas.get(i);
                switch (delta.type()) {
                    case SPAWN -> {
                        entitiesToRemove.add(delta.entityId());
                        entitiesToRespawn.remove(delta.entityId());
                        entityTargetStates.remove(delta.entityId());
                    }
                    case DESPAWN -> {
                        if (delta.oldState() != null && delta.entityType() != null) {
                            entitiesToRemove.remove(delta.entityId());
                            var info = new RewindPlan.EntitySpawnInfo(delta.dimension(), delta.entityType(), delta.oldState());
                            entitiesToRespawn.put(delta.entityId(), info);
                            entityTargetStates.put(delta.entityId(), info);
                        }
                    }
                    case UPDATE -> {
                        if (delta.oldState() != null) {
                            entitiesToRemove.remove(delta.entityId());
                            var type = delta.entityType() != null ? delta.entityType() : ResourceLocation.tryParse(delta.oldState().getString("id"));
                            var info = new RewindPlan.EntitySpawnInfo(delta.dimension(), type, delta.oldState());
                            entityTargetStates.put(delta.entityId(), info);
                            if (entitiesToRespawn.containsKey(delta.entityId())) entitiesToRespawn.put(delta.entityId(), info);
                        }
                    }
                }
            }

            // Reverse events INSIDE each tick too. Block and inventory edits share one ordering.
            List<Object> changes = frame.getBlockChanges();
            for (int i = changes.size() - 1; i >= 0; i--) {
                Object change = changes.get(i);
                if (change instanceof BlockDelta delta) {
                    RewindPlan.BlockKey key = new RewindPlan.BlockKey(delta.dimension(), delta.packedPos());
                    blockTargetStates.put(key, delta.oldState());
                    blockEntityTargetNbts.remove(key);
                    standaloneBeTargetNbts.remove(key);
                    if (delta.oldBlockEntityNbt() != null) blockEntityTargetNbts.put(key, delta.oldBlockEntityNbt());
                } else if (change instanceof BlockEntityDelta delta) {
                    RewindPlan.BlockKey key = new RewindPlan.BlockKey(delta.dimension(), delta.packedPos());
                    blockEntityTargetNbts.remove(key);
                    standaloneBeTargetNbts.put(key, delta.oldNbt());
                }
            }

            for (Map.Entry<UUID, PlayerDelta> entry : frame.getPlayerDeltas().entrySet()) {
                playerTargetStates.put(entry.getKey(), entry.getValue());
            }
        }

        TickFrame oldest = frames.isEmpty() ? null : frames.get(frames.size() - 1);

        return new RewindPlan(
                frames.size(),
                blockTargetStates,
                blockEntityTargetNbts,
                standaloneBeTargetNbts,
                entitiesToRemove,
                entitiesToRespawn,
                entityTargetStates,
                playerTargetStates,
                oldest,
                frames.stream().flatMap(frame -> frame.getExplosions().stream()).toList()
        );
    }

    public static RewindResult applyPlan(MinecraftServer server, RewindPlan plan, boolean rollbackInventory) {
        applicationDepth++;
        try {
            return applyPlanInternal(server, plan, rollbackInventory);
        } finally {
            applicationDepth--;
        }
    }

    private static RewindResult applyPlanInternal(MinecraftServer server, RewindPlan plan, boolean rollbackInventory) {
        List<String> warnings = new ArrayList<>();
        int blocksRestored = 0;
        int blockEntitiesRestored = 0;
        int entitiesRestored = 0;
        int entitiesRemoved = 0;
        int playersRestored = 0;

        // 1. Remove newly spawned entities
        for (UUID entityId : plan.entitiesToRemove()) {
            for (ServerLevel world : server.getAllLevels()) {
                Entity entity = world.getEntity(entityId);
                if (entity != null && !(entity instanceof ServerPlayer)) {
                    removeRewoundEntity(entity);
                    entitiesRemoved++;
                    break;
                }
            }
        }

        // Full snapshots restore inventory, equipment, AI data and living state as well as motion.
        for (Map.Entry<UUID, RewindPlan.EntitySpawnInfo> entry : plan.entityTargetStates().entrySet()) {
            if (plan.entitiesToRemove().contains(entry.getKey())) continue;
            var info = entry.getValue();
            ServerLevel level = server.getLevel(info.dimension());
            if (level == null) continue;
            Entity entity = findEntity(server, entry.getKey());
            if (entity instanceof ServerPlayer) continue;
            // Absence from the visible entity lookup often means a chunk unloaded, not death.
            if (entity == null && !plan.entitiesToRespawn().containsKey(entry.getKey())) continue;
            if (entity == null && info.state().getBoolean("TimeStopTransient")) continue;
            boolean reviving = info.state().getFloat("Health") > 0
                    && (entity == null || entity instanceof LivingEntity living && living.isDeadOrDying());
            try {
                if (entity != null && (entity.level() != level
                        || entity instanceof LivingEntity living && living.isDeadOrDying() && info.state().getFloat("Health") > 0)) {
                    if (info.state().getBoolean("TimeStopTransient")) continue;
                    removeRewoundEntity(entity);
                    entity = null;
                }
                if (entity == null) entity = respawnEntity(level, info.entityType(), info.state());
                else restoreEntityState(entity, info.state());
                if (entity != null) {
                    entitiesRestored++;
                    if (reviving && entity instanceof LivingEntity) {
                        var packet = new com.timestop.network.RewindMobPacket(level.dimension().location(), entity.getUUID());
                        for (ServerPlayer viewer : level.players()) if (viewer.distanceToSqr(entity) <= 32 * 32)
                            com.timestop.network.ModMessages.sendToPlayer(packet, viewer);
                    }
                }
            } catch (Exception error) {
                // A broken mod entity must not abort player/block restoration or remain half registered.
                LOGGER.warn("Skipping rewind entity {} ({})", entry.getKey(), info.entityType(), error);
                warnings.add("Could not restore entity " + info.entityType());
            }
        }

        RewindBlockAnimations.send(server, plan);

        // Discard queued forward piston actions only at positions being rewound.
        for (var key : plan.blockTargetStates().keySet()) {
            var world = server.getLevel(key.dimension());
            if (world != null) {
                var pos = BlockPos.of(key.packedPos());
                world.clearBlockEvents(new net.minecraft.world.level.levelgen.structure.BoundingBox(pos));
            }
        }
        // 4. Restore block states & block entity NBTs
        for (Map.Entry<RewindPlan.BlockKey, BlockState> entry : plan.blockTargetStates().entrySet()) {
            RewindPlan.BlockKey key = entry.getKey();
            ServerLevel level = server.getLevel(key.dimension());
            if (level == null) continue;

            BlockPos pos = BlockPos.of(key.packedPos());
            if (!ensureChunkLoaded(level, pos)) continue;

            if (level.getBlockState(pos).getBlock() != entry.getValue().getBlock()) {
                // Container onRemove drops contents even with UPDATE_SUPPRESS_DROPS.
                net.minecraft.world.Clearable.tryClear(level.getBlockEntity(pos));
            }
            if (level.setBlock(pos, entry.getValue(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SUPPRESS_DROPS)) {
                blocksRestored++;
            }

            for (ServerPlayer viewer : level.players()) viewer.connection.send(new net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket(level, pos));
        }
        for (var entry : plan.blockEntityTargetNbts().entrySet()) {
            var level = server.getLevel(entry.getKey().dimension());
            if (level != null && restoreBlockEntity(level, BlockPos.of(entry.getKey().packedPos()), entry.getValue())) blockEntitiesRestored++;
        }

        // 5. Restore standalone block entity NBTs
        for (Map.Entry<RewindPlan.BlockKey, CompoundTag> entry : plan.standaloneBeTargetNbts().entrySet()) {
            RewindPlan.BlockKey key = entry.getKey();
            if (plan.blockEntityTargetNbts().containsKey(key)) continue;

            ServerLevel level = server.getLevel(key.dimension());
            if (level == null) continue;

            BlockPos pos = BlockPos.of(key.packedPos());
            if (!ensureChunkLoaded(level, pos)) continue;

            if (restoreBlockEntity(level, pos, entry.getValue())) blockEntitiesRestored++;
        }

        // 6. Restore player health, inventory, hunger, and potion effects
        for (Map.Entry<UUID, PlayerDelta> entry : plan.playerTargetStates().entrySet()) {
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player != null && !player.isRemoved()) {
                if (player.isDeadOrDying()) {
                    player.deathTime = 0;
                }
                entry.getValue().restoreTo(player, rollbackInventory);
                playersRestored++;
            }
        }

        // 7. Restore world time and weather
        if (plan.oldestFrame() != null) {
            TickFrame oldest = plan.oldestFrame();
            for (ServerLevel level : server.getAllLevels()) {
                level.setDayTime(oldest.getDayTime());

                if (level == server.overworld()) level.setWeatherParameters(0, oldest.getRainTime(), oldest.isRaining(), oldest.isThundering());
                if (level.getLevelData() instanceof net.minecraft.world.level.storage.ServerLevelData data) {
                    data.setThunderTime(oldest.getThunderTime());
                }
                level.setThunderLevel(oldest.getThunderLevel());
                if (oldest.isRaining()) {
                    level.setRainLevel(oldest.getRainLevel());
                } else {
                    level.setRainLevel(0.0F);
                }

                for (ServerPlayer player : level.players()) {
                    player.connection.send(new net.minecraft.network.protocol.game.ClientboundSetTimePacket(level.getGameTime(), level.getDayTime(),
                            level.getGameRules().getBoolean(net.minecraft.world.level.GameRules.RULE_DAYLIGHT)));
                    player.connection.send(new ClientboundGameEventPacket(
                            oldest.isRaining() ? ClientboundGameEventPacket.START_RAINING : ClientboundGameEventPacket.STOP_RAINING, 0.0F));
                    player.connection.send(new ClientboundGameEventPacket(
                            ClientboundGameEventPacket.RAIN_LEVEL_CHANGE, oldest.getRainLevel()));
                    player.connection.send(new ClientboundGameEventPacket(
                            ClientboundGameEventPacket.THUNDER_LEVEL_CHANGE, oldest.getThunderLevel()));
                }
            }
        }

        for (TickFrame.ExplosionMoment explosion : plan.explosions()) {
            ServerLevel level = server.getLevel(explosion.dimension());
            if (level != null) RewindExplosionEffects.enqueue(level, explosion);
        }
        return new RewindResult(true, plan.frameCount(), blocksRestored, blockEntitiesRestored,
                entitiesRestored, entitiesRemoved, playersRestored, warnings);
    }

    public static RewindResult execute(MinecraftServer server, int seconds, @Nullable ServerPlayer initiator, boolean rollbackInventory) {
        return execute(server, seconds, initiator, rollbackInventory, RewindScope.forPlayer(initiator));
    }

    public static RewindResult execute(MinecraftServer server, int seconds, @Nullable ServerPlayer initiator, boolean rollbackInventory, @Nullable RewindScope scope) {
        TickRecorder recorder = TickRecorder.getInstance();
        TimelineBuffer buffer = recorder.getTimelineBuffer();

        if (buffer.isRewinding()) {
            return new RewindResult(false, 0, 0, 0, 0, 0, 0, List.of("Rewind already in progress"));
        }

        if (scope != null && LocalRewind.overlaps(scope))
            return new RewindResult(false, 0, 0, 0, 0, 0, 0, List.of("A rewind already overlaps this bubble"));
        if (scope == null) LocalRewind.clear();
        recorder.finishFrame(server);
        int ticksToRewind = seconds * 20;
        List<TickFrame> frames = buffer.getFramesForRewind(ticksToRewind);

        if (frames.isEmpty()) {
            return new RewindResult(false, 0, 0, 0, 0, 0, 0, List.of("No frames available in timeline buffer"));
        }

        LOGGER.info("[TimeStop] Initiating rewind of {} ticks ({} frames, ~{}s)", ticksToRewind, frames.size(), seconds);
        RewindPlan plan = buildPlan(frames);
        UUID initiatorUuid = initiator != null ? initiator.getUUID() : null;
        if (scope != null) plan = scope.filter(server, plan, initiatorUuid);

        buffer.setRewinding(true);
        buffer.setRecording(false);

        try {
            RewindResult result = applyPlan(server, plan, rollbackInventory);
            if (scope == null) buffer.removeRecentFrames(frames.size());
            else buffer.consumeFrames(frames, plan);
            return result;
        } catch (Exception e) {
            LOGGER.error("[TimeStop] Rewind execution failed", e);
            buffer.clear();
            return new RewindResult(false, 0, 0, 0, 0, 0, 0, List.of("Exception: " + e.getMessage()));
        } finally {
            buffer.setRewinding(false);
            buffer.setRecording(true);
            recorder.clearTrackingData();
        }
    }

    private static void removeRewoundEntity(Entity entity) {
        if (entity.level() instanceof ServerLevel level) {
            level.getChunkSource().broadcast(entity, new net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket(entity.getId()));
        }
        entity.discard();
    }

    private static Entity findEntity(MinecraftServer server, UUID entityId) {
        for (ServerLevel level : server.getAllLevels()) {
            Entity entity = level.getEntity(entityId);
            if (entity != null) return entity;
        }
        return null;
    }

    private static void restoreEntityState(Entity entity, CompoundTag tag) {
        if (entity instanceof LivingEntity living) living.removeAllEffects();
        entity.load(com.timestop.combat.RewindRuneManager.restorationNbt(tag));
        entity.setOldPosAndRot();
        entity.hasImpulse = true;
        RewindEntitySync.mark(entity);
        if (entity instanceof LivingEntity living && living.getHealth() > 0) {
            living.deathTime = 0;
            living.hurtTime = 0;
        }
        if (entity.level() instanceof ServerLevel level) {
            // World tracking is paused during playback. Send metadata now, including the TNT fuse.
            var dirtyData = entity.getEntityData().packDirty();
            if (dirtyData != null) level.getChunkSource().broadcast(entity,
                    new net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket(entity.getId(), dirtyData));
            level.getChunkSource().broadcast(entity, new net.minecraft.network.protocol.game.ClientboundTeleportEntityPacket(entity));
            level.getChunkSource().broadcast(entity, new net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket(entity));
        }
    }

    private static Entity respawnEntity(ServerLevel level, ResourceLocation entityTypeId, CompoundTag state) {
        if (entityTypeId == null || state.getBoolean("TimeStopTransient")) return null;
        Entity entity = BuiltInRegistries.ENTITY_TYPE.getOptional(entityTypeId).map(type -> type.create(level)).orElse(null);
        if (entity == null) return null;
        try {
            entity.load(com.timestop.combat.RewindRuneManager.restorationNbt(state));
            if (!ensureChunkLoaded(level, entity.blockPosition())) return null;
            return level.addFreshEntity(entity) ? entity : null;
        } catch (Exception error) {
            entity.discard();
            throw error;
        }
    }

    private static boolean restoreBlockEntity(ServerLevel level, BlockPos pos, CompoundTag tag) {
        BlockState state = level.getBlockState(pos);
        if (!state.hasBlockEntity()) return false;
        BlockEntity be = level.getBlockEntity(pos);
        CompoundTag cleanTag = com.timestop.combat.RewindRuneManager.restorationNbt(tag);
        if (be == null) {
            be = BlockEntity.loadStatic(pos, state, cleanTag, level.registryAccess());
            if (be == null) return false;
            level.setBlockEntity(be);
        } else {
            be.loadWithComponents(cleanTag, level.registryAccess());
        }
        be.setChanged();
        level.sendBlockUpdated(pos, state, state, Block.UPDATE_CLIENTS);
        // Moving pistons do not provide a vanilla update packet.
        var packet = be.getUpdatePacket();
        for (ServerPlayer viewer : level.players()) {
            if (packet != null) viewer.connection.send(packet);
            if (be instanceof net.minecraft.world.level.block.piston.PistonMovingBlockEntity)
                com.timestop.network.ModMessages.sendToPlayer(new com.timestop.network.RewindPistonPacket(
                        level.dimension().location(), pos, be.saveWithFullMetadata(level.registryAccess())), viewer);
        }
        return true;
    }

    private static boolean ensureChunkLoaded(ServerLevel level, BlockPos pos) {
        ChunkPos chunkPos = new ChunkPos(pos);
        LevelChunk chunk = level.getChunkSource().getChunk(chunkPos.x, chunkPos.z, false);
        if (chunk != null) return true;
        return level.getChunkSource().getChunk(chunkPos.x, chunkPos.z, true) != null;
    }
}
