package com.timestop.compat.sable;

import com.timestop.compat.TemporalPosition;
import com.timestop.compat.TemporalWorldBridge;
import com.timestop.compat.TemporalWorlds;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.SableCompanion;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.api.physics.constraint.FixedConstraintConfiguration;
import dev.ryanhcode.sable.api.physics.constraint.FreeConstraintConfiguration;
import dev.ryanhcode.sable.api.physics.constraint.GenericConstraintConfiguration;
import dev.ryanhcode.sable.api.physics.constraint.PhysicsConstraintConfiguration;
import dev.ryanhcode.sable.api.physics.constraint.RotaryConstraintConfiguration;
import com.timestop.core.rewind.RewindPlan;
import org.joml.Vector3d;
import org.joml.Quaterniond;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import java.util.Optional;

/** Only loaded when Sable is installed. Companion is supplied by Sable itself. */
public final class SableWorldBridge implements TemporalWorldBridge {
    @Override
    public Vec3 toWorld(Level level, Vec3 position) {
        return SableCompanion.INSTANCE.projectOutOfSubLevel(level, position);
    }

    private static Vec3 origin(SubLevel body) {
        var min = body.getPlot().getChunkMin();
        return new Vec3(min.getMinBlockX(), 0, min.getMinBlockZ());
    }

    @Override
    public TemporalPosition anchor(Level level, Vec3 position) {
        var body = SableCompanion.INSTANCE.getContaining(level, position);
        if (body instanceof SubLevel subLevel) {
            return new TemporalPosition(level.dimension(), body.getUniqueId(), position.subtract(origin(subLevel)));
        }
        if (SableCompanion.INSTANCE.isInPlotGrid(level, position)) {
            throw new IllegalStateException("Cannot anchor an unloaded Sable plot");
        }
        return TemporalWorldBridge.VANILLA.anchor(level, position);
    }

    @Override
    public Optional<Vec3> resolve(Level level, TemporalPosition position) {
        if (!level.dimension().equals(position.dimension())) return Optional.empty();
        if (position.bodyId() == null) return Optional.of(position.position());
        var container = SubLevelContainer.getContainer(level);
        if (container == null) return Optional.empty();
        var body = container.getSubLevel(position.bodyId());
        return body == null || body.isRemoved() ? Optional.empty()
                : Optional.of(body.logicalPose().transformPosition(position.position().add(origin(body))));
    }

    @Override
    public net.minecraft.world.level.block.entity.BlockEntity getBlockEntityAtWorld(Level level, Vec3 worldPos) {
        if (level == null) return null;
        var container = SubLevelContainer.getContainer(level);
        if (container != null) {
            org.joml.Vector3d probe = new org.joml.Vector3d(worldPos.x, worldPos.y, worldPos.z);
            org.joml.Vector3d local = new org.joml.Vector3d();
            for (var sub : container.getAllSubLevels()) {
                if (sub.isRemoved()) continue;
                sub.logicalPose().transformPositionInverse(probe, local);
                net.minecraft.core.BlockPos localPos = net.minecraft.core.BlockPos.containing(local.x, local.y, local.z);
                Level plotLevel = sub.getLevel();
                if (plotLevel != null && plotLevel.hasChunkAt(localPos)) {
                    var be = plotLevel.getBlockEntity(localPos);
                    if (be != null) return be;
                }
            }
        }
        net.minecraft.core.BlockPos pos = net.minecraft.core.BlockPos.containing(worldPos);
        return level.hasChunkAt(pos) ? level.getBlockEntity(pos) : null;
    }

    private final java.util.Map<net.minecraft.world.entity.Entity, DeckAnchor> deckAnchors = new java.util.WeakHashMap<>();

    private record DeckAnchor(java.util.UUID subLevelId, org.joml.Vector3d localPos) {}

    private SubLevel findSupportingSubLevel(net.minecraft.world.entity.Entity entity) {
        SubLevel tracking = dev.ryanhcode.sable.Sable.HELPER.getTrackingSubLevel(entity);
        if (tracking != null && !tracking.isRemoved()) return tracking;

        Level level = entity.level();
        var container = SubLevelContainer.getContainer(level);
        if (container != null) {
            org.joml.Vector3d footPos = new org.joml.Vector3d(entity.getX(), entity.getY() - 0.25, entity.getZ());
            org.joml.Vector3d local = new org.joml.Vector3d();
            for (var candidate : container.getAllSubLevels()) {
                if (candidate.isRemoved()) continue;
                candidate.logicalPose().transformPositionInverse(footPos, local);
                net.minecraft.core.BlockPos localPos = net.minecraft.core.BlockPos.containing(local.x, local.y, local.z);
                Level plotLevel = candidate.getLevel();
                if (plotLevel != null && plotLevel.hasChunkAt(localPos) && !plotLevel.getBlockState(localPos).isAir()) {
                    return candidate;
                }
            }
        }
        return null;
    }

