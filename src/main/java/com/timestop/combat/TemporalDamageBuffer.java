package com.timestop.combat;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.lang.ref.WeakReference;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class TemporalDamageBuffer {
    public static class DamageRecord {
        public float totalDamage = 0.0F;
        public Vec3 totalKnockback = Vec3.ZERO;
        public DamageSource lastDamageSource = null;
        public int hitCount = 0;

        public void addHit(float amount, Vec3 knockback, DamageSource source) {
            this.totalDamage += amount;
            this.totalKnockback = this.totalKnockback.add(knockback);
            this.lastDamageSource = source;
            this.hitCount++;
        }
    }

    private static final Map<UUID, DamageRecord> records = new ConcurrentHashMap<>();
    private static final Map<UUID, LivingEntity> victimEntities = new ConcurrentHashMap<>();
    private static WeakReference<ServerLevel> lastKnownLevel = new WeakReference<>(null);

    public static void recordHit(LivingEntity victim, float amount, DamageSource source) {
        records.computeIfAbsent(victim.getUUID(), k -> new DamageRecord());
        victimEntities.put(victim.getUUID(), victim);

        if (victim.level() instanceof ServerLevel serverLevel) {
            lastKnownLevel = new WeakReference<>(serverLevel);
        }

        // Calculate knockback direction from attacker
        Vec3 knockback = Vec3.ZERO;
        Entity attacker = source.getEntity();
        if (attacker != null) {
            Vec3 diff = victim.position().subtract(attacker.position()).normalize();
            knockback = new Vec3(diff.x * 0.6, 0.4, diff.z * 0.6);
        } else {
            knockback = new Vec3(0, 0.3, 0);
        }

        DamageRecord record = records.get(victim.getUUID());
        record.addHit(amount, knockback, source);

        // Immediate visual & auditory feedback during frozen time
        victim.hurtDuration = 10;
        victim.hurtTime = 10;
        victim.invulnerableTime = 0; // Allow subsequent hits during time stop!

        if (victim.level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.CRIT,
                    victim.getX(), victim.getY() + victim.getBbHeight() * 0.5, victim.getZ(),
                    15, 0.2, 0.3, 0.2, 0.1);
            serverLevel.playSound(null, victim.getX(), victim.getY(), victim.getZ(),
                    SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 1.2F, 0.7F);
        }
    }

    public static boolean hasRecord(UUID uuid) {
        return records.containsKey(uuid);
    }

    public static void dischargeAll(ServerLevel level) {
        for (Map.Entry<UUID, DamageRecord> entry : records.entrySet()) {
            UUID victimUuid = entry.getKey();
            DamageRecord record = entry.getValue();
            LivingEntity victim = victimEntities.get(victimUuid);

            if (victim == null || !victim.isAlive()) {
                Entity entity = level.getEntity(victimUuid);
                if (entity instanceof LivingEntity living) {
                    victim = living;
                }
            }

            if (victim != null && victim.isAlive()) {
                // Ensure invulnerability is cleared
                victim.invulnerableTime = 0;

                // Apply accumulated damage
                DamageSource source = record.lastDamageSource != null ? record.lastDamageSource : level.damageSources().generic();
                victim.hurt(source, record.totalDamage);

                // Apply accumulated knockback vector
                Vec3 finalKb = record.totalKnockback;
                // Cap knockback to avoid launching entities into unloaded chunks
                double maxSpeed = 4.0;
                if (finalKb.length() > maxSpeed) {
                    finalKb = finalKb.normalize().scale(maxSpeed);
                }
                victim.setDeltaMovement(victim.getDeltaMovement().add(finalKb));
                victim.hurtMarked = true;

                // Visual release shockwave
                level.sendParticles(ParticleTypes.EXPLOSION,
                        victim.getX(), victim.getY() + victim.getBbHeight() * 0.5, victim.getZ(),
                        1, 0, 0, 0, 0);
                level.playSound(null, victim.getX(), victim.getY(), victim.getZ(),
                        SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 1.0F, 1.5F);
            }
        }

        records.clear();
        victimEntities.clear();
    }

    @Nullable
    public static ServerLevel getLastKnownLevel() {
        return lastKnownLevel.get();
    }
}
