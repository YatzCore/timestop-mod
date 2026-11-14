package com.timestop.combat;

import com.timestop.core.TimeStopManager;
import com.timestop.entity.ChronoCoinEntity;
import com.timestop.item.rune.RuneType;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.*;

/** Optional native projectile integration; no TacZ classes load when the mod is absent. */
public final class TaczProjectileCompat {
    private TaczProjectileCompat() {}
    public static boolean isBullet(Projectile p) {
        return p.getClass().getName().equals("com.tacz.guns.entity.EntityKineticBullet");
    }

    /** Native bullets bypass Projectile.onHit; loader event handlers skip these bullets. */
    public static boolean beforeImpact(Projectile bullet, HitResult hit) {
        if (bullet.level().isClientSide || hit.getType() == HitResult.Type.MISS) return false;
        if (ProjectileStasisSweep.beforeImpact(bullet, hit.getLocation())) return true;
        if (OrbitalProjectileManager.onProjectileImpact(bullet, hit)) return true;
        VolatileStasisHandler.onProjectileImpact(bullet);
        return KineticPalmManager.onDroppedProjectileImpact(bullet);
    }

    /** Called only after the native hit body completes, before native piercing/discard. */
    public static boolean afterEntityImpact(Projectile bullet, EntityHitResult hit) {
        return VoltaicRicochetHandler.onProjectileImpact(bullet, hit);
    }

    public static boolean skipRicochetTarget(Projectile bullet, net.minecraft.world.entity.Entity target) {
        if (!isBullet(bullet)) return false;
        var data = com.timestop.platform.EntityDataHelper.getPersistentData(bullet);
        var hits = data.getList("RicochetHitList", net.minecraft.nbt.Tag.TAG_INT);
        return !hits.isEmpty() && hits.getInt(hits.size() - 1) == target.getId();
    }

    public static boolean beforeTick(Projectile bullet) {
        var data = com.timestop.platform.EntityDataHelper.getPersistentData(bullet);
        if (!bullet.isAlive() || data.getBoolean("KineticPalmCaptured") || data.getBoolean("InStasisOrbit")) return true;
        if (!(bullet.level() instanceof ServerLevel level)) return false;
        if (ProjectileStasisSweep.intercept(bullet)) return true;
        KineticPalmManager.interceptIncoming(bullet);
        OrbitalProjectileManager.interceptIncoming(bullet);
        if (data.getBoolean("KineticPalmCaptured") || data.getBoolean("InStasisOrbit")) return true;
        if (TimeStopManager.isProjectileSuspended(bullet)) return true;
        Vec3 start = bullet.position();
        Vec3 end = start.add(bullet.getDeltaMovement().scale(DecelerationFieldManager.getMovementScale(bullet)));
        var wall = level.clip(new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, bullet));
        if (wall.getType() != HitResult.Type.MISS) end = wall.getLocation();
        // Sweep defenses along the whole native step: bullets can cross a field in one tick.
        net.minecraft.world.entity.player.Player defender = null;
        Vec3 entry = null;
        double closest = Double.MAX_VALUE;
        for (var player : level.players()) {
            if (player == bullet.getOwner() || !player.isAlive() || player.isSpectator()) continue;
            // Creative Watch matrix runes also participate in the native sweep.
            double radius = RuneManager.hasRune(player, RuneType.DEFLECTION) ? 3.4
                    : RuneManager.hasRune(player, RuneType.SNATCHING) ? 2.9
                    : RuneManager.hasRune(player, RuneType.PHASING) ? 2.4 : 0;
            radius = Math.min(radius, DecelerationFieldManager.getDecelerationRadius(player));
            if (radius <= 0) continue;
            Vec3 center = player.position().add(0, player.getEyeHeight() * 0.5, 0);
            Vec3 travel = end.subtract(start);
            double t = travel.lengthSqr() < 1e-12 ? 0 : Math.max(0, Math.min(1, center.subtract(start).dot(travel) / travel.lengthSqr()));
            Vec3 near = start.add(travel.scale(t));
            if (near.distanceToSqr(center) > radius * radius) continue;
            // First entry into the defense sphere, not its far side or the player's body.
            double length = travel.length();
            double offset = Math.sqrt(Math.max(0, radius * radius - near.distanceToSqr(center)));
            Vec3 point = length < 1e-12 ? start : start.add(travel.scale(Math.max(0, t - offset / length)));
            if (start.distanceToSqr(point) < closest) {
                closest = start.distanceToSqr(point); defender = player; entry = point;
            }
        }
        if (defender != null) {
            bullet.setPos(entry);
            RuneManager.evaluateRuneDefense(bullet, defender);
            if (!bullet.isAlive() || bullet.getOwner() == defender || data.getBoolean("InStasisOrbit")) return true;
            bullet.setPos(start);
        }
        // Coins use a swept hit box so native high-speed bullets cannot tunnel through them.
        ChronoCoinEntity coinHit = null;
        double coinDistance = Double.MAX_VALUE;
        for (var coin : level.getEntitiesOfClass(ChronoCoinEntity.class, new AABB(start, end).inflate(0.5), c -> c.isAlive() && !c.onGround())) {
            var box = coin.getBoundingBox().inflate(0.4);
            var hit = box.contains(start) ? java.util.Optional.of(start) : box.clip(start, end);
            if (hit.isPresent() && start.distanceToSqr(hit.get()) < coinDistance) {
                coinDistance = start.distanceToSqr(hit.get()); coinHit = coin;
            }
        }
        if (coinHit != null) {
            coinHit.triggerRicoshot(bullet.getOwner() == null ? bullet : bullet.getOwner());
            bullet.discard();
            return true;
        }
        return false;
    }

    public static ItemStack ammunition(Projectile bullet) {
        if (!isBullet(bullet)) return ItemStack.EMPTY;
        try {
            Object id = bullet.getClass().getMethod("getAmmoId").invoke(bullet);
            Class<?> type = Class.forName("com.tacz.guns.api.item.builder.AmmoItemBuilder");
            Object builder = type.getMethod("create").invoke(null);
            type.getMethod("setId", net.minecraft.resources.ResourceLocation.class).invoke(builder, id);
            type.getMethod("setCount", int.class).invoke(builder, 1);
            return (ItemStack) type.getMethod("build").invoke(builder);
        } catch (ReflectiveOperationException | LinkageError e) {
            return ItemStack.EMPTY;
        }
    }
}