    @Override
    public void updateStasisEntityTransport(net.minecraft.world.entity.Entity entity) {
        if (entity.getVehicle() != null) {
            // A vehicle owns the passenger's position; discard any pre-mount deck position.
            deckAnchors.remove(entity);
            entity.getVehicle().positionRider(entity);
            return;
        }

        DeckAnchor anchor = deckAnchors.get(entity);
        if (anchor == null) {
            SubLevel supporting = findSupportingSubLevel(entity);
            if (supporting != null && !supporting.isRemoved()) {
                org.joml.Vector3d local = supporting.logicalPose().transformPositionInverse(
                        new org.joml.Vector3d(entity.getX(), entity.getY(), entity.getZ()), new org.joml.Vector3d());
                anchor = new DeckAnchor(supporting.getUniqueId(), local);
                deckAnchors.put(entity, anchor);
            }
        }

        if (anchor != null) {
            SubLevelContainer container = SubLevelContainer.getContainer(entity.level());
            if (container != null) {
                SubLevel subLevel = container.getSubLevel(anchor.subLevelId());
                if (subLevel != null && !subLevel.isRemoved()) {
                    org.joml.Vector3d worldPos = subLevel.logicalPose().transformPosition(
                            new org.joml.Vector3d(anchor.localPos), new org.joml.Vector3d());
                    entity.setPos(worldPos.x, worldPos.y, worldPos.z);
                    return;
                }
            }
            deckAnchors.remove(entity);
        }
    }

    @Override
    public void clearStasisEntityTransport(net.minecraft.world.entity.Entity entity) {
        deckAnchors.remove(entity);
        deckAnchors.keySet().removeIf(net.minecraft.world.entity.Entity::isRemoved);
    }

    @Override
    public java.util.UUID getContainingSubLevelId(Level level, net.minecraft.core.BlockPos pos) {
        if (level == null || pos == null) return null;
        var body = SableCompanion.INSTANCE.getContaining(level, new Vec3(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5));
        return body != null ? body.getUniqueId() : null;
    }

    @Override
    public java.util.Optional<Vec3> getSubLevelCenterOfMassWorld(Level level, java.util.UUID bodyId) {
        if (level == null || bodyId == null) return java.util.Optional.empty();
        var container = dev.ryanhcode.sable.api.sublevel.SubLevelContainer.getContainer(level);
        if (container == null) return java.util.Optional.empty();
        var sub = container.getSubLevel(bodyId);
        if (sub instanceof dev.ryanhcode.sable.sublevel.ServerSubLevel body && !body.isRemoved()) {
            org.joml.Vector3d com = SableBodyTimeManager.getBodyCenterOfMassWorld(body);
            return java.util.Optional.of(new Vec3(com.x, com.y, com.z));
        }
        return java.util.Optional.empty();
    }

    private final java.util.Map<java.util.UUID, Vec3> lastLiveCom = new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.Set<java.util.UUID> unloadedBodies = new java.util.HashSet<>();

    @Override
    public java.util.Optional<Vec3> getLastLiveCenterOfMassWorld(Level level, java.util.UUID bodyId) {
        if (bodyId == null) return java.util.Optional.empty();
        return java.util.Optional.ofNullable(lastLiveCom.get(bodyId));
    }

    public static void recordBodyCenterOfMass(java.util.UUID bodyId, Vec3 pos) {
        if (bodyId != null && pos != null) {
            if (TemporalWorlds.bridge() instanceof SableWorldBridge sableBridge) {
                sableBridge.lastLiveCom.put(bodyId, pos);
            }
        }
    }

    public static void recordBodyRemoved(dev.ryanhcode.sable.sublevel.ServerSubLevel body,
                                         dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason reason) {
        if (body == null) return;
        if (TemporalWorlds.bridge() instanceof SableWorldBridge bridge) {
            if (reason == dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason.UNLOADED) bridge.unloadedBodies.add(body.getUniqueId());
            else bridge.unloadedBodies.remove(body.getUniqueId());
        }
        LAST_CAPTURED_PLOTS.remove(body.getUniqueId());
        org.joml.Vector3d com = SableBodyTimeManager.getBodyCenterOfMassWorld(body);
        recordBodyCenterOfMass(body.getUniqueId(), new Vec3(com.x, com.y, com.z));
    }

