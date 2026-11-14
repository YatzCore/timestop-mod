package com.timestop.prediction;

import com.timestop.platform.Services;
import java.lang.reflect.Method;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/** Optional read-only gun data lookup. Never invokes shooting or inventory methods. */
public final class TaczPredictionCompat {
    private static boolean initialized;
    private static Method gunLookup, gunId, assets, gunIndex, bulletData, speed, gravity, friction;
    private TaczPredictionCompat() {}
    private static void initialize() {
        if (initialized) return;
        initialized = true;
        if (!Services.PLATFORM.isModLoaded("tacz")) return;
        try {
            Class<?> gun = Class.forName("com.tacz.guns.api.item.IGun");
            Class<?> provider = Class.forName("com.tacz.guns.resource.ICommonResourceProvider");
            Class<?> index = Class.forName("com.tacz.guns.resource.index.CommonGunIndex");
            Class<?> data = Class.forName("com.tacz.guns.resource.pojo.data.gun.BulletData");
            gunLookup = gun.getMethod("getIGunOrNull", ItemStack.class);
            gunId = gun.getMethod("getGunId", ItemStack.class);
            assets = Class.forName("com.tacz.guns.resource.CommonAssetsManager").getMethod("get");
            gunIndex = provider.getMethod("getGunIndex", ResourceLocation.class);
            bulletData = index.getMethod("getBulletData");
            speed = data.getMethod("getSpeed"); gravity = data.getMethod("getGravity"); friction = data.getMethod("getFriction");
        } catch (ReflectiveOperationException | LinkageError ex) { gunLookup = null; }
    }
    public static PredictionMotion heldProfile(Player owner) {
        initialize();
        if (gunLookup == null) return null;
        try {
            ItemStack stack = owner.getMainHandItem();
            Object gun = gunLookup.invoke(null, stack);
            if (gun == null) return null;
            Object index = gunIndex.invoke(assets.invoke(null), gunId.invoke(gun, stack));
            if (index == null) return null;
            Object data = bulletData.invoke(index);
            double velocity = ((Number) speed.invoke(data)).doubleValue() / 20;
            return new PredictionMotion(-1, owner.getId(), PredictionMotion.Kind.NATIVE, owner.getEyePosition(),
                    owner.getLookAngle().scale(velocity), Vec3.ZERO, ((Number)gravity.invoke(data)).doubleValue(),
                    Math.max(0, 1 - ((Number)friction.invoke(data)).doubleValue()), .6, .1F, .1F,
                    false, false, true, 0, 1);
        } catch (ReflectiveOperationException | LinkageError | RuntimeException ex) { return null; }
    }
}
