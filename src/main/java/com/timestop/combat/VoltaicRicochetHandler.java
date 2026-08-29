package com.timestop.combat;

import com.timestop.core.TimeMode;
import com.timestop.core.TimeStopManager;
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
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.ProjectileImpactEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import com.timestop.TimeStopMod;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = TimeStopMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class VoltaicRicochetHandler {

    public static final int MAX_CHAIN_COUNT = 6;
    public static final double CHAIN_RADIUS = 9.0;
    public static final double CHAIN_RADIUS_SQR = 81.0;
    private static final List<WeakReference<AbstractArrow>> activeRicochetArrows = new CopyOnWriteArrayList<>();

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onProjectileImpact(ProjectileImpactEvent event) {
        if (!(event.getProjectile() instanceof AbstractArrow arrow)) return;
        if (!(arrow.level() instanceof ServerLevel level)) return;

        Entity shooter = arrow.getOwner();
        if (!(shooter instanceof Player player)) return;

        // Must have Voltaic Ricochet rune equipped (works at any time, just like Dead Eye!)
        if (RuneManager.getSocketedRuneType(player) != RuneType.RICOCHET) return;

        HitResult hit = event.getRayTraceResult();
        if (!(hit instanceof EntityHitResult entityHit)) return;

        Entity hitEntity = entityHit.getEntity();
        if (!(hitEntity instanceof LivingEntity victim) || !victim.isAlive()) return;

        ChainTargetFilter filter = RuneManager.getActiveChainFilter(player);
        if (!filter.matches(victim)) return;

        // Damage the victim with electrical kinetic impact
        victim.hurt(level.damageSources().arrow(arrow, player), 10.0F);
        victim.invulnerableTime = 0; // Clear immunity frames so ping-pong damage registers immediately
        victim.hurtTime = 0;
        victim.hurtDuration = 0;

        // Audio & Particle FX on hit
        level.playSound(null, victim.getX(), victim.getY(), victim.getZ(),
                SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.PLAYERS, 1.2F, 1.8F);
        level.playSound(null, victim.getX(), victim.getY(), victim.getZ(),
                SoundEvents.ARROW_HIT_PLAYER, SoundSource.PLAYERS, 1.2F, 1.4F);

        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, victim.getX(), victim.getY() + victim.getBbHeight() * 0.5, victim.getZ(),
                18, 0.4, 0.4, 0.4, 0.2);

        // Update hit list and chain count
        CompoundTag tag = arrow.getPersistentData();
        ListTag hitList = tag.getList("RicochetHitList", Tag.TAG_INT);
        hitList.add(IntTag.valueOf(victim.getId()));
        tag.put("RicochetHitList", hitList);

        int chainCount = tag.getInt("RicochetChainCount") + 1;
        tag.putInt("RicochetChainCount", chainCount);

        // Check if chain continues (must hit exactly MAX_CHAIN_COUNT times if any eligible targets exist)
        if (chainCount < MAX_CHAIN_COUNT) {
            LivingEntity nextTarget = findNextTarget(level, victim, hitList, filter, player);
            if (nextTarget != null) {
                // Cancel arrow impact discard so the arrow continues flying to the next target!
                event.setCanceled(true);

                // Reposition arrow at center of current victim
                Vec3 launchPos = victim.position().add(0, victim.getBbHeight() * 0.6, 0);
                arrow.setPos(launchPos.x, launchPos.y, launchPos.z);

                Vec3 targetPos = nextTarget.position().add(0, nextTarget.getBbHeight() * 0.6, 0);
                Vec3 dir = targetPos.subtract(launchPos).normalize();

                arrow.shoot(dir.x, dir.y, dir.z, 3.6F, 0.0F);
                arrow.setNoGravity(true);
                arrow.setCritArrow(true);
                arrow.pickup = AbstractArrow.Pickup.DISALLOWED;
                arrow.hasImpulse = true;

                tag.putInt("RicochetTargetId", nextTarget.getId());
                activeRicochetArrows.add(new WeakReference<>(arrow));

                // Electric bounce feedback
                level.playSound(null, launchPos.x, launchPos.y, launchPos.z,
                        SoundEvents.ARROW_SHOOT, SoundSource.PLAYERS, 1.2F, 1.8F);
                level.sendParticles(ParticleTypes.SONIC_BOOM, launchPos.x, launchPos.y, launchPos.z,
                        1, 0, 0, 0, 0);
                return;
            }
        }

        // End of chain (or no eligible targets remain): final thunder discharge flash
        level.sendParticles(ParticleTypes.FLASH, victim.getX(), victim.getY() + 1.0, victim.getZ(), 1, 0, 0, 0, 0);
        arrow.discard();
        event.setCanceled(true);
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

        // If the closest candidate is unhit, take it immediately!
        if (!hasBeenHit(hitList, closest.getId())) {
            return closest;
        }

        // Check if there are unhit candidates nearby (within 3.5 blocks of closest)
        List<LivingEntity> unhit = new ArrayList<>();
        for (LivingEntity e : candidates) {
            if (!hasBeenHit(hitList, e.getId())) {
                unhit.add(e);
            }
        }

        if (!unhit.isEmpty()) {
            LivingEntity closestUnhit = unhit.get(0);
            double unhitDist = current.distanceTo(closestUnhit);
            // Only prioritize unhit if it's in the immediate cluster (doesn't leap across the field)
            if (unhitDist <= closestDist + 3.5) {
                return closestUnhit;
            }
        }

        // Otherwise, among the close cluster, pick the one hit least recently to ping-pong cleanly
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

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || activeRicochetArrows.isEmpty()) return;

        Iterator<WeakReference<AbstractArrow>> it = activeRicochetArrows.iterator();
        while (it.hasNext()) {
            WeakReference<AbstractArrow> ref = it.next();
            AbstractArrow arrow = ref.get();
            if (arrow == null || !arrow.isAlive() || arrow.onGround()) {
                activeRicochetArrows.remove(ref);
                continue;
            }

            if (arrow.level() instanceof ServerLevel level) {
                // Electric particle trail
                level.sendParticles(ParticleTypes.ELECTRIC_SPARK, arrow.getX(), arrow.getY(), arrow.getZ(),
                        3, 0.05, 0.05, 0.05, 0.05);

                // In-flight homing guidance toward next target
                int targetId = arrow.getPersistentData().getInt("RicochetTargetId");
                if (targetId != 0) {
                    Entity target = level.getEntity(targetId);
                    if (target instanceof LivingEntity living && living.isAlive()) {
                        Vec3 targetCoord = living.position().add(0, living.getBbHeight() * 0.6, 0);
                        Vec3 toTarget = targetCoord.subtract(arrow.position()).normalize();
                        Vec3 currentVel = arrow.getDeltaMovement();
                        Vec3 guided = currentVel.normalize().scale(0.78).add(toTarget.scale(0.22)).normalize().scale(3.6);
                        arrow.setDeltaMovement(guided);
                        arrow.hasImpulse = true;
                    }
                }
            }
        }
    }
}