    public record SavedConstraint(
            java.util.UUID bodyA,
            java.util.UUID bodyB,
            dev.ryanhcode.sable.api.physics.constraint.PhysicsConstraintConfiguration<?> config,
            SableConstraintState.Snapshot settings
    ) implements com.timestop.core.rewind.data.SubLevelSnapshot.RetainedConstraint {
        @Override public long estimatedBytes() { return 256L + settings.estimatedBytes(); }
    }

    public record ConstraintRecord(
            java.util.UUID id,
            java.util.UUID a,
            java.util.UUID b,
            dev.ryanhcode.sable.api.physics.constraint.PhysicsConstraintConfiguration<?> config,
            dev.ryanhcode.sable.api.physics.constraint.PhysicsConstraintHandle handle
    ) {}
    private static final java.util.List<ConstraintRecord> ACTIVE_CONSTRAINTS = new java.util.concurrent.CopyOnWriteArrayList<>();
    // History/playback owns the tags. This lookup must never retain a tag after its last owner releases it.
    private static final java.lang.ref.ReferenceQueue<net.minecraft.nbt.CompoundTag> PLOT_QUEUE = new java.lang.ref.ReferenceQueue<>();
    private static final java.util.Map<java.util.UUID, PlotReference> LAST_CAPTURED_PLOTS = new java.util.concurrent.ConcurrentHashMap<>();

    private static final class PlotReference extends java.lang.ref.WeakReference<net.minecraft.nbt.CompoundTag> {
        private final java.util.UUID bodyId;

        private PlotReference(java.util.UUID bodyId, net.minecraft.nbt.CompoundTag tag) {
            super(tag, PLOT_QUEUE);
            this.bodyId = bodyId;
        }
    }

    private static void prunePlotReferences() {
        PlotReference ref;
        while ((ref = (PlotReference) PLOT_QUEUE.poll()) != null) {
            LAST_CAPTURED_PLOTS.remove(ref.bodyId, ref);
        }
    }

    public static void registerConstraint(java.util.UUID a, java.util.UUID b,
                                          dev.ryanhcode.sable.api.physics.constraint.PhysicsConstraintConfiguration<?> config,
                                          dev.ryanhcode.sable.api.physics.constraint.PhysicsConstraintHandle handle) {
        if ((a != null || b != null) && handle != null) {
            ACTIVE_CONSTRAINTS.add(new ConstraintRecord(java.util.UUID.randomUUID(), a, b, config, handle));
        }
    }

    /** Integration-owned stasis holds must never become historical gameplay joints. */
    public static void forgetConstraint(dev.ryanhcode.sable.api.physics.constraint.PhysicsConstraintHandle handle) {
        ACTIVE_CONSTRAINTS.removeIf(c -> c.handle() == handle);
    }

    public static void registerConstraint(java.util.UUID a, java.util.UUID b, dev.ryanhcode.sable.api.physics.constraint.PhysicsConstraintHandle handle) {
        registerConstraint(a, b, null, handle);
    }

    @Override
    public java.util.Set<java.util.UUID> getConnectedSubLevels(java.util.UUID bodyId) {
        if (bodyId == null) return java.util.Collections.emptySet();
        ACTIVE_CONSTRAINTS.removeIf(c -> c.handle() == null || !c.handle().isValid());
        java.util.Set<java.util.UUID> set = new java.util.HashSet<>();
        for (var c : ACTIVE_CONSTRAINTS) {
            if (c.handle().isValid()) {
                if (bodyId.equals(c.a()) && c.b() != null) set.add(c.b());
                else if (bodyId.equals(c.b()) && c.a() != null) set.add(c.a());
            }
        }
        return set;
    }

    public record PlotKey(net.minecraft.resources.ResourceKey<Level> dimension, net.minecraft.core.BlockPos center) {}
    private final java.util.Map<PlotKey, net.minecraft.core.BlockPos> plotRemapping = new java.util.concurrent.ConcurrentHashMap<>();

    @Override
    public boolean isSubLevelPlotBlock(Level level, net.minecraft.core.BlockPos pos, com.timestop.core.rewind.data.SubLevelSnapshot target) {
        if (level == null || pos == null || target == null || target.plotCenter() == null) return false;
        if (!level.dimension().equals(target.dimension())) return false;
        var container = dev.ryanhcode.sable.api.sublevel.SubLevelContainer.getContainer(level);
        if (container == null || !container.inBounds(pos)) return false;
        int halfSize = (1 << container.getLogPlotSize()) * 8;
        int dx = Math.abs(pos.getX() - target.plotCenter().getX());
        int dz = Math.abs(pos.getZ() - target.plotCenter().getZ());
        return dx <= halfSize && dz <= halfSize;
    }

