package com.timestop.core.rewind;

import com.timestop.core.rewind.data.TickFrame;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import org.joml.Vector3f;
import java.util.ArrayList;
import java.util.List;

/** Cosmetic only: physical rollback stays atomic. Hard caps also cover chain explosions. */
public final class RewindExplosionEffects {
    public static final int MAX_ACTIVE = 4;
    public static final int DURATION_TICKS = 12;
    private static final DustParticleOptions VIOLET = new DustParticleOptions(new Vector3f(0.65F, 0.3F, 1.0F), 0.65F);
    private static final List<Effect> active = new ArrayList<>();
    private RewindExplosionEffects() {}

    public static void enqueue(ServerLevel level, TickFrame.ExplosionMoment moment) {
        if (level.players().isEmpty()) return;
        double radius = Math.max(1.5, Math.min(6, moment.radius() * 1.2));
        for (Effect effect : active) {
            double dx = effect.x - moment.x(), dy = effect.y - moment.y(), dz = effect.z - moment.z();
            if (effect.level == level && dx * dx + dy * dy + dz * dz < 36) return;
        }
        if (active.size() >= MAX_ACTIVE) return;
        if (active.isEmpty()) level.playSound(null, moment.x(), moment.y(), moment.z(),
                SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 0.25F, 1.6F);
        active.add(new Effect(level, moment.x(), moment.y(), moment.z(), radius));
    }

    public static void tick() {
        active.removeIf(effect -> {
            if (effect.age % 2 == 0) {
                double spread = effect.radius * (1.0 - effect.age / (double) DURATION_TICKS);
                for (int point = 0; point < 3; point++) {
                    double angle = point * Math.PI * 2 / 3 + effect.age * 0.35;
                    double dx = Math.cos(angle) * spread, dz = Math.sin(angle) * spread;
                    double dy = 0.3 + Math.sin(angle * 2) * spread * 0.25;
                    effect.level.sendParticles(VIOLET, effect.x + dx, effect.y + dy, effect.z + dz, 1, 0, 0, 0, 0);
                    effect.level.sendParticles(ParticleTypes.END_ROD, effect.x + dx, effect.y + dy, effect.z + dz,
                            0, -dx * 0.09, -dy * 0.09, -dz * 0.09, 1);
                }
            }
            return ++effect.age >= DURATION_TICKS;
        });
    }

    public static int activeCount() { return active.size(); }
    public static void clear() { active.clear(); }

    private static final class Effect {
        final ServerLevel level;
        final double x, y, z, radius;
        int age;
        Effect(ServerLevel level, double x, double y, double z, double radius) {
            this.level = level;
            this.x = x;
            this.y = y;
            this.z = z;
            this.radius = radius;
        }
    }
}
