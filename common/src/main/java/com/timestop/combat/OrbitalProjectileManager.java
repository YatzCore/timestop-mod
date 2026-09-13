package com.timestop.combat;

import com.timestop.item.rune.RuneType;
import com.timestop.network.ModMessages;
import com.timestop.network.SyncOrbitCountPacket;
import com.timestop.network.SyncOrbitalEntityPacket;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.network.protocol.game.ClientboundTeleportEntityPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.AbstractHurtingProjectile;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ThrownTrident;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.lang.ref.WeakReference;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import com.timestop.platform.Services;

public class OrbitalProjectileManager {

    public static final int MAX_ORBIT_COUNT = 16;
    public static final double CATCH_RADIUS = 3.8;
    public static final double ORBIT_RADIUS = 1.45;

    private static final Map<UUID, List<WeakReference<Projectile>>> playerOrbits = new ConcurrentHashMap<>();
    private static final List<WeakReference<Projectile>> activeGuidedProjectiles = new CopyOnWriteArrayList<>();

    public static int getOrbitCount(Player player) {
        List<WeakReference<Projectile>> list = playerOrbits.get(player.getUUID());
        if (list == null) return 0;
        cleanOrbitList(list);
        return list.size();
    }

    private static void cleanOrbitList(List<WeakReference<Projectile>> list) {
        list.removeIf(ref -> {
            Projectile p = ref.get();
            return p == null || !p.isAlive() || p.onGround();
        });
    }

    private static boolean canAutoCapture(Player player, Projectile projectile) {
        if (ProjectileCombatHelper.wasReleasedBy(projectile, player)) return false;
        // Wearing the rune must not intercept each shot as it leaves the player's weapon.
        // Own rounds become collectible once time stop has actually suspended them.
        return projectile.getOwner() != player
                || com.timestop.core.TimeStopManager.isProjectileSuspended(projectile);
    }

    /** Shared swept interception runs before either vanilla or TacZ movement/stasis handling. */
    public static void interceptIncoming(Projectile projectile) {
        if (!(projectile.level() instanceof ServerLevel level) || !ProjectileCombatHelper.isActiveInFlight(projectile)
                || com.timestop.platform.EntityDataHelper.getPersistentData(projectile).getBoolean("InStasisOrbit")
                || com.timestop.platform.EntityDataHelper.getPersistentData(projectile).getBoolean("KineticPalmCaptured")
                || com.timestop.platform.EntityDataHelper.getPersistentData(projectile).getBoolean("KineticPalmDropped")) return;
        Vec3 start = projectile.position();
        Vec3 velocity = ProjectileCombatHelper.incomingVelocity(projectile);
        Vec3 nearest = null;
        Player defender = null;
        for (ServerPlayer player : level.players()) {
            if (!player.isAlive() || player.isSpectator() || RuneManager.getSocketedRuneType(player) != RuneType.ORBITAL
                    || !canAutoCapture(player, projectile)) continue;
            Vec3 relative = start.subtract(player.position().add(0, player.getEyeHeight() * 0.5, 0));
            double t = 0;
            double c = relative.lengthSqr() - CATCH_RADIUS * CATCH_RADIUS;
            if (c > 0) {
                double a = velocity.lengthSqr();
                double b = relative.dot(velocity);
                double discriminant = b * b - a * c;
                if (a < 1e-8 || discriminant < 0) continue;
                t = (-b - Math.sqrt(discriminant)) / a;
                if (t < 0 || t > 1) continue;
            }
            Vec3 entry = start.add(velocity.scale(t));
            if (level.clip(new net.minecraft.world.level.ClipContext(start, entry,
                    net.minecraft.world.level.ClipContext.Block.COLLIDER,
                    net.minecraft.world.level.ClipContext.Fluid.NONE, projectile)).getType() != HitResult.Type.MISS) continue;
            if (nearest == null || start.distanceToSqr(entry) < start.distanceToSqr(nearest)) {
                nearest = entry;
                defender = player;
            }
        }
        if (defender != null) {
            projectile.setPos(nearest);
            captureProjectile(defender, projectile, level);
        }
    }

