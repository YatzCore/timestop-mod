package com.timestop.combat;

import com.timestop.core.TimeStopManager;
import com.timestop.item.rune.RuneType;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
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
        public boolean isSuperchargedKinetic = false;

        public void addHit(float amount, Vec3 knockback, DamageSource source) {
            this.totalDamage += amount;
            this.totalKnockback = this.totalKnockback.add(knockback);
            this.lastDamageSource = source;
            this.hitCount++;
        }
    }

    private static final Map<UUID, DamageRecord> records = new ConcurrentHashMap<>();
    private static final Map<UUID, LivingEntity> victimEntities = new ConcurrentHashMap<>();
    private static final Set<UUID> leechedMobsThisSession = ConcurrentHashMap.newKeySet();
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

        // Check Rune enhancements
        if (attacker instanceof Player player) {
            RuneType rune = RuneManager.getSocketedRuneType(player);
            if (rune == RuneType.KINETIC) {
                amount *= 1.5F; // +50% punch damage
                knockback = knockback.scale(2.5D); // 2.5x launch force!
                record.isSuperchargedKinetic = true;
            } else if (rune == RuneType.VAMPIRISM) {
                // Siphons duration with per-mob diminishing returns and double-duration cap
                if (leechedMobsThisSession.add(victim.getUUID())) {
                    boolean isLethal = (victim.getHealth() - (record.totalDamage + amount)) <= 0;
                    int bonusTicks = isLethal ? 30 : 10;
                    boolean extended = com.timestop.core.TemporalBubbleManager.extendPlayerBubble(player.getUUID(), bonusTicks)
                            || TimeStopManager.extendTimeStop(bonusTicks);
                    if (extended) {
                        player.displayClientMessage(Component.literal("+" + (bonusTicks / 20.0F) + "s Chrono-Leech!").withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD), true);
                        if (victim.level() instanceof ServerLevel sl) {
                            sl.sendParticles(ParticleTypes.DAMAGE_INDICATOR, victim.getX(), victim.getY() + 1.0, victim.getZ(), 6, 0.2, 0.2, 0.2, 0.1);
                        }
                    }
                }
            }
        }

        record.addHit(amount, knockback, source);

        // Immediate visual & auditory feedback during frozen time
        victim.hurtDuration = 10;
        victim.hurtTime = 10;
        victim.invulnerableTime = 0; // Allow subsequent hits during time stop!

        if (victim.level() instanceof ServerLevel serverLevel) {
            Vec3 look = attacker != null ? attacker.getLookAngle().normalize() : new Vec3(0, 0, 1);
            double hitX = victim.getX() - look.x * 0.3;
            double hitY = victim.getY() + victim.getEyeHeight() * 0.7;
            double hitZ = victim.getZ() - look.z * 0.3;

            serverLevel.sendParticles(ParticleTypes.CRIT,
                    hitX, hitY, hitZ,
                    record.isSuperchargedKinetic ? 12 : 6,
                    look.x * 0.25, look.y * 0.25 + 0.1, look.z * 0.25, 0.12);

            serverLevel.sendParticles(ParticleTypes.ELECTRIC_SPARK,
                    hitX, hitY, hitZ,
                    record.isSuperchargedKinetic ? 10 : 4,
                    look.x * 0.3, look.y * 0.3, look.z * 0.3, 0.15);

            float pitch = Math.min(1.8F, 1.0F + (record.hitCount * 0.1F));
            serverLevel.playSound(null, victim.getX(), victim.getY(), victim.getZ(),
                    SoundEvents.PLAYER_ATTACK_STRONG, SoundSource.PLAYERS, 1.0F, pitch);
        }
    }

    public static boolean hasRecord(UUID uuid) {
        return records.containsKey(uuid);
    }

    public static Map<UUID, DamageRecord> getRecords() {
        return Collections.unmodifiableMap(records);
    }

    public static LivingEntity getVictim(UUID uuid) {
        return victimEntities.get(uuid);
    }

    public static void dischargeEntity(ServerLevel level, UUID victimUuid) {
        DamageRecord record = records.remove(victimUuid);
        LivingEntity victim = victimEntities.remove(victimUuid);
        if (record == null) return;
        if (victim == null || !victim.isAlive()) {
            Entity entity = level.getEntity(victimUuid);
            if (entity instanceof LivingEntity living) {
                victim = living;
            }
        }
        if (victim != null && victim.isAlive()) {
            applyDischarge(level, victim, record);
        }
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
                applyDischarge(level, victim, record);
            }
        }

        records.clear();
        victimEntities.clear();
        leechedMobsThisSession.clear();
    }

    private static void applyDischarge(ServerLevel level, LivingEntity victim, DamageRecord record) {
        // Ensure invulnerability is cleared
        victim.invulnerableTime = 0;

        // Apply accumulated damage
        DamageSource source = record.lastDamageSource != null ? record.lastDamageSource : level.damageSources().generic();
        victim.hurt(source, record.totalDamage);

        // Apply accumulated knockback vector
        Vec3 finalKb = record.totalKnockback;
        // Cap knockback to avoid launching entities into unloaded chunks
        double maxSpeed = record.isSuperchargedKinetic ? 8.0 : 4.0;
        if (finalKb.length() > maxSpeed) {
            finalKb = finalKb.normalize().scale(maxSpeed);
        }
        victim.setDeltaMovement(victim.getDeltaMovement().add(finalKb));
        victim.hurtMarked = true;
        victim.hasImpulse = true;

        // Broadcast motion packet immediately to all nearby clients so the launch trajectory is animated smoothly
        level.getChunkSource().broadcast(victim, new net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket(victim));
        if (victim instanceof ServerPlayer sp) {
            sp.connection.send(new net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket(victim));
        }

        // Focused kinetic impact discharge
        double centerY = victim.getY() + victim.getBbHeight() * 0.5;
        level.sendParticles(ParticleTypes.SWEEP_ATTACK,
                victim.getX(), centerY, victim.getZ(),
                1, 0, 0, 0, 0);
        level.sendParticles(ParticleTypes.CRIT,
                victim.getX(), centerY, victim.getZ(),
                Math.min(35, 12 + record.hitCount * 5), 0.25, 0.35, 0.25, 0.18);
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK,
                victim.getX(), centerY, victim.getZ(),
                10, 0.2, 0.2, 0.2, 0.12);

        if (record.isSuperchargedKinetic) {
            level.sendParticles(ParticleTypes.SONIC_BOOM,
                    victim.getX(), centerY, victim.getZ(),
                    1, 0, 0, 0, 0);
            level.playSound(null, victim.getX(), victim.getY(), victim.getZ(),
                    SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 1.2F, 1.8F);
        }

        level.playSound(null, victim.getX(), victim.getY(), victim.getZ(),
                SoundEvents.PLAYER_ATTACK_KNOCKBACK, SoundSource.PLAYERS, 1.2F, 1.2F);
        level.playSound(null, victim.getX(), victim.getY(), victim.getZ(),
                SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 0.6F, 1.6F);
    }

    @Nullable
    public static ServerLevel getLastKnownLevel() {
        return lastKnownLevel.get();
    }
}
