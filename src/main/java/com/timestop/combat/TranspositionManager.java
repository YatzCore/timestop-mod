package com.timestop.combat;

import com.timestop.core.TimeStopManager;
import com.timestop.item.rune.RuneType;
import com.timestop.item.rune.TemporalRuneItem;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.network.protocol.game.ClientboundTeleportEntityPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class TranspositionManager {

    public static final double MAX_SWAP_DISTANCE = 42.0;
    // Uses real-time timestamps (ms) to eliminate tick-reset / negative deadlock bugs across world loads/deaths
    private static final Map<UUID, Long> playerCooldowns = new ConcurrentHashMap<>();

    public static boolean hasTranspositionRune(Player player) {
        if (player == null || !player.isAlive()) return false;

        // 1. Check socketed watch via RuneManager
        if (RuneManager.getSocketedRuneType(player) == RuneType.TRANSPOSITION) {
            return true;
        }

        // 2. Check direct hand holding
        if (player.getMainHandItem().getItem() instanceof TemporalRuneItem r1 && r1.getType() == RuneType.TRANSPOSITION) {
            return true;
        }
        if (player.getOffhandItem().getItem() instanceof TemporalRuneItem r2 && r2.getType() == RuneType.TRANSPOSITION) {
            return true;
        }

        return false;
    }

    private static Entity cachedClientTarget = null;
    private static int lastClientCacheTick = -1;

    public static Entity getCachedSwapTargetClient(Player player) {
        if (player == null) return null;
        int tick = player.tickCount;
        if (cachedClientTarget != null && (!cachedClientTarget.isAlive() || cachedClientTarget.level() != player.level())) {
            cachedClientTarget = null;
        }
        if (lastClientCacheTick != tick && tick % 2 == 0) {
            lastClientCacheTick = tick;
            cachedClientTarget = findSwapTargetClient(player);
        }
        return cachedClientTarget;
    }

    public static boolean isOnCooldown(Player player) {
        return getCooldownRemainingMs(player) > 0L;
    }

    public static long getCooldownRemainingMs(Player player) {
        long lastTime = playerCooldowns.getOrDefault(player.getUUID(), 0L);
        long now = System.currentTimeMillis();
        boolean timeSlow = TimeStopManager.isTimeStopped(player.level());
        long requiredMs = timeSlow ? 250L : 750L; // 250ms in stasis, 750ms in real-time

        // Guard against negative deltas or clock shifts
        if (now < lastTime) {
            playerCooldowns.remove(player.getUUID());
            return 0L;
        }

        long elapsed = now - lastTime;
        return elapsed < requiredMs ? (requiredMs - elapsed) : 0L;
    }

    public static List<Entity> getClientSwapCandidates(Player player) {
        if (player == null) return Collections.emptyList();
        Vec3 eyePos = player.getEyePosition();
        Vec3 look = player.getLookAngle().normalize();
        return getSwapCandidates(player, eyePos, look, MAX_SWAP_DISTANCE);
    }

    public static void setCooldown(Player player) {
        playerCooldowns.put(player.getUUID(), System.currentTimeMillis());
    }

    public static void executeSwap(ServerPlayer player, boolean isSneaking) {
        if (!hasTranspositionRune(player)) return;
        if (isOnCooldown(player)) return;

        ServerLevel level = player.serverLevel();
        Vec3 eyePos = player.getEyePosition();
        Vec3 look = player.getLookAngle().normalize();

        List<Entity> candidates = getSwapCandidates(player, eyePos, look, MAX_SWAP_DISTANCE);
        if (candidates.isEmpty()) return;

        // 1. Sneak-Swap: Swapping two nearby mobs/entities with each other
        if (isSneaking && candidates.size() >= 2 && candidates.get(0) instanceof LivingEntity firstLiving && candidates.get(1) instanceof LivingEntity secondLiving) {
            performDualEntitySwap(player, level, firstLiving, secondLiving);
            setCooldown(player);
            return;
        }

        // 2. Normal Swap: Player <-> Target (LivingEntity or Projectile)
        Entity target = candidates.get(0);
        if (target instanceof LivingEntity targetEntity) {
            performPlayerEntitySwap(player, level, targetEntity);
            setCooldown(player);
        } else if (target instanceof Projectile targetProjectile) {
            performPlayerProjectileSwap(player, level, targetProjectile);
            setCooldown(player);
        }
    }

    private static void performPlayerEntitySwap(ServerPlayer player, ServerLevel level, LivingEntity target) {
        Vec3 pPos = player.position();
        Vec3 tPos = target.position();

        // 1. Play percussive hand-clap audio across both points
        playClapAudio(level, pPos);
        playClapAudio(level, tPos);

        // 2. Spawn dimensional rift particles at both origins
        spawnRiftParticles(level, pPos.add(0, 1.0, 0));
        spawnRiftParticles(level, tPos.add(0, target.getBbHeight() * 0.5, 0));

        // 3. Swap physical positions safely
        player.teleportTo(level, tPos.x, tPos.y, tPos.z, Collections.emptySet(), player.getYRot(), player.getXRot());
        player.resetFallDistance();

        target.teleportTo(pPos.x, pPos.y, pPos.z);
        target.resetFallDistance();

        // 4. Disorient enemy mob
        if (target instanceof Mob mob) {
            mob.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 1));
            mob.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 40, 0));
            mob.setTarget(null);
        }
    }

    private static void performPlayerProjectileSwap(ServerPlayer player, ServerLevel level, Projectile proj) {
        Vec3 pPos = player.position();
        Vec3 projPos = proj.position();
        Vec3 projVel = proj.getDeltaMovement();

        // 1. Play percussive hand-clap audio
        playClapAudio(level, pPos);
        playClapAudio(level, projPos);

        // 2. Spawn dimensional rift particles
        spawnRiftParticles(level, pPos.add(0, 1.0, 0));
        spawnRiftParticles(level, projPos);

        // 3. Swap player to projectile location
        player.teleportTo(level, projPos.x, projPos.y, projPos.z, Collections.emptySet(), player.getYRot(), player.getXRot());
        player.resetFallDistance();
        player.setDeltaMovement(player.getLookAngle().scale(0.15));

        // 4. Move projectile to player previous position and REVERSE trajectory 180 degrees
        proj.setPos(pPos.x, pPos.y + 1.2, pPos.z);
        Vec3 reversedVel = projVel.scale(-1.0);
        proj.setDeltaMovement(reversedVel);
        proj.setYRot((proj.getYRot() + 180.0F) % 360.0F);
        proj.setXRot(-proj.getXRot());
        proj.yRotO = proj.getYRot();
        proj.xRotO = proj.getXRot();
        proj.hasImpulse = true;

        if (proj instanceof net.minecraft.world.entity.projectile.AbstractArrow arrow) {
            arrow.setOwner(player);
        }

        level.getChunkSource().broadcast(proj, new ClientboundTeleportEntityPacket(proj));
        level.getChunkSource().broadcast(proj, new ClientboundSetEntityMotionPacket(proj));
    }

    private static void performDualEntitySwap(ServerPlayer player, ServerLevel level, LivingEntity a, LivingEntity b) {
        Vec3 aPos = a.position();
        Vec3 bPos = b.position();

        playClapAudio(level, player.position());
        playClapAudio(level, aPos);
        playClapAudio(level, bPos);

        spawnRiftParticles(level, aPos.add(0, a.getBbHeight() * 0.5, 0));
        spawnRiftParticles(level, bPos.add(0, b.getBbHeight() * 0.5, 0));

        a.teleportTo(bPos.x, bPos.y, bPos.z);
        b.teleportTo(aPos.x, aPos.y, aPos.z);
        a.resetFallDistance();
        b.resetFallDistance();

        if (a instanceof Mob mobA) {
            mobA.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 1));
            mobA.setTarget(null);
        }
        if (b instanceof Mob mobB) {
            mobB.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 1));
            mobB.setTarget(null);
        }
    }

    public static List<Entity> getSwapCandidates(Player player, Vec3 eyePos, Vec3 look, double maxDist) {
        AABB searchBox = player.getBoundingBox().inflate(maxDist);
        List<Entity> allEntities = player.level().getEntitiesOfClass(Entity.class, searchBox,
                e -> e.isAlive() && e != player && !e.isSpectator()
                        && (e instanceof LivingEntity || (e instanceof Projectile proj && !proj.onGround())));

        Vec3 reachEnd = eyePos.add(look.scale(maxDist));
        List<ScoredEntity> scored = new ArrayList<>();

        for (Entity e : allEntities) {
            // Strict Line-of-Sight check: entities behind blocks, walls, floors, or in caves are excluded!
            if (!hasClearLineOfSight(player, e)) continue;

            AABB inflatedBox = e.getBoundingBox().inflate(0.75);

            // 1. Direct line of sight ray-intersection check (Highest accuracy)
            Optional<Vec3> clipHit = inflatedBox.clip(eyePos, reachEnd);
            if (clipHit.isPresent()) {
                double dist = eyePos.distanceTo(clipHit.get());
                scored.add(new ScoredEntity(e, dist * 0.1)); // Prioritized direct hits
                continue;
            }

            // 2. Cone tolerance check (~25 degrees offset for fast action aiming)
            Vec3 center = e.getBoundingBox().getCenter();
            Vec3 toCenter = center.subtract(eyePos);
            double distAlongRay = toCenter.dot(look);

            if (distAlongRay < 0.4 || distAlongRay > maxDist) continue;

            Vec3 rayPoint = look.scale(distAlongRay);
            double perpDist = toCenter.subtract(rayPoint).length();
            double angleRatio = perpDist / distAlongRay;

            if (angleRatio <= 0.45 && perpDist <= 9.0) {
                double score = 10.0 + (angleRatio * 80.0) + (distAlongRay * 0.1);
                scored.add(new ScoredEntity(e, score));
            }
        }

        scored.sort(Comparator.comparingDouble(s -> s.score));
        List<Entity> result = new ArrayList<>();
        for (ScoredEntity s : scored) {
            result.add(s.entity);
        }
        return result;
    }

    public static boolean hasClearLineOfSight(Player player, Entity entity) {
        Level level = player.level();
        Vec3 eyePos = player.getEyePosition();

        // 1. Center of bounding box
        Vec3 center = entity.getBoundingBox().getCenter();
        if (canSeePoint(level, eyePos, center, player)) return true;

        // 2. Head/Eye level
        Vec3 top = (entity instanceof LivingEntity living) ? living.getEyePosition() : center.add(0, entity.getBbHeight() * 0.35, 0);
        if (canSeePoint(level, eyePos, top, player)) return true;

        // 3. Base/Feet level
        Vec3 bottom = entity.position().add(0, 0.25, 0);
        return canSeePoint(level, eyePos, bottom, player);
    }

    private static boolean canSeePoint(Level level, Vec3 from, Vec3 to, Entity observer) {
        ClipContext context = new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, observer);
        BlockHitResult hit = level.clip(context);
        if (hit.getType() == HitResult.Type.MISS) {
            return true;
        }
        // If a block was hit, verify if the block collision is behind or at the target
        double hitDistSq = from.distanceToSqr(hit.getLocation());
        double targetDistSq = from.distanceToSqr(to);
        return hitDistSq >= (targetDistSq - 0.25);
    }

    public static Entity findSwapTargetClient(Player player) {
        Vec3 eyePos = player.getEyePosition();
        Vec3 look = player.getLookAngle().normalize();
        List<Entity> list = getSwapCandidates(player, eyePos, look, MAX_SWAP_DISTANCE);
        return list.isEmpty() ? null : list.get(0);
    }

    public static void playClapAudio(ServerLevel level, Vec3 pos) {
        // Authentic Aoi Todo hand-clap blend
        level.playSound(null, pos.x, pos.y, pos.z, SoundEvents.WOODEN_TRAPDOOR_CLOSE, SoundSource.PLAYERS, 2.0F, 1.95F);
        level.playSound(null, pos.x, pos.y, pos.z, SoundEvents.CHORUS_FRUIT_TELEPORT, SoundSource.PLAYERS, 1.4F, 1.3F);
        level.playSound(null, pos.x, pos.y, pos.z, SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 1.2F, 1.7F);
    }

    public static void spawnRiftParticles(ServerLevel level, Vec3 pos) {
        level.sendParticles(ParticleTypes.PORTAL, pos.x, pos.y, pos.z, 28, 0.35, 0.45, 0.35, 0.2);
        level.sendParticles(ParticleTypes.REVERSE_PORTAL, pos.x, pos.y, pos.z, 16, 0.25, 0.35, 0.25, 0.05);
        level.sendParticles(ParticleTypes.FLASH, pos.x, pos.y, pos.z, 1, 0, 0, 0, 0);
        level.sendParticles(ParticleTypes.DRAGON_BREATH, pos.x, pos.y, pos.z, 10, 0.2, 0.2, 0.2, 0.02);
    }

    private static class ScoredEntity {
        final Entity entity;
        final double score;

        ScoredEntity(Entity entity, double score) {
            this.entity = entity;
            this.score = score;
        }
    }
}