    public static boolean onProjectileImpact(Projectile projectile, HitResult hit) {
        if (!(projectile.level() instanceof ServerLevel level)) return false;

        // 1. Check if this projectile was launched from orbit and has now impacted a surface or entity
        if (com.timestop.platform.EntityDataHelper.getPersistentData(projectile).contains("WasOrbitalLaunched") || com.timestop.platform.EntityDataHelper.getPersistentData(projectile).contains("OrbitalTargetId")) {
            // hit passed in
            if (hit instanceof EntityHitResult entityHit && entityHit.getEntity() == projectile.getOwner()) {
                // Owner immunity: launched orbital projectiles never damage or hit the player who fired them!
                return true;
            }

            projectile.setNoGravity(false);
            com.timestop.platform.EntityDataHelper.getPersistentData(projectile).remove("OrbitalTargetId");
            com.timestop.platform.EntityDataHelper.getPersistentData(projectile).remove("WasOrbitalLaunched");
            com.timestop.platform.EntityDataHelper.getPersistentData(projectile).putBoolean("TridentImpacted", true);

            if (projectile instanceof ThrownTrident trident) {
                trident.setNoGravity(false);
                trident.hasImpulse = true;
            }
        }

        // hit passed in
        if (!(hit instanceof EntityHitResult entityHit)) return false;

        Entity hitEntity = entityHit.getEntity();
        if (!(hitEntity instanceof Player player)) return false;

        // Verify player has the Orbital Redirection rune equipped
        if (RuneManager.getSocketedRuneType(player) != RuneType.ORBITAL || !canAutoCapture(player, projectile)) return false;

        // Catch the projectile directly upon imminent impact!
        captureProjectile(player, projectile, level);
        return true;
    }