    @Override
    public net.minecraft.core.BlockPos remapBlockPos(Level level, net.minecraft.core.BlockPos pos) {
        if (level == null || pos == null || plotRemapping.isEmpty()) return pos;
        var container = dev.ryanhcode.sable.api.sublevel.SubLevelContainer.getContainer(level);
        if (container == null || !container.inBounds(pos)) return pos;
        int halfSize = (1 << container.getLogPlotSize()) * 8;
        for (var entry : plotRemapping.entrySet()) {
            if (!entry.getKey().dimension().equals(level.dimension())) continue;
            net.minecraft.core.BlockPos oldCenter = entry.getKey().center();
            int dx = Math.abs(pos.getX() - oldCenter.getX());
            int dz = Math.abs(pos.getZ() - oldCenter.getZ());
            if (dx <= halfSize && dz <= halfSize) {
                net.minecraft.core.Vec3i diff = entry.getValue().subtract(oldCenter);
                net.minecraft.core.BlockPos remapped = pos.offset(diff);
                var chunkPos = new net.minecraft.world.level.ChunkPos(remapped);
                if (container.getChunk(chunkPos) == null) {
                    var plot = container.getPlot(chunkPos);
                    if (plot != null) {
                        plot.newEmptyChunk(chunkPos);
                    }
                }
                return remapped;
            }
        }
        return pos;
    }

    private final java.util.Set<java.util.UUID> aliveAtBegin = new java.util.HashSet<>();

    @Override
    public void captureSubLevelsBegin(net.minecraft.server.MinecraftServer server, com.timestop.core.rewind.data.TickFrame frame) {
        if (server == null || frame == null) return;
        aliveAtBegin.clear();
        prunePlotReferences();
        for (net.minecraft.server.level.ServerLevel level : server.getAllLevels()) {
            var container = dev.ryanhcode.sable.api.sublevel.SubLevelContainer.getContainer(level);
            if (container == null) continue;
            for (var bodyObj : container.getAllSubLevels()) {
                if (!(bodyObj instanceof dev.ryanhcode.sable.sublevel.ServerSubLevel body)) continue;
                if (body.isRemoved()) continue;
                java.util.UUID id = body.getUniqueId();
                aliveAtBegin.add(id);
                unloadedBodies.remove(id);
                if (com.timestop.core.rewind.LocalRewind.containsSubLevel(id)) continue;

                org.joml.Vector3d pos = new org.joml.Vector3d(body.logicalPose().position());
                org.joml.Quaterniond ori = new org.joml.Quaterniond(body.logicalPose().orientation());
                org.joml.Vector3d linVel = new org.joml.Vector3d();
                org.joml.Vector3d angVel = new org.joml.Vector3d();
                var handle = dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle.of(body);
                if (handle != null && handle.isValid()) {
                    handle.getLinearVelocity(linVel);
                    handle.getAngularVelocity(angVel);
                } else {
                    linVel.set(body.latestLinearVelocity);
                    angVel.set(body.latestAngularVelocity);
                }
                var stasis = SableBodyTimeManager.getState(id);
                if (stasis.isHeld) {
                    linVel.set(stasis.storedLinearVelocity);
                    angVel.set(stasis.storedAngularVelocity);
                }
                org.joml.Vector3d linImp = new org.joml.Vector3d(stasis.bufferedLinearImpulse);
                org.joml.Vector3d angImp = new org.joml.Vector3d(stasis.bufferedAngularImpulse);
                double credits = stasis.credits.pendingSeconds();
                org.joml.Vector3d com = SableBodyTimeManager.getBodyCenterOfMassWorld(body);
                lastLiveCom.put(id, new Vec3(com.x, com.y, com.z));
                net.minecraft.core.BlockPos plotCenter = body.getPlot() != null ? body.getPlot().getCenterBlock() : null;

                net.minecraft.nbt.CompoundTag plotData = null;
                if (body.getPlot() instanceof dev.ryanhcode.sable.sublevel.plot.ServerLevelPlot plot) {
                    try {
                        net.minecraft.nbt.CompoundTag currentPlot = plot.save();
                        PlotReference previous = LAST_CAPTURED_PLOTS.get(id);
                        net.minecraft.nbt.CompoundTag prevPlot = previous == null ? null : previous.get();
                        if (prevPlot != null && prevPlot.equals(currentPlot)) {
                            plotData = prevPlot;
                        } else {
                            LAST_CAPTURED_PLOTS.put(id, new PlotReference(id, currentPlot));
                            plotData = currentPlot;
                        }
                    } catch (Exception ignored) {}
                }

                ACTIVE_CONSTRAINTS.removeIf(c -> c.handle() == null || !c.handle().isValid());
                java.util.Map<java.util.UUID, Object> constraintConfigs = new java.util.HashMap<>();
                java.util.Set<java.util.UUID> connected = new java.util.HashSet<>();
                for (var c : ACTIVE_CONSTRAINTS) {
                    if (c.handle() != null && c.handle().isValid()) {
                        if (id.equals(c.a())) {
                            if (c.b() != null) connected.add(c.b());
                        } else if (id.equals(c.b())) {
                            if (c.a() != null) connected.add(c.a());
                        } else {
                            continue;
                        }
                        constraintConfigs.put(c.id(), new SavedConstraint(c.a(), c.b(),
                                copyConstraint(c.config(), new Vector3d(), new Vector3d()), SableConstraintState.capture(c.handle())));
                    }
                }

                frame.putSubLevelSnapshot(new com.timestop.core.rewind.data.SubLevelSnapshot(
                        id, level.dimension(), pos, ori, linVel, angVel, linImp, angImp, credits, false,
                        new org.joml.Vector3d(com), plotCenter, plotData, connected, constraintConfigs
                ));
            }
        }
        LAST_CAPTURED_PLOTS.keySet().retainAll(aliveAtBegin);
    }

