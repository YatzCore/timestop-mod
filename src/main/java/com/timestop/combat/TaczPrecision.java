package com.timestop.combat;

import com.timestop.TimeStopMod;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.util.ArrayDeque;
import java.util.UUID;
import java.util.function.Supplier;

@Mod.EventBusSubscriber(modid = TimeStopMod.MOD_ID)
public final class TaczPrecision {
    public record Aim(LivingEntity shooter, Supplier<Vec3> target, UUID targetId, boolean head,
                      java.util.concurrent.atomic.AtomicBoolean feedbackStarted) implements Supplier<Float> {
        public Aim(LivingEntity shooter, Supplier<Vec3> target, UUID targetId, boolean head) {
            this(shooter, target, targetId, head, new java.util.concurrent.atomic.AtomicBoolean());
        }
        @Override public Float get() {
            Vec3 direction = target.get().subtract(shooter.getEyePosition());
            return (float) -Math.toDegrees(Math.atan2(direction.y, direction.horizontalDistance()));
        }
        public Float yaw() {
            Vec3 direction = target.get().subtract(shooter.getEyePosition());
            return (float) Math.toDegrees(Math.atan2(-direction.x, direction.z));
        }
    }
    private static final class Shot {
        final Aim aim;
        boolean spawned;
        Shot(Aim aim) { this.aim = aim; }
    }
    private static final ThreadLocal<ArrayDeque<Shot>> SHOTS = ThreadLocal.withInitial(ArrayDeque::new);
    private TaczPrecision() {}

    public static void begin(Supplier<Float> pitch) {
        SHOTS.get().push(new Shot(pitch instanceof Aim aim ? aim : null));
    }

    public static void end(boolean success, ItemStack gun) {
        var stack = SHOTS.get();
        if (stack.isEmpty()) return;
        Shot shot = stack.pop();
        if (stack.isEmpty()) SHOTS.remove();
        if (success && shot.spawned && shot.aim != null && shot.aim.shooter() instanceof ServerPlayer player) {
            com.timestop.network.ModMessages.sendToPlayer(new com.timestop.network.DeadEyeGunFeedbackPacket(gun, !shot.aim.feedbackStarted().getAndSet(true)), player);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onBulletSpawn(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide || !(event.getEntity() instanceof Projectile bullet)
                || !bullet.getClass().getName().equals("com.tacz.guns.entity.EntityKineticBullet")) return;
        Shot shot = SHOTS.get().peek();
        if (shot == null || shot.aim == null || bullet.getOwner() != shot.aim.shooter()) return;
        Vec3 target = shot.aim.target().get();
        var data = bullet.getPersistentData();
        data.putBoolean("DeadEyeTaczPrecision", true);
        data.putDouble("DeadEyeAimX", target.x);
        data.putDouble("DeadEyeAimY", target.y);
        data.putDouble("DeadEyeAimZ", target.z);
        if (shot.aim.targetId() != null) data.putUUID("DeadEyeAimEntity", shot.aim.targetId());
        data.putBoolean("DeadEyeAimHead", shot.aim.head());
        guide(bullet);
        shot.spawned = true;
    }

    public static void guide(Projectile bullet) {
        var data = bullet.getPersistentData();
        if (!(bullet.level() instanceof ServerLevel level) || !data.getBoolean("DeadEyeTaczPrecision")) return;
        Vec3 target = new Vec3(data.getDouble("DeadEyeAimX"), data.getDouble("DeadEyeAimY"), data.getDouble("DeadEyeAimZ"));
        if (data.hasUUID("DeadEyeAimEntity")) {
            var entity = level.getEntity(data.getUUID("DeadEyeAimEntity"));
            if (!(entity instanceof LivingEntity living) || !living.isAlive()) {
                data.remove("DeadEyeTaczPrecision");
                return;
            }
            target = data.getBoolean("DeadEyeAimHead") ? living.getEyePosition()
                    : living.position().add(0, living.getBbHeight() * 0.65, 0);
        }
        Vec3 direction = target.subtract(bullet.position());
        double speed = bullet.getDeltaMovement().length();
        if (direction.lengthSqr() < 1e-8 || speed < 1e-8) return;
        bullet.setDeltaMovement(direction.normalize().scale(speed));
        bullet.setYRot((float) Math.toDegrees(Math.atan2(direction.x, direction.z)));
        bullet.setXRot((float) Math.toDegrees(Math.atan2(direction.y, direction.horizontalDistance())));
        bullet.hasImpulse = true;
    }
}
