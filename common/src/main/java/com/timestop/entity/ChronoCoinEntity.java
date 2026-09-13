package com.timestop.entity;

import com.timestop.combat.TemporalDamageBuffer;
import com.timestop.item.ModItems;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.Comparator;
import java.util.List;

public class ChronoCoinEntity extends ThrowableItemProjectile {

    private int coinMultiplier = 1;

    public ChronoCoinEntity(EntityType<? extends ThrowableItemProjectile> type, Level level) {
        super(type, level);
    }

    public ChronoCoinEntity(Level level, LivingEntity shooter) {
        super(ModEntities.CHRONO_COIN.get(), shooter, level);
    }

    public ChronoCoinEntity(Level level, double x, double y, double z) {
        super(ModEntities.CHRONO_COIN.get(), x, y, z, level);
    }

    @Override
    protected Item getDefaultItem() {
        return ModItems.CHRONO_COIN.get();
    }

    public void setMultiplier(int mult) {
        this.coinMultiplier = mult;
    }

    public int getMultiplier() {
        return this.coinMultiplier;
    }

    @Override
    public boolean isPickable() {
        return true;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (!this.level().isClientSide && this.isAlive()) {
            triggerRicoshot(source.getEntity());
            return true;
        }
        return super.hurt(source, amount);
    }

    @Override
    public void tick() {
        super.tick();

        if (this.level().isClientSide) {
            if (this.random.nextFloat() < 0.45F) {
                this.level().addParticle(ParticleTypes.ELECTRIC_SPARK, this.getX(), this.getY() + 0.1, this.getZ(),
                        (this.random.nextDouble() - 0.5) * 0.05, 0.05, (this.random.nextDouble() - 0.5) * 0.05);
            }
            return;
        }

        // Check if any in-flight projectile intersects our generous coin hit box
        AABB hitBox = this.getBoundingBox().inflate(0.40);
        List<Projectile> projectiles = this.level().getEntitiesOfClass(Projectile.class, hitBox,
                p -> p != this && p.isAlive() && !p.onGround());

        if (!projectiles.isEmpty()) {
            Projectile incoming = projectiles.get(0);
            triggerRicoshot(incoming.getOwner() != null ? incoming.getOwner() : incoming);
            incoming.discard();
        }

        if (this.tickCount > 200 || this.onGround()) {
            this.discard();
        }
    }

    public void triggerRicoshot(Entity shooter) {
        if (this.isRemoved()) return;
        this.discard();

        if (!(this.level() instanceof ServerLevel sl)) return;

        // Iconic metallic DING audio!
        sl.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 2.0F, 2.0F);
        sl.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.8F, 2.0F);
        sl.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1.2F, 2.0F);

        // Check for chaining to another airborne coin nearby
        AABB coinSearch = this.getBoundingBox().inflate(24.0);
        List<ChronoCoinEntity> otherCoins = sl.getEntitiesOfClass(ChronoCoinEntity.class, coinSearch,
                c -> c != this && c.isAlive());

        if (!otherCoins.isEmpty()) {
            // Chain to the nearest other coin!
            otherCoins.sort(Comparator.comparingDouble(c -> c.distanceToSqr(this)));
            ChronoCoinEntity nextCoin = otherCoins.get(0);

            drawBeam(sl, this.position(), nextCoin.position());

            if (shooter instanceof ServerPlayer sp) {
                sp.displayClientMessage(Component.literal("⚡ + DUAL RICOSHOT! (x" + (coinMultiplier * 2) + ")").withStyle(net.minecraft.ChatFormatting.GOLD, net.minecraft.ChatFormatting.BOLD), true);
            }

            nextCoin.setMultiplier(this.coinMultiplier * 2);
            nextCoin.triggerRicoshot(shooter);
            return;
        }

        // Find nearest living enemy target
        AABB enemySearch = this.getBoundingBox().inflate(32.0);
        List<LivingEntity> enemies = sl.getEntitiesOfClass(LivingEntity.class, enemySearch,
                e -> e.isAlive() && !e.isSpectator() && e != shooter && !(shooter instanceof Player p && p.getTeam() != null && e.getTeam() != null && p.getTeam().isAlliedTo(e.getTeam())));

        LivingEntity bestTarget = null;
        double bestDist = Double.MAX_VALUE;
        for (LivingEntity e : enemies) {
            double d = e.distanceToSqr(this);
            if (d < bestDist) {
                bestDist = d;
                bestTarget = e;
            }
        }

        if (bestTarget != null) {
            Vec3 targetHead = bestTarget.getEyePosition();
            drawBeam(sl, this.position(), targetHead);

            float damage = 22.0F * this.coinMultiplier;
            DamageSource ds = shooter instanceof LivingEntity le
                    ? sl.damageSources().mobAttack(le)
                    : sl.damageSources().generic();

            if (com.timestop.core.TemporalBubbleManager.isEntityInStasis(bestTarget)) {
                TemporalDamageBuffer.recordHit(bestTarget, damage, ds);
                bestTarget.level().broadcastEntityEvent(bestTarget, (byte) 2);
            } else {
                bestTarget.hurt(ds, damage);
            }

            sl.sendParticles(ParticleTypes.SONIC_BOOM, targetHead.x, targetHead.y, targetHead.z, 1, 0, 0, 0, 0);
            sl.sendParticles(ParticleTypes.CRIT, targetHead.x, targetHead.y, targetHead.z, 16, 0.2, 0.2, 0.2, 0.2);

            if (shooter instanceof ServerPlayer sp) {
                String label = this.coinMultiplier > 1
                        ? "⚡ + ULTRA RICOSHOT! (" + (int)damage + " DMG)"
                        : "⚡ + RICOSHOT!";
                sp.displayClientMessage(Component.literal(label).withStyle(net.minecraft.ChatFormatting.GOLD, net.minecraft.ChatFormatting.BOLD), true);
            }
        }
    }

    private void drawBeam(ServerLevel sl, Vec3 from, Vec3 to) {
        Vec3 diff = to.subtract(from);
        double dist = diff.length();
        Vec3 step = diff.normalize().scale(0.35);
        int steps = (int) (dist / 0.35);

        Vec3 cur = from;
        for (int i = 0; i < steps; i++) {
            sl.sendParticles(ParticleTypes.ELECTRIC_SPARK, cur.x, cur.y, cur.z, 1, 0, 0, 0, 0.0);
            cur = cur.add(step);
        }
    }
}