    @Override
    public void captureSubLevelsFinish(net.minecraft.server.MinecraftServer server, com.timestop.core.rewind.data.TickFrame frame) {
        if (server == null || frame == null) return;
        for (net.minecraft.server.level.ServerLevel level : server.getAllLevels()) {
            var container = dev.ryanhcode.sable.api.sublevel.SubLevelContainer.getContainer(level);
            if (container == null) continue;
            for (var bodyObj : container.getAllSubLevels()) {
                if (!(bodyObj instanceof dev.ryanhcode.sable.sublevel.ServerSubLevel body)) continue;
                if (body.isRemoved()) continue;
                java.util.UUID id = body.getUniqueId();
                if (!aliveAtBegin.contains(id)) {
                    // Loading an existing body is not a new assembly to remove on rewind.
                    if (unloadedBodies.remove(id)) continue;
                    org.joml.Vector3d pos = new org.joml.Vector3d(body.logicalPose().position());
                    org.joml.Quaterniond ori = new org.joml.Quaterniond(body.logicalPose().orientation());
                    org.joml.Vector3d com = SableBodyTimeManager.getBodyCenterOfMassWorld(body);
                    lastLiveCom.put(id, new Vec3(com.x, com.y, com.z));
                    net.minecraft.core.BlockPos plotCenter = body.getPlot() != null ? body.getPlot().getCenterBlock() : null;
                    frame.putSubLevelSnapshot(new com.timestop.core.rewind.data.SubLevelSnapshot(
                            id, level.dimension(), pos, ori,
                            new org.joml.Vector3d(), new org.joml.Vector3d(),
                            new org.joml.Vector3d(), new org.joml.Vector3d(),
                            0.0, true, new org.joml.Vector3d(com), plotCenter, null, java.util.Collections.emptySet(), java.util.Collections.emptyMap()
                    ));
                }
            }
        }
    }

