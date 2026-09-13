package com.timestop.combat;

import com.timestop.item.rune.RuneType;
import com.timestop.network.KineticPalmActionPacket;
import com.timestop.network.ModMessages;
import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.network.protocol.game.ClientboundTeleportEntityPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.Vec3;
import com.timestop.platform.Services;
import net.minecraft.world.level.Level;

import java.lang.ref.WeakReference;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class KineticPalmManager {


    private static final Map<UUID, List<WeakReference<Projectile>>> capturedProjectiles = new ConcurrentHashMap<>();
    private static final Set<UUID> guardingPlayers = ConcurrentHashMap.newKeySet();

    public static final double MAX_HOLD_DISTANCE = 5.0;

    // Client-side tracking
    public static boolean clientGuarding = false;
    private static boolean repulsedUntilRelease = false;

        public static boolean isGuarding(Player player) {
        if (player == null) return false;
        if (player.level().isClientSide) {
            return clientGuarding && player.getUUID().equals(Minecraft.getInstance().player != null ? Minecraft.getInstance().player.getUUID() : null);
        }
        return guardingPlayers.contains(player.getUUID());
    }

    public static void setGuarding(ServerPlayer player, boolean guarding) {
        if (guarding) {
            if (!RuneManager.hasRune(player, RuneType.KINETIC_BARRIER)) {
                return;
            }
            guardingPlayers.add(player.getUUID());
        } else {
            guardingPlayers.remove(player.getUUID());
        }
    }

    public static int getCapturedCount(Player player) {
        List<WeakReference<Projectile>> list = capturedProjectiles.get(player.getUUID());
        if (list == null) return 0;
        cleanProjectileList(list);
        return list.size();
    }

    public static List<Projectile> getCapturedProjectilesList(Player player) {
        if (player.level().isClientSide) {
            return player.level().getEntitiesOfClass(Projectile.class, player.getBoundingBox().inflate(8),
                    p -> com.timestop.platform.EntityDataHelper.getPersistentData(p).hasUUID("KineticPalmOwner")
                            && com.timestop.platform.EntityDataHelper.getPersistentData(p).getUUID("KineticPalmOwner").equals(player.getUUID()));
        }
        List<WeakReference<Projectile>> list = capturedProjectiles.get(player.getUUID());
        if (list == null) return Collections.emptyList();
        cleanProjectileList(list);
        List<Projectile> result = new ArrayList<>();
        for (WeakReference<Projectile> ref : list) {
            Projectile p = ref.get();
            if (p != null && p.isAlive()) result.add(p);
        }
        return result;
    }

    private static void cleanProjectileList(List<WeakReference<Projectile>> list) {
        list.removeIf(ref -> {
            Projectile p = ref.get();
            return p == null || !p.isAlive() || p.onGround();
        });
    }

    private static boolean isAllied(Player defender, Entity shooter) {
        if (shooter == defender) return true;
        if (shooter instanceof Player otherPlayer) {
            if (defender.getTeam() != null && otherPlayer.getTeam() != null && defender.getTeam().isAlliedTo(otherPlayer.getTeam())) {
                return true;
            }
            if (com.timestop.sync.SyncManager.isSynced(defender.getUUID(), otherPlayer.getUUID())) {
                return true;
            }
        }
        return false;
    }

    // ==========================================
    // CLIENT TICK: GUARD DETECTION & REPULSE INPUT
    // ==========================================
        public static void clientTick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.screen != null) {
            if (clientGuarding) {
                clientGuarding = false;
                if (mc.player != null && mc.getConnection() != null) {
                    ModMessages.sendToServer(new KineticPalmActionPacket(KineticPalmActionPacket.Action.STOP_GUARD_DROP, Vec3.ZERO));
                }
            }
            repulsedUntilRelease = false;
            return;
        }

        // Check guard condition: Holding Middle Mouse Button / Barrier Key with Rune socketed
        boolean barrierKeyDown = com.timestop.client.ModKeyBindings.KINETIC_BARRIER_KEY.isDown();
        if (!barrierKeyDown) repulsedUntilRelease = false;
        boolean hasRune = RuneManager.hasRune(mc.player, RuneType.KINETIC_BARRIER);

        boolean canGuard = barrierKeyDown && hasRune && !repulsedUntilRelease;

        if (canGuard) {
            if (!clientGuarding) {
                clientGuarding = true;
                ModMessages.sendToServer(new KineticPalmActionPacket(KineticPalmActionPacket.Action.START_GUARD, Vec3.ZERO));
            }

            // Repulse triggered by Left Click / Attack while guarding
            if (mc.options.keyAttack.isDown()) {
                clientGuarding = false;
                repulsedUntilRelease = true;
                mc.player.swing(InteractionHand.MAIN_HAND, true);
                ModMessages.sendToServer(new KineticPalmActionPacket(KineticPalmActionPacket.Action.REPULSE, mc.player.getLookAngle()));
            }
        } else {
            if (clientGuarding) {
                clientGuarding = false;
                ModMessages.sendToServer(new KineticPalmActionPacket(KineticPalmActionPacket.Action.STOP_GUARD_DROP, Vec3.ZERO));
            }
        }

    }

    // ==========================================
    // SERVER TICK: CONE PROJECTION & BULLET CAPTURE
    // ==========================================
    public static void serverTick() {
        if (guardingPlayers.isEmpty()) return;
        net.minecraft.server.MinecraftServer server = Services.PLATFORM.getCurrentServer();
        if (server == null) return;

        for (UUID playerUuid : new ArrayList<>(guardingPlayers)) {
            ServerPlayer player = server.getPlayerList().getPlayer(playerUuid);
            if (player == null || !player.isAlive()) {
                guardingPlayers.remove(playerUuid);
                capturedProjectiles.remove(playerUuid);
                continue;
            }

            if (!RuneManager.hasRune(player, RuneType.KINETIC_BARRIER)) {
                setGuarding(player, false);
                dischargeDrop(player);
                continue;
            }

            ServerLevel level = player.serverLevel();
            List<WeakReference<Projectile>> list = capturedProjectiles.get(playerUuid);
            if (list == null) continue;
            cleanProjectileList(list);
            for (WeakReference<Projectile> ref : list) {
                Projectile projectile = ref.get();
                if (projectile != null && releaseIfTooFar(player, projectile)) {
                    list.remove(ref);
                }
            }
        }
    }

    public static void interceptIncoming(Projectile projectile) {
        if (guardingPlayers.isEmpty() || !(projectile.level() instanceof ServerLevel level) || !projectile.isAlive()
                || projectile.onGround() || com.timestop.platform.EntityDataHelper.getPersistentData(projectile).getBoolean("KineticPalmCaptured")
                || com.timestop.platform.EntityDataHelper.getPersistentData(projectile).getBoolean("KineticPalmDropped")
                || com.timestop.platform.EntityDataHelper.getPersistentData(projectile).getBoolean("InStasisOrbit")) return;
        for (ServerPlayer player : level.players()) {
            if (!guardingPlayers.contains(player.getUUID()) || !player.isAlive()
                    || !RuneManager.hasRune(player, RuneType.KINETIC_BARRIER)
                    || ProjectileCombatHelper.wasReleasedBy(projectile, player)
                    || (projectile.getOwner() != player && isAllied(player, projectile.getOwner()))) continue;
            List<WeakReference<Projectile>> list = capturedProjectiles.computeIfAbsent(player.getUUID(),
                    k -> new CopyOnWriteArrayList<>());
            cleanProjectileList(list);
            if (list.size() >= 64) continue;
            Vec3 incoming = ProjectileCombatHelper.incomingVelocity(projectile);
            Vec3 entry = projectile.getOwner() == player
                    ? NeoBulletMotion.ownShotEntryPoint(projectile.position(), incoming, player.getEyePosition(), player.getLookAngle())
                    : NeoBulletMotion.entryPoint(projectile.position(), incoming, player.getEyePosition(), player.getLookAngle());
            if (entry == null) continue;
            // Do not pull a bullet through a solid wall on its way into the stopping field.
            var hit = level.clip(new net.minecraft.world.level.ClipContext(projectile.position(), entry,
                    net.minecraft.world.level.ClipContext.Block.COLLIDER,
                    net.minecraft.world.level.ClipContext.Fluid.NONE, projectile));
            if (hit.getType() != net.minecraft.world.phys.HitResult.Type.MISS) continue;
            projectile.setPos(entry);
            var captureData = com.timestop.platform.EntityDataHelper.getPersistentData(projectile);
            captureData.putDouble("NeoIncomingX", incoming.x);
            captureData.putDouble("NeoIncomingY", incoming.y);
            captureData.putDouble("NeoIncomingZ", incoming.z);
            captureData.putBoolean("NeoOriginalNoGravity", projectile.isNoGravity());
            Vec3 drift = NeoBulletMotion.initialDrift(incoming, entry.distanceTo(player.getEyePosition()));
            setDrift(projectile, drift);
            com.timestop.platform.EntityDataHelper.getPersistentData(projectile).putBoolean("KineticPalmCaptured", true);
            com.timestop.platform.EntityDataHelper.getPersistentData(projectile).putUUID("KineticPalmOwner", player.getUUID());
            projectile.setNoGravity(true);
            projectile.setDeltaMovement(Vec3.ZERO);
            com.timestop.core.TimeStopManager.removeSuspendedProjectile(projectile);
            list.add(new WeakReference<>(projectile));
            syncCapture(projectile, player.getUUID(), true);
            return;
        }
    }

    public static boolean captureOnImpact(ServerPlayer player, Projectile projectile) {
        if (!isGuarding(player) || !RuneManager.hasRune(player, RuneType.KINETIC_BARRIER)) return false;
        if (com.timestop.platform.EntityDataHelper.getPersistentData(projectile).getBoolean("KineticPalmCaptured")) return true;

        List<WeakReference<Projectile>> list = capturedProjectiles.computeIfAbsent(player.getUUID(),
                k -> new CopyOnWriteArrayList<>());
        cleanProjectileList(list);
        if (list.size() >= 64) return false;

        Vec3 look = player.getLookAngle();
        Vec3 eye = player.getEyePosition();
        Vec3 entry = eye.add(look.scale(1.5));

        projectile.setPos(entry);
        var captureData = com.timestop.platform.EntityDataHelper.getPersistentData(projectile);
        Vec3 incoming = ProjectileCombatHelper.incomingVelocity(projectile);
        captureData.putDouble("NeoIncomingX", incoming.x);
        captureData.putDouble("NeoIncomingY", incoming.y);
        captureData.putDouble("NeoIncomingZ", incoming.z);
        captureData.putBoolean("NeoOriginalNoGravity", projectile.isNoGravity());
        setDrift(projectile, Vec3.ZERO);
        captureData.putBoolean("KineticPalmCaptured", true);
        captureData.putUUID("KineticPalmOwner", player.getUUID());
        projectile.setNoGravity(true);
        projectile.setDeltaMovement(Vec3.ZERO);
        com.timestop.core.TimeStopManager.removeSuspendedProjectile(projectile);
        list.add(new WeakReference<>(projectile));
        syncCapture(projectile, player.getUUID(), true);

        player.level().playSound(null, entry.x, entry.y, entry.z, SoundEvents.SHIELD_BLOCK, SoundSource.PLAYERS, 1.2F, 1.5F);
        return true;
    }

    private static void setDrift(Projectile projectile, Vec3 drift) {
        var data = com.timestop.platform.EntityDataHelper.getPersistentData(projectile);
        data.putDouble("NeoDriftX", drift.x);
        data.putDouble("NeoDriftY", drift.y);
        data.putDouble("NeoDriftZ", drift.z);
    }

    public static void tickCaptured(Projectile projectile) {
        projectile.setOldPosAndRot();
        var data = com.timestop.platform.EntityDataHelper.getPersistentData(projectile);
        Vec3 drift = new Vec3(data.getDouble("NeoDriftX"), data.getDouble("NeoDriftY"), data.getDouble("NeoDriftZ"));
        if (drift.lengthSqr() > 0.000001) {
            Vec3 target = projectile.position().add(drift);
            var hit = projectile.level().clip(new net.minecraft.world.level.ClipContext(projectile.position(), target,
                    net.minecraft.world.level.ClipContext.Block.COLLIDER,
                    net.minecraft.world.level.ClipContext.Fluid.NONE, projectile));
            if (hit.getType() == net.minecraft.world.phys.HitResult.Type.MISS) {
                projectile.setPos(target);
                setDrift(projectile, drift.scale(NeoBulletMotion.DRAG));
            } else {
                setDrift(projectile, Vec3.ZERO);
            }
            if (data.hasUUID("KineticPalmOwner")) syncCapture(projectile, data.getUUID("KineticPalmOwner"), true);
        }
        projectile.setDeltaMovement(Vec3.ZERO);
    }

    // ==========================================
    // DISCHARGE 1: DROP (METALLIC CASING CLATTER)
    // ==========================================
    public static void dischargeDrop(ServerPlayer player) {
        List<WeakReference<Projectile>> list = capturedProjectiles.remove(player.getUUID());
        if (list == null || list.isEmpty()) return;

        for (WeakReference<Projectile> ref : list) {
            Projectile projectile = ref.get();
            if (projectile != null && projectile.isAlive()) dropProjectile(projectile, player.getUUID());
        }
    }

    public static boolean canKeepSuspended(Player player, Projectile projectile) {
        return player.level() == projectile.level()
                && projectile.position().distanceToSqr(player.getEyePosition()) <= MAX_HOLD_DISTANCE * MAX_HOLD_DISTANCE;
    }

    public static boolean releaseIfTooFar(Player player, Projectile projectile) {
        if (player.level().isClientSide || canKeepSuspended(player, projectile)) return false;
        dropProjectile(projectile, player.getUUID());
        return true;
    }

    private static void dropProjectile(Projectile projectile, UUID owner) {
        var level = (ServerLevel) projectile.level();
        var data = com.timestop.platform.EntityDataHelper.getPersistentData(projectile);
        data.remove("KineticPalmCaptured");
        data.remove("KineticPalmOwner");
        data.putBoolean("KineticPalmDropped", true);
        syncCapture(projectile, owner, false);
        projectile.setNoGravity(false);
        if (projectile instanceof net.minecraft.world.entity.projectile.AbstractHurtingProjectile fireball) {
            fireball.xPower = fireball.yPower = fireball.zPower = 0;
        }
        projectile.setDeltaMovement(new Vec3((level.random.nextDouble() - 0.5) * 0.05,
                -0.22, (level.random.nextDouble() - 0.5) * 0.05));
        projectile.hasImpulse = true;
        level.getChunkSource().broadcast(projectile, new ClientboundTeleportEntityPacket(projectile));
        level.getChunkSource().broadcast(projectile, new ClientboundSetEntityMotionPacket(projectile));
    }

    // ==========================================
    // DISCHARGE 2: KINETIC REPULSE (SHOTGUN VOLLEY)
    // ==========================================
    public static void dischargeRepulse(ServerPlayer player, Vec3 aimVector) {
        if (!guardingPlayers.contains(player.getUUID())) return;
        if (!RuneManager.hasRune(player, RuneType.KINETIC_BARRIER)) {
            dischargeDrop(player);
            return;
        }
        List<WeakReference<Projectile>> list = capturedProjectiles.remove(player.getUUID());
        ServerLevel level = player.serverLevel();

        int count = 0;
        if (list != null && !list.isEmpty()) {
            for (WeakReference<Projectile> ref : list) {
                Projectile p = ref.get();
                if (p == null || !p.isAlive()) continue;

                Vec3 incoming = capturedVelocity(p);
                // Returning an outgoing shot to its own shooter would send an RPG back into the defender.
                Vec3 shootDir = p.getOwner() == player ? player.getLookAngle().normalize()
                        : ProjectileRedirection.direction(p, player, p.getOwner(), incoming);
                releaseCaptured(p, player);
                ProjectileRedirection.clearGuidance(p);
                com.timestop.platform.EntityDataHelper.getPersistentData(p).putBoolean("KineticPalmRepulsed", true);
                p.setOwner(player);
                p.setNoGravity(com.timestop.platform.EntityDataHelper.getPersistentData(p).getBoolean("NeoOriginalNoGravity"));
                double speed = Math.max(3.2, incoming.length());
                p.setDeltaMovement(shootDir.scale(speed));
                if (p instanceof net.minecraft.world.entity.projectile.AbstractHurtingProjectile fireball) {
                    fireball.xPower = shootDir.x * 0.1;
                    fireball.yPower = shootDir.y * 0.1;
                    fireball.zPower = shootDir.z * 0.1;
                }
                p.hasImpulse = true;

                if (p instanceof AbstractArrow arrow) {
                    arrow.setBaseDamage(arrow.getBaseDamage() + 6.0);
                    arrow.setCritArrow(true);
                    arrow.setPierceLevel((byte) 3);
                }

                double horiz = Math.sqrt(shootDir.x * shootDir.x + shootDir.z * shootDir.z);
                float yRot = (float) (Mth.atan2(shootDir.x, shootDir.z) * (180.0D / Math.PI));
                float xRot = (float) (Mth.atan2(shootDir.y, horiz) * (180.0D / Math.PI));
                p.setYRot(yRot);
                p.setXRot(xRot);
                p.yRotO = yRot;
                p.xRotO = xRot;

                level.getChunkSource().broadcast(p, new ClientboundTeleportEntityPacket(p));
                level.getChunkSource().broadcast(p, new ClientboundSetEntityMotionPacket(p));
                count++;
            }
        }

        if (count > 0) {
            level.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.ARROW_SHOOT, SoundSource.PLAYERS, 0.45F, 0.7F);
        }
    }

    private static Vec3 capturedVelocity(Projectile projectile) {
        var data = com.timestop.platform.EntityDataHelper.getPersistentData(projectile);
        return new Vec3(data.getDouble("NeoIncomingX"), data.getDouble("NeoIncomingY"), data.getDouble("NeoIncomingZ"));
    }

    public static boolean releaseCaptured(Projectile projectile, Player player) {
        var data = com.timestop.platform.EntityDataHelper.getPersistentData(projectile);
        if (!data.getBoolean("KineticPalmCaptured") || !data.hasUUID("KineticPalmOwner")
                || !data.getUUID("KineticPalmOwner").equals(player.getUUID())) return false;
        var list = capturedProjectiles.get(player.getUUID());
        if (list != null) list.removeIf(ref -> ref.get() == projectile);
        ProjectileCombatHelper.markReleased(projectile, player);
        data.remove("KineticPalmCaptured");
        data.remove("KineticPalmOwner");
        projectile.setNoGravity(data.getBoolean("NeoOriginalNoGravity"));
        projectile.setDeltaMovement(capturedVelocity(projectile));
        syncCapture(projectile, player.getUUID(), false);
        return true;
    }

    // Clean up dropped projectiles when they hit the floor
    public static boolean onDroppedProjectileImpact(Projectile p) {
        if (com.timestop.platform.EntityDataHelper.getPersistentData(p).getBoolean("KineticPalmDropped")) {
            if (p.level() instanceof ServerLevel sl) {
                sl.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.CHAIN_HIT, SoundSource.PLAYERS, 0.2F, 1.4F);
            }
            p.discard();
            return true;
        }
        return false;
    }

    private static void syncCapture(Projectile projectile, UUID owner, boolean captured) {
        ModMessages.sendToTracking(new com.timestop.network.KineticCaptureSyncPacket(projectile.getId(), owner, captured, projectile.position()), projectile);
    }

    public static void onStartTracking(ServerPlayer player, Entity target) {
        if (target instanceof Projectile projectile
                && com.timestop.platform.EntityDataHelper.getPersistentData(projectile).getBoolean("KineticPalmCaptured")
                && com.timestop.platform.EntityDataHelper.getPersistentData(projectile).hasUUID("KineticPalmOwner")) {
            ModMessages.sendToPlayer(new com.timestop.network.KineticCaptureSyncPacket(projectile.getId(),
                    com.timestop.platform.EntityDataHelper.getPersistentData(projectile).getUUID("KineticPalmOwner"), true, projectile.position()), player);
        }
    }

    public static void clearAll() {
        for (var list : capturedProjectiles.values()) {
            for (var ref : list) {
                Projectile projectile = ref.get();
                if (projectile != null && projectile.isAlive()) {
                    com.timestop.platform.EntityDataHelper.getPersistentData(projectile).remove("KineticPalmCaptured");
                    com.timestop.platform.EntityDataHelper.getPersistentData(projectile).remove("KineticPalmOwner");
                    projectile.setNoGravity(false);
                }
            }
        }
        capturedProjectiles.clear();
        guardingPlayers.clear();
    }

    public static void onProjectileLoaded(Entity entity, Level level) {
        if (!level.isClientSide && entity instanceof Projectile projectile
                && com.timestop.platform.EntityDataHelper.getPersistentData(projectile).getBoolean("KineticPalmCaptured")) {
            com.timestop.platform.EntityDataHelper.getPersistentData(projectile).remove("KineticPalmCaptured");
            com.timestop.platform.EntityDataHelper.getPersistentData(projectile).remove("KineticPalmOwner");
            projectile.setNoGravity(false);
        }
    }
}