    public static void serverTick() {

        // 1. Proximity detection: catch incoming projectiles within CATCH_RADIUS
        net.minecraft.server.MinecraftServer server = Services.PLATFORM.getCurrentServer();
        if (server != null) {
            for (ServerLevel sl : server.getAllLevels()) {
                for (ServerPlayer player : sl.players()) {
                    if (RuneManager.getSocketedRuneType(player) != RuneType.ORBITAL) continue;

                    AABB catchBox = player.getBoundingBox().inflate(CATCH_RADIUS);
                    List<Projectile> incoming = sl.getEntitiesOfClass(Projectile.class, catchBox,
                            p -> p.isAlive() && !p.onGround()
                                    && !com.timestop.platform.EntityDataHelper.getPersistentData(p).contains("OrbitedPlayerUuid"));

                    for (Projectile p : incoming) {
                        interceptIncoming(p);
                    }
                }
            }
        }

        // Clean orbit lists
        for (Map.Entry<UUID, List<WeakReference<Projectile>>> entry : playerOrbits.entrySet()) {
            cleanOrbitList(entry.getValue());
        }

        // 2. Physics & Orbit Motion Tick
        for (Map.Entry<UUID, List<WeakReference<Projectile>>> entry : playerOrbits.entrySet()) {
            UUID playerUuid = entry.getKey();
            List<WeakReference<Projectile>> list = entry.getValue();
            if (list.isEmpty()) continue;

            // Find player across all dimensions
            ServerPlayer player = server != null ? server.getPlayerList().getPlayer(playerUuid) : null;
            if (player == null || !player.isAlive() || RuneManager.getSocketedRuneType(player) != RuneType.ORBITAL) {
                // Player left, died, or unequipped the rune: drop projectiles
                for (WeakReference<Projectile> ref : list) {
                    Projectile p = ref.get();
                    if (p != null && p.isAlive()) {
                        p.setNoGravity(false);
                        com.timestop.platform.EntityDataHelper.getPersistentData(p).remove("OrbitedPlayerUuid");
                        com.timestop.platform.EntityDataHelper.getPersistentData(p).remove("InStasisOrbit");
                        ModMessages.sendToTracking(new SyncOrbitalEntityPacket(p.getId(), playerUuid, -1, -1, false), p);
                    }
                }
                list.clear();
                playerOrbits.remove(playerUuid);
                if (player != null) {
                    ModMessages.sendToPlayer(new SyncOrbitCountPacket(0), player);
                }
                continue;
            }

            ServerLevel level = player.serverLevel();

            int count = list.size();
            double angularSpeed = 0.09; // Smooth rotation
            long tick = player.tickCount;

            for (int i = 0; i < count; i++) {
                Projectile proj = list.get(i).get();
                if (proj == null || !proj.isAlive()) continue;

                double theta = tick * angularSpeed + (i * 2.0 * Math.PI / count);
                double x = player.getX() + ORBIT_RADIUS * Math.cos(theta);
                double z = player.getZ() + ORBIT_RADIUS * Math.sin(theta);
                double bob = Math.sin(tick * 0.15 + i) * 0.08;
                double y = player.getY() + 1.15 + bob;

                // Tangential flight orientation along circle
                double vx = -Math.sin(theta) * 0.22;
                double vz = Math.cos(theta) * 0.22;
                float yRot = (float) (Mth.atan2(vx, vz) * (180.0D / Math.PI));

                proj.setPos(x, y, z);
                proj.setYRot(yRot);
                proj.setXRot(0.0F);
                proj.yRotO = yRot;
                proj.xRotO = 0.0F;
                proj.setDeltaMovement(vx, 0, vz);
                proj.hasImpulse = true;

                // Fireballs: neutralize internal acceleration while in orbit
                if (proj instanceof AbstractHurtingProjectile hurting) {
                    hurting.xPower = 0.0;
                    hurting.yPower = 0.0;
                    hurting.zPower = 0.0;
                }

                int oldIndex = com.timestop.platform.EntityDataHelper.getPersistentData(proj).getInt("OrbitIndex");
                int oldTotal = com.timestop.platform.EntityDataHelper.getPersistentData(proj).getInt("OrbitTotal");
                if (oldIndex != i || oldTotal != count) {
                    com.timestop.platform.EntityDataHelper.getPersistentData(proj).putInt("OrbitIndex", i);
                    com.timestop.platform.EntityDataHelper.getPersistentData(proj).putInt("OrbitTotal", count);
                    ModMessages.sendToTracking(new SyncOrbitalEntityPacket(proj.getId(), playerUuid, i, count, true), proj);
                }

                if (tick % 2 == 0) {
                    level.getChunkSource().broadcast(proj, new ClientboundTeleportEntityPacket(proj));
                    level.getChunkSource().broadcast(proj, new ClientboundSetEntityMotionPacket(proj));
                }

                if (tick % 4 == 0) {
                    level.sendParticles(ParticleTypes.ELECTRIC_SPARK, x, y, z, 1, 0, 0, 0, 0);
                }
            }
        }

        // 3. Precision Laser Guidance for Launched Projectiles
        if (!activeGuidedProjectiles.isEmpty()) {
            Iterator<WeakReference<Projectile>> it = activeGuidedProjectiles.iterator();
            while (it.hasNext()) {
                WeakReference<Projectile> ref = it.next();
                Projectile proj = ref.get();
                if (proj == null || !proj.isAlive() || proj.onGround()) {
                    if (proj != null) {
                        proj.setNoGravity(false);
                        com.timestop.platform.EntityDataHelper.getPersistentData(proj).remove("OrbitalTargetId");
                    }
                    activeGuidedProjectiles.remove(ref);
                    continue;
                }

                // If trident has hit a mob or the ground, stop guiding and restore gravity immediately
                if (proj instanceof ThrownTrident trident) {
                    if (trident.onGround() || com.timestop.platform.EntityDataHelper.getPersistentData(trident).getBoolean("TridentImpacted")) {
                        trident.setNoGravity(false);
                        com.timestop.platform.EntityDataHelper.getPersistentData(trident).remove("OrbitalTargetId");
                        activeGuidedProjectiles.remove(ref);
                        continue;
                    }
                }

                int targetId = com.timestop.platform.EntityDataHelper.getPersistentData(proj).getInt("OrbitalTargetId");
                if (targetId != 0 && proj.level() instanceof ServerLevel sl) {
                    Entity target = sl.getEntity(targetId);
                    if (target instanceof LivingEntity living && living.isAlive()) {
                        Vec3 targetCenter = living.getBoundingBox().getCenter();
                        Vec3 toTarget = targetCenter.subtract(proj.position()).normalize();

                        if (proj instanceof AbstractHurtingProjectile hurting) {
                            hurting.xPower = toTarget.x * 0.18D;
                            hurting.yPower = toTarget.y * 0.18D;
                            hurting.zPower = toTarget.z * 0.18D;
                            hurting.setDeltaMovement(toTarget.scale(3.4D));
                            hurting.hasImpulse = true;
                        } else if (proj instanceof ThrownTrident trident) {
                            Vec3 cur = trident.getDeltaMovement();
                            Vec3 guided = cur.normalize().scale(0.82).add(toTarget.scale(0.18)).normalize().scale(3.2D);
                            trident.setDeltaMovement(guided);
                            trident.hasImpulse = true;
                            sl.sendParticles(ParticleTypes.NAUTILUS, trident.getX(), trident.getY(), trident.getZ(),
                                    2, 0.05, 0.05, 0.05, 0.02);
                        } else if (proj instanceof AbstractArrow arrow) {
                            Vec3 cur = arrow.getDeltaMovement();
                            Vec3 guided = cur.normalize().scale(0.80).add(toTarget.scale(0.20)).normalize().scale(3.6D);
                            arrow.setDeltaMovement(guided);
                            arrow.hasImpulse = true;
                        } else {
                            proj.setDeltaMovement(toTarget.scale(Math.max(3.6, com.timestop.platform.EntityDataHelper.getPersistentData(proj).getDouble("OrbitalNativeSpeed"))));
                            proj.hasImpulse = true;
                        }

                        Vec3 moveVec = proj.getDeltaMovement();
                        double horiz = Math.sqrt(moveVec.x * moveVec.x + moveVec.z * moveVec.z);
                        float yaw = (float) (Mth.atan2(moveVec.x, moveVec.z) * (180.0D / Math.PI));
                        float pitch = (float) (Mth.atan2(moveVec.y, horiz) * (180.0D / Math.PI));
                        proj.setYRot(yaw);
                        proj.setXRot(pitch);
                        proj.yRotO = yaw;
                        proj.xRotO = pitch;
                    } else {
                        // Target dead or disappeared: cancel guidance and restore natural gravity
                        proj.setNoGravity(false);
                        com.timestop.platform.EntityDataHelper.getPersistentData(proj).remove("OrbitalTargetId");
                        activeGuidedProjectiles.remove(ref);
                    }
                }
            }
        }
    }