    @Override
    public void validateRewind(net.minecraft.server.MinecraftServer server, RewindPlan plan) {
        java.util.Map<dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer, Integer> allocations = new java.util.HashMap<>();
        java.util.Map<java.util.UUID, SavedConstraint> joints = new java.util.HashMap<>();
        for (var entry : plan.subLevelTargetStates().entrySet()) {
            var snap = entry.getValue();
            if (!entry.getKey().equals(snap.bodyId())) throw new IllegalStateException("Rewind body identity mismatch");
            if (unloadedBodies.contains(snap.bodyId())) throw new IllegalStateException("Rewind requires an unloaded ship: " + snap.bodyId());
            var level = server.getLevel(snap.dimension());
            var container = level == null ? null : SubLevelContainer.getContainer(level);
            if (container == null) throw new IllegalStateException("Rewind dimension is unavailable: " + snap.dimension());
            if (!snap.position().isFinite() || !snap.orientation().isFinite() || !snap.linearVelocity().isFinite()
                    || !snap.angularVelocity().isFinite() || !snap.bufferedLinearImpulse().isFinite()
                    || !snap.bufferedAngularImpulse().isFinite() || !Double.isFinite(snap.pendingSeconds()) || snap.pendingSeconds() < 0)
                throw new IllegalStateException("Invalid recorded ship motion: " + snap.bodyId());
            var live = container.getSubLevel(snap.bodyId());
            if (!snap.removed() && snap.plotData() != null) SablePlotHistory.validate(level, container, snap.plotData());
            if (!snap.removed() && (live == null || live.isRemoved())) {
                if (snap.plotCenter() == null || snap.plotData() == null)
                    throw new IllegalStateException("Missing structural history for ship: " + snap.bodyId());
                allocations.merge(container, 1, Integer::sum);
            }
            if (!snap.removed() && snap.constraintConfigs() != null) {
                for (var joint : snap.constraintConfigs().entrySet()) {
                    if (!(joint.getValue() instanceof SavedConstraint saved) || saved.config() == null || saved.settings() == null
                            || (saved.bodyA() == null && saved.bodyB() == null))
                        throw new IllegalStateException("Missing recorded joint configuration: " + joint.getKey());
                    var previous = joints.putIfAbsent(joint.getKey(), saved);
                    if (previous != null && !previous.equals(saved)) throw new IllegalStateException("Inconsistent recorded joint: " + joint.getKey());
                    for (var endpoint : new java.util.UUID[]{saved.bodyA(), saved.bodyB()}) {
                        if (endpoint == null) continue;
                        var target = plan.subLevelTargetStates().get(endpoint);
                        if (target == null || target.removed() || !target.dimension().equals(snap.dimension()))
                            throw new IllegalStateException("Missing rewind joint endpoint: " + endpoint);
                    }
                }
            }
        }
        allocations.forEach((container, required) -> {
            int side = 1 << container.getLogSideLength();
            if (side * side - container.getOccupancy().cardinality() < required)
                throw new IllegalStateException("Insufficient free Sable plots for rewind");
        });
    }

    private static org.joml.Vector2i freePlot(dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer container) {
        int side = 1 << container.getLogSideLength();
        for (int x = 0; x < side; x++) for (int z = 0; z < side; z++) {
            // getSubLevel is null for unloaded plots too; occupancy is the allocation authority.
            if (!container.getOccupancy().get(container.getIndex(x, z))) return new org.joml.Vector2i(x, z);
        }
        throw new IllegalStateException("No free Sable plot for rewind");
    }

    @Override
    public void preRewind(net.minecraft.server.MinecraftServer server, com.timestop.core.rewind.RewindPlan plan) {
        deckAnchors.clear();
        plotRemapping.clear();
        if (server == null || plan == null) return;
        for (var entry : plan.subLevelTargetStates().entrySet()) {
            var snap = entry.getValue();
            var level = server.getLevel(snap.dimension());
            if (level == null) continue;
            var container = dev.ryanhcode.sable.api.sublevel.SubLevelContainer.getContainer(level);
            if (container == null) continue;
            var sub = container.getSubLevel(entry.getKey());
            if (sub instanceof dev.ryanhcode.sable.sublevel.ServerSubLevel serverSub && !serverSub.isRemoved()) {
                container.physicsSystem().getPipeline().resetVelocity(serverSub);
            } else if (!snap.removed()) {
                // Pre-allocate recreated sublevel before block restoration so plot allocation is established
                var free = freePlot(container);
                int foundX = free.x, foundZ = free.y;
                if (foundX != -1) {
                    var pose = new dev.ryanhcode.sable.companion.math.Pose3d();
                    pose.position().set(snap.position());
                    pose.orientation().set(snap.orientation());
                    var allocated = container.allocateSubLevel(entry.getKey(), foundX, foundZ, pose);
                    if (allocated != null && allocated.getPlot() != null) {
                        if (snap.plotData() != null && allocated.getPlot() instanceof dev.ryanhcode.sable.sublevel.plot.ServerLevelPlot serverPlot) {
                            serverPlot.load(SablePlotHistory.prepare(snap.plotData(), snap.plotCenter(), serverPlot.getCenterBlock()));
                        } else throw new IllegalStateException("Missing structural history for allocated ship " + entry.getKey());
                        if (snap.plotCenter() != null) {
                            net.minecraft.core.BlockPos newCenter = allocated.getPlot().getCenterBlock();
                            if (!snap.plotCenter().equals(newCenter)) {
                                plotRemapping.put(new PlotKey(snap.dimension(), snap.plotCenter()), newCenter);
                            }
                        }
                    }
                }
            }
        }
    }

