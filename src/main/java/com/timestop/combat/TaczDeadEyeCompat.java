package com.timestop.combat;

import com.timestop.TimeStopMod;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import java.lang.reflect.Method;
import java.util.function.Supplier;

/** Optional TACZ integration: delegate to its operator so ammo, bullets and gun rules remain native. */
public final class TaczDeadEyeCompat {
    public enum Result { FIRED, RETRY, STOP }
    private static Method gunLookup;
    private static Method operatorLookup;
    private static Method shoot;
    private static Method bolt;
    static {
        if (net.minecraftforge.fml.ModList.get().isLoaded("tacz")) {
            try {
                Class<?> gun = Class.forName("com.tacz.guns.api.item.IGun");
                Class<?> operator = Class.forName("com.tacz.guns.api.entity.IGunOperator");
                gunLookup = gun.getMethod("getIGunOrNull", ItemStack.class);
                operatorLookup = operator.getMethod("fromLivingEntity", LivingEntity.class);
                shoot = operator.getMethod("shoot", Supplier.class, Supplier.class);
                bolt = operator.getMethod("bolt");
            } catch (ReflectiveOperationException | LinkageError exception) {
                TimeStopMod.LOGGER.error("TACZ Dead Eye integration unavailable", exception);
                shoot = null;
            }
        }
    }
    private TaczDeadEyeCompat() {}

    public static boolean isGun(ItemStack stack) {
        if (gunLookup == null || stack.isEmpty()) return false;
        try {
            return gunLookup.invoke(null, stack) != null;
        } catch (ReflectiveOperationException exception) {
            return false;
        }
    }

    public static Result fire(LivingEntity player, Vec3 target) {
        return fire(player, new TaczPrecision.Aim(player, () -> target, null, false));
    }

    public static Result fire(LivingEntity player, LivingEntity target, boolean head) {
        return fire(player, new TaczPrecision.Aim(player,
                () -> head ? target.getEyePosition() : target.position().add(0, target.getBbHeight() * 0.65, 0),
                target.getUUID(), head));
    }

    private static Result fire(LivingEntity player, TaczPrecision.Aim aim) {
        if (shoot == null || !isGun(player.getMainHandItem())) return Result.STOP;
        Vec3 direction = aim.target().get().subtract(player.getEyePosition());
        if (direction.lengthSqr() < 1e-8 || !Double.isFinite(direction.lengthSqr())) return Result.STOP;
        try {
            Object operator = operatorLookup.invoke(null, player);
            String result = ((Enum<?>) shoot.invoke(operator, aim, (Supplier<Float>) aim::yaw)).name();
            if (result.equals("NEED_BOLT")) bolt.invoke(operator);
            return switch (result) {
                case "SUCCESS" -> Result.FIRED;
                case "COOL_DOWN", "IS_DRAWING", "IS_BOLTING", "NEED_BOLT" -> Result.RETRY;
                default -> Result.STOP;
            };
        } catch (ReflectiveOperationException | LinkageError exception) {
            TimeStopMod.LOGGER.error("TACZ rejected a Dead Eye shot", exception);
            return Result.STOP;
        }
    }
}
