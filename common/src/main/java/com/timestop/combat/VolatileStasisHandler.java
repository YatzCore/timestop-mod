package com.timestop.combat;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;

public class VolatileStasisHandler {

    public static boolean onProjectileImpact(Projectile projectile) {
        if (projectile == null || projectile.level().isClientSide) return false;

        if (com.timestop.platform.EntityDataHelper.getPersistentData(projectile).getBoolean("VolatileStasis")) {
            Level level = projectile.level();
            if (level instanceof ServerLevel serverLevel) {
                // Trigger safe non-destructive kinetic concussion blast
                serverLevel.explode(projectile, null, null,
                        projectile.getX(), projectile.getY(), projectile.getZ(),
                        2.8F, false, Level.ExplosionInteraction.NONE);

                serverLevel.sendParticles(ParticleTypes.FLASH, projectile.getX(), projectile.getY(), projectile.getZ(), 1, 0, 0, 0, 0);
                serverLevel.sendParticles(ParticleTypes.FLAME, projectile.getX(), projectile.getY() + 0.2, projectile.getZ(), 16, 0.3, 0.3, 0.3, 0.12);
                serverLevel.playSound(null, projectile.getX(), projectile.getY(), projectile.getZ(),
                        SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 1.2F, 1.4F);
                return true;
            }
        }
        return false;
    }
}