    @Override
    public void applySubLevelRewind(net.minecraft.server.MinecraftServer server, com.timestop.core.rewind.RewindPlan plan) {
        if (server == null || plan == null) return;
        for (var entry : plan.subLevelTargetStates().entrySet()) {
            java.util.UUID id = entry.getKey();
            var target = entry.getValue();
            var level = server.getLevel(target.dimension());
            if (level == null) continue;
            var container = dev.ryanhcode.sable.api.sublevel.SubLevelContainer.getContainer(level);
            if (container == null) continue;
            var sub = container.getSubLevel(id);

            if (target.removed()) {
                if (sub instanceof dev.ryanhcode.sable.sublevel.ServerSubLevel serverSub && !serverSub.isRemoved()) {
                    container.removeSubLevel(serverSub, dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason.REMOVED);
                    SableBodyTimeManager.onBodyRemoved(serverSub);
                }
            } else {
                if (sub == null || (sub instanceof dev.ryanhcode.sable.sublevel.ServerSubLevel s && s.isRemoved())) {
                    throw new IllegalStateException("Rewind ship was not prepared: " + id);
                }

                if (sub instanceof dev.ryanhcode.sable.sublevel.ServerSubLevel body && !body.isRemoved()) {
                    SablePlotHistory.restoreContents(body, target);
                    body.logicalPose().position().set(target.position());
                    body.logicalPose().orientation().set(target.orientation());

                    var pipeline = container.physicsSystem().getPipeline();
                    pipeline.teleport(body, target.position(), target.orientation());

                    pipeline.resetVelocity(body);
                    pipeline.addLinearAndAngularVelocity(body, target.linearVelocity(), target.angularVelocity());
                    body.latestLinearVelocity.set(target.linearVelocity());
                    body.latestAngularVelocity.set(target.angularVelocity());

                    var state = SableBodyTimeManager.getState(id);
                    state.heldPosition.set(target.position());
                    state.heldOrientation.set(target.orientation());
                    state.storedLinearVelocity.set(target.linearVelocity());
                    state.storedAngularVelocity.set(target.angularVelocity());
                    state.bufferedLinearImpulse.set(target.bufferedLinearImpulse());
                    state.bufferedAngularImpulse.set(target.bufferedAngularImpulse());
                    state.credits.restore(target.pendingSeconds());

                    if (state.isHeld) {
                        if (state.constraintHandle != null) {
                            try {
                                if (state.constraintHandle.isValid()) state.constraintHandle.remove();
                            } catch (Exception ignored) {}
                            state.constraintHandle = null;
                        }
                        try {
                            state.constraintHandle = SableBodyTimeManager.createHoldConstraint(container.physicsSystem(), body);
                        } catch (Exception ignored) {
                            state.constraintHandle = null;
                        }
                        pipeline.resetVelocity(body);
                    }

                    body.updateMergedMassData(1.0f);
                    pipeline.onStatsChanged(body);
                }
            }
        }
        // All endpoints must exist and have their restored poses/mass before rebuilding joints.
        reconcileConstraints(server, plan);
    }

    private static PhysicsConstraintConfiguration<?> copyConstraint(PhysicsConstraintConfiguration<?> config,
                                                                      Vector3d offsetA, Vector3d offsetB) {
        if (config == null) return null;
        return switch (config) {
            case FixedConstraintConfiguration c -> new FixedConstraintConfiguration(
                    new Vector3d(c.pos1()).add(offsetA), new Vector3d(c.pos2()).add(offsetB), new Quaterniond(c.orientation()));
            case FreeConstraintConfiguration c -> new FreeConstraintConfiguration(
                    new Vector3d(c.pos1()).add(offsetA), new Vector3d(c.pos2()).add(offsetB), new Quaterniond(c.orientation()));
            case RotaryConstraintConfiguration c -> new RotaryConstraintConfiguration(
                    new Vector3d(c.pos1()).add(offsetA), new Vector3d(c.pos2()).add(offsetB),
                    new Vector3d(c.normal1()), new Vector3d(c.normal2()));
            case GenericConstraintConfiguration c -> new GenericConstraintConfiguration(
                    new Vector3d(c.pos1()).add(offsetA), new Vector3d(c.pos2()).add(offsetB),
                    new Quaterniond(c.orientation1()), new Quaterniond(c.orientation2()), java.util.Set.copyOf(c.lockedAxes()));
        };
    }

