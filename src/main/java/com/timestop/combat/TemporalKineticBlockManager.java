package com.timestop.combat;

import com.timestop.item.rune.RuneType;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.AnvilBlock;
import net.minecraft.world.phys.Vec3;

import java.lang.ref.WeakReference;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class TemporalKineticBlockManager {

    public static class KineticRecord {
        public Vec3 totalVelocity = Vec3.ZERO;
        public int hitCount = 0;
        public boolean isSupercharged = false;
        public boolean isVolatile = false;
        public WeakReference<Entity> entityRef;
        public UUID puncherUuid = null;

        public KineticRecord(Entity entity) {
            this.entityRef = new WeakReference<>(entity);
        }

        public void addImpulse(Vec3 impulse, boolean supercharged, boolean volatileBlast) {
            this.isSupercharged |= supercharged;
            this.isVolatile |= volatileBlast;

            double damping = 1.0 / (1.0 + (this.hitCount * 0.35));
            Vec3 scaledImpulse = impulse.scale(damping);
            this.totalVelocity = this.totalVelocity.add(scaledImpulse);

            double maxSpeed = this.isSupercharged ? 2.5 : 1.35;
            if (this.totalVelocity.length() > maxSpeed) {
                this.totalVelocity = this.totalVelocity.normalize().scale(maxSpeed);
            }
            this.hitCount++;
        }
    }

    private static final Map<UUID, KineticRecord> records = new ConcurrentHashMap<>();
    private static final List<WeakReference<FallingBlockEntity>> activeKineticBlocks = new CopyOnWriteArrayList<>();

    public static boolean hasRecord(UUID uuid) {
        return records.containsKey(uuid);
    }

    public static void recordHit(Entity entity, Vec3 impulse, Player player) {
        KineticRecord record = records.computeIfAbsent(entity.getUUID(), k -> new KineticRecord(entity));
        record.puncherUuid = player.getUUID();

        RuneType rune = RuneManager.getSocketedRuneType(player);
        boolean supercharged = (rune == RuneType.KINETIC);
        boolean volatileBlast = (rune == RuneType.VOLATILE);

        if (supercharged) {
            impulse = impulse.scale(2.5);
        }

        if (volatileBlast) {
            entity.getPersistentData().putBoolean("VolatileStasis", true);
        }

        record.addImpulse(impulse, supercharged, volatileBlast);

        if (entity.level() instanceof ServerLevel level) {
            Vec3 dir = impulse.lengthSqr() > 1e-5 ? impulse.normalize() : new Vec3(0, 0, 1);
            double centerY = entity.getY() + 0.5;

            level.sendParticles(ParticleTypes.CRIT,
                    entity.getX(), centerY, entity.getZ(),
                    supercharged ? 16 : 8,
                    dir.x * 0.3, dir.y * 0.3 + 0.1, dir.z * 0.3, 0.15);

            level.sendParticles(ParticleTypes.ELECTRIC_SPARK,
                    entity.getX(), centerY, entity.getZ(),
                    supercharged ? 12 : 5,
                    dir.x * 0.35, dir.y * 0.35, dir.z * 0.35, 0.18);

            if (volatileBlast) {
                level.sendParticles(ParticleTypes.FLAME,
                        entity.getX(), centerY, entity.getZ(),
                        8, 0.2, 0.2, 0.2, 0.05);
            }

            float pitch = Math.min(1.8F, 1.0F + (record.hitCount * 0.12F));
            level.playSound(null, entity.getX(), entity.getY(), entity.getZ(),
                    SoundEvents.ANVIL_PLACE, SoundSource.BLOCKS, 1.2F, pitch);
            level.playSound(null, entity.getX(), entity.getY(), entity.getZ(),
                    SoundEvents.PLAYER_ATTACK_STRONG, SoundSource.PLAYERS, 1.0F, pitch);
        }
    }

    public static void dischargeAll(ServerLevel level) {
        if (records.isEmpty()) return;
        net.minecraft.server.MinecraftServer server = level.getServer();
        for (Map.Entry<UUID, KineticRecord> entry : new ArrayList<>(records.entrySet())) {
            UUID entityUuid = entry.getKey();
            KineticRecord record = entry.getValue();
            Entity entity = record.entityRef.get();
            ServerLevel targetLevel = level;

            if (entity != null && entity.isAlive() && entity.level() instanceof ServerLevel sl) {
                targetLevel = sl;
            } else {
                for (ServerLevel sl : server.getAllLevels()) {
                    Entity e = sl.getEntity(entityUuid);
                    if (e != null && e.isAlive()) {
                        entity = e;
                        targetLevel = sl;
                        break;
                    }
                }
            }

            if (entity != null && entity.isAlive()) {
                applyDischargeSingle(targetLevel, entity, record);
            }
        }
        records.clear();
    }

    public static void dischargeInArea(ServerLevel level, Vec3 center, double radius) {
        double rSq = radius * radius;
        List<UUID> toDischarge = new ArrayList<>();
        for (UUID uuid : records.keySet()) {
            KineticRecord record = records.get(uuid);
            Entity entity = record != null ? record.entityRef.get() : null;
            if (entity == null || !entity.isAlive()) {
                entity = level.getEntity(uuid);
            }
            if (entity != null && entity.level() == level && entity.distanceToSqr(center) <= rSq) {
                toDischarge.add(uuid);
            }
        }
        for (UUID uuid : toDischarge) {
            KineticRecord record = records.remove(uuid);
            if (record != null) {
                Entity entity = record.entityRef.get();
                if (entity == null || !entity.isAlive()) {
                    entity = level.getEntity(uuid);
                }
                if (entity != null && entity.isAlive()) {
                    applyDischargeSingle(level, entity, record);
                }
            }
        }
    }

    private static void applyDischargeSingle(ServerLevel level, Entity entity, KineticRecord record) {
        Vec3 finalVel = record.totalVelocity;

        if (entity instanceof FallingBlockEntity fallingBlock) {
            fallingBlock.setNoGravity(false);
            fallingBlock.dropItem = true;
            fallingBlock.time = 1;
            if (record.puncherUuid != null) {
                fallingBlock.getPersistentData().putUUID("KineticPuncherUuid", record.puncherUuid);
            }

            if (fallingBlock.getBlockState().getBlock() instanceof AnvilBlock) {
                fallingBlock.setHurtsEntities(8.0F, 60);
            }

            fallingBlock.setDeltaMovement(finalVel);
            fallingBlock.hasImpulse = true;
            activeKineticBlocks.add(new WeakReference<>(fallingBlock));
        } else if (entity instanceof PrimedTnt tnt) {
            tnt.setDeltaMovement(finalVel);
            tnt.hasImpulse = true;
        } else {
            entity.setDeltaMovement(finalVel);
            entity.hasImpulse = true;
        }

        double centerY = entity.getY() + 0.5;
        level.sendParticles(ParticleTypes.POOF,
                entity.getX(), centerY, entity.getZ(),
                6, 0.2, 0.2, 0.2, 0.04);
        level.sendParticles(ParticleTypes.CRIT,
                entity.getX(), centerY, entity.getZ(),
                record.isSupercharged ? 24 : 12, finalVel.x * 0.2, finalVel.y * 0.2 + 0.1, finalVel.z * 0.2, 0.15);
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK,
                entity.getX(), centerY, entity.getZ(),
                record.isSupercharged ? 16 : 8, 0.15, 0.15, 0.15, 0.1);

        if (record.isSupercharged) {
            level.sendParticles(ParticleTypes.SONIC_BOOM,
                    entity.getX(), centerY, entity.getZ(),
                    1, 0, 0, 0, 0);
            level.playSound(null, entity.getX(), entity.getY(), entity.getZ(),
                    SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 1.2F, 1.8F);
        }

        level.playSound(null, entity.getX(), entity.getY(), entity.getZ(),
                SoundEvents.GENERIC_EXPLODE, SoundSource.BLOCKS, 0.8F, 1.6F);
        level.playSound(null, entity.getX(), entity.getY(), entity.getZ(),
                SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 1.4F, 0.8F);
    }

    public static void serverTick() {
        if (activeKineticBlocks.isEmpty()) return;

        Iterator<WeakReference<FallingBlockEntity>> it = activeKineticBlocks.iterator();
        while (it.hasNext()) {
            WeakReference<FallingBlockEntity> ref = it.next();
            FallingBlockEntity block = ref.get();
            if (block == null || !block.isAlive() || block.onGround() || block.getDeltaMovement().lengthSqr() < 0.04) {
                if (block != null && block.getPersistentData().getBoolean("VolatileStasis")) {
                    detonateVolatileBlast(block);
                }
                activeKineticBlocks.remove(ref);
                continue;
            }

            if (block.level() instanceof ServerLevel level) {
                // In-flight kinetic trail
                if (level.getGameTime() % 2 == 0) {
                    level.sendParticles(ParticleTypes.CRIT,
                            block.getX(), block.getY() + 0.4, block.getZ(),
                            2, 0.08, 0.08, 0.08, 0.02);
                }

                // Check collision with living entities
                List<LivingEntity> targets = level.getEntitiesOfClass(LivingEntity.class, block.getBoundingBox().inflate(0.5));
                UUID puncherUuid = block.getPersistentData().hasUUID("KineticPuncherUuid") ? block.getPersistentData().getUUID("KineticPuncherUuid") : null;
                for (LivingEntity target : targets) {
                    if (target.isAlive()) {
                        if (puncherUuid != null && target.getUUID().equals(puncherUuid)) {
                            continue; // Prevent self-damage to puncher!
                        }
                        float damage = (float) (block.getDeltaMovement().length() * 12.0F);
                        target.hurt(level.damageSources().fallingBlock(block), Math.max(6.0F, damage));
                        target.setDeltaMovement(target.getDeltaMovement().add(block.getDeltaMovement().scale(0.6)));
                        target.hurtMarked = true;

                        level.sendParticles(ParticleTypes.CRIT,
                                target.getX(), target.getY() + target.getBbHeight() * 0.5, target.getZ(),
                                15, 0.2, 0.2, 0.2, 0.15);
                        level.playSound(null, target.getX(), target.getY(), target.getZ(),
                                SoundEvents.ANVIL_LAND, SoundSource.BLOCKS, 1.5F, 0.9F);

                        if (block.getPersistentData().getBoolean("VolatileStasis")) {
                            detonateVolatileBlast(block);
                            block.discard();
                            activeKineticBlocks.remove(ref);
                            break;
                        }
                    }
                }
            }
        }
    }

    public static void detonateVolatileBlast(Entity entity) {
        if (entity.level() instanceof ServerLevel level) {
            level.explode(entity, null, null,
                    entity.getX(), entity.getY(), entity.getZ(),
                    2.8F, false, Level.ExplosionInteraction.NONE);
            level.sendParticles(ParticleTypes.FLASH, entity.getX(), entity.getY(), entity.getZ(), 1, 0, 0, 0, 0);
            level.sendParticles(ParticleTypes.FLAME, entity.getX(), entity.getY() + 0.5, entity.getZ(), 20, 0.4, 0.4, 0.4, 0.15);
            level.playSound(null, entity.getX(), entity.getY(), entity.getZ(), SoundEvents.GENERIC_EXPLODE, SoundSource.BLOCKS, 1.2F, 1.4F);
        }
    }
}
