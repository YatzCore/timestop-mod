package com.timestop.combat;

import com.timestop.item.rune.RuneType;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class VoltaicRicochetHandler {

    public static final int MAX_CHAIN_COUNT = 6;
    public static final double CHAIN_RADIUS = 9.0;
    public static final double CHAIN_RADIUS_SQR = 81.0;
    private static final List<WeakReference<Projectile>> activeRicochetArrows = new CopyOnWriteArrayList<>();

    public static boolean onProjectileImpact(Projectile arrow, HitResult hit) {
        if (!(arrow instanceof AbstractArrow) && !TaczProjectileCompat.isBullet(arrow)) return false;
        if (!(arrow.level() instanceof ServerLevel level)) return false;

        Entity shooter = arrow.getOwner();
        if (!(shooter instanceof Player player)) return false;

        // Must have Voltaic Ricochet rune equipped (works at any time, just like Dead Eye!)
        if (RuneManager.getSocketedRuneType(player) != RuneType.RICOCHET) return false;

        if (!(hit instanceof EntityHitResult entityHit)) return false;

        Entity hitEntity = entityHit.getEntity();
        if (!(hitEntity instanceof LivingEntity victim)) return false;

        ChainTargetFilter filter = RuneManager.getActiveChainFilter(player);
        if (victim.isSpectator() || !filter.matchesType(victim)) return false;

        // 1. Damage already dealt by vanilla/TACZ arrow impact
        // 2. Play sound and electrical VFX
        level.playSound(null, victim.getX(), victim.getY(), victim.getZ(),
                SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 0.45F, 1.9F);
        level.playSound(null, victim.getX(), victim.getY(), victim.getZ(),
                SoundEvents.TRIDENT_THUNDER, SoundSource.PLAYERS, 0.6F, 1.8F);

        // Chain lightning strike line connecting to mob
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, victim.getX(), victim.getY() + victim.getBbHeight() * 0.5, victim.getZ(),
                18, 0.4, 0.4, 0.4, 0.2);

        // Update hit list and chain count
        CompoundTag tag = com.timestop.platform.EntityDataHelper.getPersistentData(arrow);
        ListTag hitList = tag.getList("RicochetHitList", Tag.TAG_INT);
        hitList.add(IntTag.valueOf(victim.getId()));
        tag.put("RicochetHitList", hitList);

        int chainCount = tag.getInt("RicochetChainCount") + 1;
        tag.putInt("RicochetChainCount", chainCount);

        // Check if chain continues (must hit exactly MAX_CHAIN_COUNT times if any eligible targets exist)
        if (chainCount < MAX_CHAIN_COUNT) {
            LivingEntity nextTarget = findNextTarget(level, victim, hitList, filter, player);
            if (nextTarget != null) {
                // Reposition arrow at center of current victim
                Vec3 launchPos = victim.position().add(0, victim.getBbHeight() * 0.6, 0);
                arrow.setPos(launchPos.x, launchPos.y, launchPos.z);

                Vec3 targetPos = nextTarget.position().add(0, nextTarget.getBbHeight() * 0.6, 0);
                Vec3 dir = targetPos.subtract(launchPos).normalize();

                arrow.shoot(dir.x, dir.y, dir.z, 3.6F, 0.0F);
                arrow.setNoGravity(true);
                ProjectileRedirection.clearGuidance(arrow);
                if (arrow instanceof AbstractArrow vanilla) {
                    vanilla.setCritArrow(true);
                    vanilla.pickup = AbstractArrow.Pickup.DISALLOWED;
                }
                arrow.hasImpulse = true;

                tag.putInt("RicochetTargetId", nextTarget.getId());
                activeRicochetArrows.add(new WeakReference<>(arrow));

                // Electric bounce feedback
                level.playSound(null, launchPos.x, launchPos.y, launchPos.z,
                        SoundEvents.ARROW_SHOOT, SoundSource.PLAYERS, 1.2F, 1.8F);
                level.sendParticles(ParticleTypes.SONIC_BOOM, launchPos.x, launchPos.y, launchPos.z,
                        1, 0, 0, 0, 0);
                return true;
            }
        }

        // End of chain (or no eligible targets remain): final thunder discharge flash
        level.sendParticles(ParticleTypes.FLASH, victim.getX(), victim.getY() + 1.0, victim.getZ(), 1, 0, 0, 0, 0);
        arrow.discard();
        return true;
    }

    private static LivingEntity findNextTarget(ServerLevel level, LivingEntity current, ListTag hitList, ChainTargetFilter filter, Player shooter) {
        AABB searchBox = current.getBoundingBox().inflate(CHAIN_RADIUS);
        List<LivingEntity> candidates = level.getEntitiesOfClass(LivingEntity.class, searchBox,
                e -> e.isAlive() && !e.isSpectator() && e != shooter && e.getId() != current.getId()
                        && filter.matches(e)
                        && current.distanceToSqr(e) <= CHAIN_RADIUS_SQR);

        if (candidates.isEmpty()) return null;

        // Proximity priority: Sort all candidates strictly by distance to current mob
        candidates.sort(Comparator.comparingDouble(current::distanceToSqr));
        LivingEntity closest = candidates.get(0);
        double closestDist = current.distanceTo(closest);

        // Fresh target check: prefer mobs not yet hit in this specific chain
        for (LivingEntity candidate : candidates) {
            if (!hasBeenHit(hitList, candidate.getId())) {
                // If a fresh mob is reasonably close (within 1.7x of closest distance), prioritize it over revisiting
                if (current.distanceTo(candidate) <= closestDist * 1.7) {
                    return candidate;
                }
            }
        }

        // Fallback: If no fresh mobs remain nearby, allow bouncing to previously hit mobs
        // Pick the mob that was hit earliest in the chain (least recently struck)
        List<LivingEntity> previouslyHit = new ArrayList<>(candidates);
        previouslyHit.sort((a, b) -> {
            int lastIndexA = getLastHitIndex(hitList, a.getId());
            int lastIndexB = getLastHitIndex(hitList, b.getId());
            if (lastIndexA != lastIndexB) {
                return Integer.compare(lastIndexA, lastIndexB);
            }
            return Double.compare(current.distanceToSqr(a), current.distanceToSqr(b));
        });

        return previouslyHit.get(0);
    }

    private static boolean hasBeenHit(ListTag hitList, int entityId) {
        for (int i = 0; i < hitList.size(); i++) {
            if (hitList.getInt(i) == entityId) return true;
        }
        return false;
    }

    private static int getLastHitIndex(ListTag hitList, int entityId) {
        for (int i = hitList.size() - 1; i >= 0; i--) {
            if (hitList.getInt(i) == entityId) return i;
        }
        return -1;
    }

    public static void serverTick() {
        if (activeRicochetArrows.isEmpty()) return;

        Iterator<WeakReference<Projectile>> it = activeRicochetArrows.iterator();
        while (it.hasNext()) {
            WeakReference<Projectile> ref = it.next();
            Projectile arrow = ref.get();
            if (arrow == null || !arrow.isAlive() || arrow.onGround()) {
                if (arrow != null) {
                    arrow.setNoGravity(false);
                }
                activeRicochetArrows.remove(ref);
                continue;
            }

            if (arrow.level() instanceof ServerLevel level) {
                // Electric particle trail
                level.sendParticles(ParticleTypes.ELECTRIC_SPARK, arrow.getX(), arrow.getY(), arrow.getZ(),
                        3, 0.05, 0.05, 0.05, 0.05);

                // In-flight homing guidance toward next target
                int targetId = com.timestop.platform.EntityDataHelper.getPersistentData(arrow).getInt("RicochetTargetId");
                boolean guided = false;
                if (targetId != 0) {
                    Entity target = level.getEntity(targetId);
                    if (target instanceof LivingEntity living && living.isAlive()) {
                        Vec3 targetCoord = living.position().add(0, living.getBbHeight() * 0.6, 0);
                        Vec3 toTarget = targetCoord.subtract(arrow.position()).normalize();
                        Vec3 currentVel = arrow.getDeltaMovement();
                        Vec3 guidedVel = currentVel.normalize().scale(0.78).add(toTarget.scale(0.22)).normalize().scale(3.6);
                        arrow.setDeltaMovement(guidedVel);
                        arrow.hasImpulse = true;
                        guided = true;
                    }
                }
                if (!guided) {
                    arrow.setNoGravity(false);
                    com.timestop.platform.EntityDataHelper.getPersistentData(arrow).remove("RicochetTargetId");
                    activeRicochetArrows.remove(ref);
                }
            }
        }
    }
}