    private static Vector3d plotOffset(RewindPlan plan, dev.ryanhcode.sable.sublevel.ServerSubLevel body) {
        if (body == null) return new Vector3d();
        var snapshot = plan.subLevelTargetStates().get(body.getUniqueId());
        if (snapshot == null || snapshot.plotCenter() == null) return new Vector3d();
        var offset = body.getPlot().getCenterBlock().subtract(snapshot.plotCenter());
        return new Vector3d(offset.getX(), offset.getY(), offset.getZ());
    }

    private static void reconcileConstraints(net.minecraft.server.MinecraftServer server, RewindPlan plan) {
        java.util.Map<java.util.UUID, SavedConstraint> desired = new java.util.HashMap<>();
        java.util.Map<java.util.UUID, dev.ryanhcode.sable.sublevel.ServerSubLevel> bodies = new java.util.HashMap<>();
        for (var snapshot : plan.subLevelTargetStates().values()) {
            if (snapshot.removed()) continue;
            var level = server.getLevel(snapshot.dimension());
            var container = level == null ? null : SubLevelContainer.getContainer(level);
            if (container == null) continue;
            var body = container.getSubLevel(snapshot.bodyId());
            if (body instanceof dev.ryanhcode.sable.sublevel.ServerSubLevel live && !live.isRemoved()) {
                bodies.put(snapshot.bodyId(), live);
            }
            if (snapshot.constraintConfigs() != null) {
                snapshot.constraintConfigs().forEach((id, value) -> {
                    if (value instanceof SavedConstraint saved) desired.putIfAbsent(id, saved);
                });
            }
        }
        // Build the target configs in current plot coordinates without mutating history.
        desired.replaceAll((id, saved) -> {
            var a = bodies.get(saved.bodyA());
            var b = bodies.get(saved.bodyB());
            if ((saved.bodyA() != null && a == null) || (saved.bodyB() != null && b == null) || saved.config() == null) {
                throw new IllegalStateException("Missing rewind constraint endpoint/configuration: " + id);
            }
            return new SavedConstraint(saved.bodyA(), saved.bodyB(),
                    copyConstraint(saved.config(), plotOffset(plan, a), plotOffset(plan, b)), saved.settings());
        });
        ACTIVE_CONSTRAINTS.removeIf(c -> c.handle() == null || !c.handle().isValid());
        for (var live : new java.util.ArrayList<>(ACTIVE_CONSTRAINTS)) {
            if ((live.a() == null || !plan.subLevelTargetStates().containsKey(live.a()))
                    && (live.b() == null || !plan.subLevelTargetStates().containsKey(live.b()))) continue;
            var target = desired.get(live.id());
            if (target == null || !java.util.Objects.equals(live.a(), target.bodyA()) || !java.util.Objects.equals(live.b(), target.bodyB())
                    || !java.util.Objects.equals(live.config(), target.config())
                    || !SableConstraintState.capture(live.handle()).equals(target.settings())) {
                live.handle().remove();
                ACTIVE_CONSTRAINTS.remove(live);
            }
        }
        for (var entry : desired.entrySet()) {
            if (ACTIVE_CONSTRAINTS.stream().anyMatch(c -> c.id().equals(entry.getKey()))) continue;
            var target = entry.getValue();
            var a = bodies.get(target.bodyA());
            var b = bodies.get(target.bodyB());
            var pipeline = SubLevelContainer.getContainer((a != null ? a : b).getLevel()).physicsSystem().getPipeline();
            var handle = pipeline.addConstraint(a, b, target.config());
            if (handle == null || !handle.isValid()) throw new IllegalStateException("Could not restore constraint " + entry.getKey());
            // The add hook registers a new native handle. Retain the historical joint identity across replacement.
            ACTIVE_CONSTRAINTS.removeIf(c -> c.handle() == handle);
            ACTIVE_CONSTRAINTS.add(new ConstraintRecord(entry.getKey(), target.bodyA(), target.bodyB(), target.config(), handle));
            target.settings().apply(handle);
        }
    }

    @Override
    public void postRewind(net.minecraft.server.MinecraftServer server, com.timestop.core.rewind.RewindPlan plan) {
        plotRemapping.clear();
        com.timestop.pedestal.PedestalManager.serverTick();
    }

    public void clearTrackingData() {
        lastLiveCom.clear();
        unloadedBodies.clear();
        ACTIVE_CONSTRAINTS.clear();
        LAST_CAPTURED_PLOTS.clear();
        prunePlotReferences();
    }
}