    public static void captureProjectile(Player player, Projectile projectile, ServerLevel level) {
        if (projectile == null || !projectile.isAlive()) return;
        if (com.timestop.platform.EntityDataHelper.getPersistentData(projectile).contains("OrbitedPlayerUuid")) return;

        List<WeakReference<Projectile>> list = playerOrbits.computeIfAbsent(player.getUUID(), k -> new CopyOnWriteArrayList<>());
        cleanOrbitList(list);

        if (list.size() >= MAX_ORBIT_COUNT) {
            // Already full: auto-launch current volley to make room!
            launchOrbitingProjectiles(player);
            list = playerOrbits.computeIfAbsent(player.getUUID(), k -> new CopyOnWriteArrayList<>());
        }

        com.timestop.platform.EntityDataHelper.getPersistentData(projectile).putDouble("OrbitalNativeSpeed", ProjectileCombatHelper.incomingVelocity(projectile).length());
        com.timestop.core.TimeStopManager.removeSuspendedProjectile(projectile);
        ProjectileRedirection.clearGuidance(projectile);
        // Lock projectile into stasis
        projectile.setNoGravity(true);
        projectile.setDeltaMovement(Vec3.ZERO);
        projectile.setOwner(player);
        com.timestop.platform.EntityDataHelper.getPersistentData(projectile).putUUID("OrbitedPlayerUuid", player.getUUID());
        com.timestop.platform.EntityDataHelper.getPersistentData(projectile).putBoolean("InStasisOrbit", true);

        if (projectile instanceof AbstractHurtingProjectile hurting) {
            hurting.xPower = 0.0;
            hurting.yPower = 0.0;
            hurting.zPower = 0.0;
        }

        // Disallow arrow pickup, but KEEP tridents retrievable!
        if (projectile instanceof AbstractArrow arrow && !(projectile instanceof ThrownTrident)) {
            arrow.pickup = AbstractArrow.Pickup.DISALLOWED;
        }

        list.add(new WeakReference<>(projectile));
        int newCount = list.size();

        // Audio & Visual capture feedback
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.5F, 1.8F);
        level.playSound(null, projectile.getX(), projectile.getY(), projectile.getZ(),
                SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 1.2F, 1.4F);

        level.sendParticles(ParticleTypes.PORTAL, projectile.getX(), projectile.getY(), projectile.getZ(),
                12, 0.2, 0.2, 0.2, 0.1);

        // Sync count to client HUD and tracking clients
        if (player instanceof ServerPlayer serverPlayer) {
            ModMessages.sendToPlayer(new SyncOrbitCountPacket(newCount), serverPlayer);
        }
        ModMessages.sendToTracking(new SyncOrbitalEntityPacket(projectile.getId(), player.getUUID(), newCount - 1, newCount, true), projectile);
    }

    public static void launchOrbitingProjectiles(Player player) {
        if (player == null || !(player.level() instanceof ServerLevel level)) return;

        List<WeakReference<Projectile>> list = playerOrbits.get(player.getUUID());
        if (list == null || list.isEmpty()) return;

        cleanOrbitList(list);
        if (list.isEmpty()) return;

        // Find surrounding target mobs within 32 blocks respecting ChainTargetFilter
        AABB searchBox = player.getBoundingBox().inflate(32.0);
        ChainTargetFilter filter = RuneManager.getActiveChainFilter(player);
        List<LivingEntity> targets = level.getEntitiesOfClass(LivingEntity.class, searchBox, e -> {
            if (!e.isAlive() || e.isSpectator() || e == player) return false;
            if (e instanceof TamableAnimal tamed && tamed.isOwnedBy(player)) return false;
            return filter.matches(e);
        });

        targets.sort(Comparator.comparingDouble(player::distanceToSqr));

        int targetIndex = 0;
        for (WeakReference<Projectile> ref : list) {
            Projectile proj = ref.get();
            if (proj == null || !proj.isAlive()) continue;

            com.timestop.platform.EntityDataHelper.getPersistentData(proj).remove("OrbitedPlayerUuid");
            com.timestop.platform.EntityDataHelper.getPersistentData(proj).remove("InStasisOrbit");
            com.timestop.platform.EntityDataHelper.getPersistentData(proj).remove("OrbitIndex");
            com.timestop.platform.EntityDataHelper.getPersistentData(proj).remove("OrbitTotal");
            com.timestop.platform.EntityDataHelper.getPersistentData(proj).putBoolean("WasOrbitalLaunched", true);
            ProjectileCombatHelper.markReleased(proj, player);
            ModMessages.sendToTracking(new SyncOrbitalEntityPacket(proj.getId(), player.getUUID(), -1, -1, false), proj);

            LivingEntity target = null;
            Vec3 dir;
            if (!targets.isEmpty()) {
                target = targets.get(targetIndex % targets.size());
                targetIndex++;
                Vec3 targetPos = target.position().add(0, target.getBbHeight() * 0.55, 0);
                dir = targetPos.subtract(proj.position()).normalize();
            } else {
                dir = player.getLookAngle().normalize();
            }

            proj.setOwner(player);

            if (target != null) {
                com.timestop.platform.EntityDataHelper.getPersistentData(proj).putInt("OrbitalTargetId", target.getId());
                activeGuidedProjectiles.add(new WeakReference<>(proj));
            }

            boolean isTrident = proj instanceof ThrownTrident;
            boolean isFireball = proj instanceof AbstractHurtingProjectile;
            boolean isArrow = proj instanceof AbstractArrow && !isTrident;

            if (isTrident) {
                ThrownTrident trident = (ThrownTrident) proj;
                trident.setNoGravity(false); // Natural gravity so it falls/returns after hit!
                trident.shoot(dir.x, dir.y, dir.z, 3.2F, 0.0F);

                level.sendParticles(ParticleTypes.NAUTILUS, proj.getX(), proj.getY(), proj.getZ(),
                        12, dir.x * 0.2, dir.y * 0.2, dir.z * 0.2, 0.12);
                level.sendParticles(ParticleTypes.ELECTRIC_SPARK, proj.getX(), proj.getY(), proj.getZ(),
                        6, 0.1, 0.1, 0.1, 0.05);
            } else if (isArrow) {
                AbstractArrow arrow = (AbstractArrow) proj;
                arrow.setNoGravity(target != null);
                arrow.shoot(dir.x, dir.y, dir.z, 3.4F, 0.0F);
                arrow.setCritArrow(true);
                arrow.setBaseDamage(arrow.getBaseDamage() + 4.5);
                arrow.pickup = AbstractArrow.Pickup.DISALLOWED;

                level.sendParticles(ParticleTypes.CRIT, proj.getX(), proj.getY(), proj.getZ(),
                        8, dir.x * 0.2, dir.y * 0.2, dir.z * 0.2, 0.1);
            } else if (isFireball) {
                AbstractHurtingProjectile hurting = (AbstractHurtingProjectile) proj;
                hurting.setNoGravity(true);
                hurting.xPower = dir.x * 0.18D;
                hurting.yPower = dir.y * 0.18D;
                hurting.zPower = dir.z * 0.18D;
                hurting.setDeltaMovement(dir.scale(3.4D));

                level.sendParticles(ParticleTypes.FLAME, proj.getX(), proj.getY(), proj.getZ(),
                        10, dir.x * 0.2, dir.y * 0.2, dir.z * 0.2, 0.08);
            } else {
                proj.setNoGravity(false);
                proj.shoot(dir.x, dir.y, dir.z, (float) Math.max(3.0, com.timestop.platform.EntityDataHelper.getPersistentData(proj).getDouble("OrbitalNativeSpeed")), 0.0F);
            }

            proj.hasImpulse = true;
            level.sendParticles(ParticleTypes.SONIC_BOOM, proj.getX(), proj.getY(), proj.getZ(),
                    1, 0, 0, 0, 0);
        }

        // Release audio
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 1.8F, 1.8F);
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.TRIDENT_THROW, SoundSource.PLAYERS, 1.4F, 1.2F);
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.ARROW_SHOOT, SoundSource.PLAYERS, 1.2F, 1.6F);

        list.clear();
        playerOrbits.remove(player.getUUID());

        if (player instanceof ServerPlayer serverPlayer) {
            ModMessages.sendToPlayer(new SyncOrbitCountPacket(0), serverPlayer);
        }
    }

    public static void launchSingleProjectile(Player player) {
        if (player == null || !(player.level() instanceof ServerLevel level)) return;

        List<WeakReference<Projectile>> list = playerOrbits.get(player.getUUID());
        if (list == null || list.isEmpty()) return;

        cleanOrbitList(list);
        if (list.isEmpty()) return;

        // Pop the first available projectile
        Projectile proj = null;
        while (!list.isEmpty() && (proj == null || !proj.isAlive())) {
            WeakReference<Projectile> ref = list.remove(0);
            proj = ref.get();
        }

        if (proj == null || !proj.isAlive()) return;
        final Projectile toLaunch = proj;

        // 1. Raycast / Cone scan along player cursor line of sight up to 32 blocks
        Vec3 eyePos = player.getEyePosition();
        Vec3 look = player.getLookAngle().normalize();
        double maxDistance = 32.0;
        AABB searchBox = player.getBoundingBox().inflate(maxDistance);

        ChainTargetFilter filter = RuneManager.getActiveChainFilter(player);
        List<LivingEntity> candidates = level.getEntitiesOfClass(LivingEntity.class, searchBox, e -> {
            if (!e.isAlive() || e.isSpectator() || e == player) return false;
            if (e instanceof TamableAnimal tamed && tamed.isOwnedBy(player)) return false;
            return filter.matches(e);
        });

        LivingEntity bestTarget = null;
        double bestScore = Double.MAX_VALUE;

        for (LivingEntity target : candidates) {
            Vec3 toTarget = target.getBoundingBox().getCenter().subtract(eyePos);
            double distAlongRay = toTarget.dot(look);

            // Must be in front of the player and within max range
            if (distAlongRay < 1.0 || distAlongRay > maxDistance) continue;

            // Perpendicular distance from look ray
            Vec3 rayPoint = look.scale(distAlongRay);
            double perpDist = toTarget.subtract(rayPoint).length();

            // Cone check: maximum angular deviation allowed (~22 degrees or 8 blocks offset)
            double angularRatio = perpDist / distAlongRay;
            if (angularRatio > 0.40 && perpDist > 8.0) continue;

            // Prioritize entity closest to crosshair center!
            double score = angularRatio * 100.0 + (distAlongRay * 0.08);
            if (score < bestScore) {
                bestScore = score;
                bestTarget = target;
            }
        }

        // Spawn cleanly in front of player so arrow never passes through player's hitbox
        Vec3 launchOrigin = eyePos.add(look.scale(0.8));
        proj.setPos(launchOrigin.x, launchOrigin.y, launchOrigin.z);

        // Determine fire direction
        Vec3 fireDir;
        if (bestTarget != null) {
            Vec3 targetCenter = bestTarget.getBoundingBox().getCenter();
            fireDir = targetCenter.subtract(launchOrigin).normalize();
            // Lock target into guidance tracking
            com.timestop.platform.EntityDataHelper.getPersistentData(proj).putInt("OrbitalTargetId", bestTarget.getId());
            activeGuidedProjectiles.add(new WeakReference<>(proj));
        } else {
            fireDir = look;
        }

        // Release from orbit
        com.timestop.platform.EntityDataHelper.getPersistentData(proj).remove("OrbitedPlayerUuid");
        com.timestop.platform.EntityDataHelper.getPersistentData(proj).remove("InStasisOrbit");
        com.timestop.platform.EntityDataHelper.getPersistentData(proj).remove("OrbitIndex");
        com.timestop.platform.EntityDataHelper.getPersistentData(proj).remove("OrbitTotal");
        com.timestop.platform.EntityDataHelper.getPersistentData(proj).putBoolean("WasOrbitalLaunched", true);
        ProjectileCombatHelper.markReleased(proj, player);
        proj.setOwner(player);
        ModMessages.sendToTracking(new SyncOrbitalEntityPacket(toLaunch.getId(), player.getUUID(), -1, -1, false), toLaunch);

        boolean isTrident = proj instanceof ThrownTrident;
        boolean isFireball = proj instanceof AbstractHurtingProjectile;
        boolean isArrow = proj instanceof AbstractArrow && !isTrident;

        if (isTrident) {
            ThrownTrident trident = (ThrownTrident) proj;
            trident.setNoGravity(false); // Natural gravity so tridents drop/return!
            trident.shoot(fireDir.x, fireDir.y, fireDir.z, 3.2F, 0.0F);

            // Authentic trident audio
            level.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.TRIDENT_THROW, SoundSource.PLAYERS, 1.6F, 1.0F);
            level.playSound(null, proj.getX(), proj.getY(), proj.getZ(),
                    SoundEvents.TRIDENT_RIPTIDE_1, SoundSource.PLAYERS, 1.4F, 1.2F);

            // Oceanic nautilus & electric sparks
            level.sendParticles(ParticleTypes.NAUTILUS, proj.getX(), proj.getY(), proj.getZ(),
                    14, fireDir.x * 0.25, fireDir.y * 0.25, fireDir.z * 0.25, 0.12);
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, proj.getX(), proj.getY(), proj.getZ(),
                    8, 0.1, 0.1, 0.1, 0.08);
        } else if (isArrow) {
            AbstractArrow arrow = (AbstractArrow) proj;
            arrow.setNoGravity(bestTarget != null);
            arrow.shoot(fireDir.x, fireDir.y, fireDir.z, 3.6F, 0.0F); // High-velocity sniper precision!
            arrow.setCritArrow(true);
            arrow.setBaseDamage(arrow.getBaseDamage() + 5.0);
            arrow.pickup = AbstractArrow.Pickup.DISALLOWED;

            // Bow audio
            level.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.ARROW_SHOOT, SoundSource.PLAYERS, 1.3F, 1.8F);
            level.playSound(null, proj.getX(), proj.getY(), proj.getZ(),
                    SoundEvents.AMETHYST_BLOCK_HIT, SoundSource.PLAYERS, 1.2F, 1.6F);

            // Crit particles
            level.sendParticles(ParticleTypes.CRIT, proj.getX(), proj.getY(), proj.getZ(),
                    10, fireDir.x * 0.2, fireDir.y * 0.2, fireDir.z * 0.2, 0.1);
        } else if (isFireball) {
            AbstractHurtingProjectile hurting = (AbstractHurtingProjectile) proj;
            hurting.setNoGravity(true);
            hurting.xPower = fireDir.x * 0.18D;
            hurting.yPower = fireDir.y * 0.18D;
            hurting.zPower = fireDir.z * 0.18D;
            hurting.setDeltaMovement(fireDir.scale(3.4D));

            // Fireball audio
            level.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.BLAZE_SHOOT, SoundSource.PLAYERS, 1.4F, 1.2F);

            // Flame particles
            level.sendParticles(ParticleTypes.FLAME, proj.getX(), proj.getY(), proj.getZ(),
                    12, fireDir.x * 0.2, fireDir.y * 0.2, fireDir.z * 0.2, 0.08);
        } else {
            proj.setNoGravity(false);
            proj.shoot(fireDir.x, fireDir.y, fireDir.z, (float) Math.max(3.2, com.timestop.platform.EntityDataHelper.getPersistentData(proj).getDouble("OrbitalNativeSpeed")), 0.0F);

            level.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.ARROW_SHOOT, SoundSource.PLAYERS, 1.2F, 1.5F);
        }

        proj.hasImpulse = true;
        level.sendParticles(ParticleTypes.SONIC_BOOM, proj.getX(), proj.getY(), proj.getZ(),
                1, 0, 0, 0, 0);

        // Update remaining count & sync
        cleanOrbitList(list);
        int remaining = list.size();
        if (remaining == 0) {
            playerOrbits.remove(player.getUUID());
        }

        if (player instanceof ServerPlayer serverPlayer) {
            ModMessages.sendToPlayer(new SyncOrbitCountPacket(remaining), serverPlayer);
        }
    }

    public static void onPlayerRespawn(ServerPlayer player) {
        playerOrbits.remove(player.getUUID());
        ModMessages.sendToPlayer(new SyncOrbitCountPacket(0), player);
    }

    public static void onPlayerLoggedIn(ServerPlayer player) {
        int count = getOrbitCount(player);
        ModMessages.sendToPlayer(new SyncOrbitCountPacket(count), player);
    }
}
