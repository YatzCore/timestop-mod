package com.timestop.client;

import com.timestop.TimeStopMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Optional;

/** Calls the installed TACZ local effects callback, without its shoot/ammo/network stage. */
public final class TaczDeadEyeFeedback {
    private static boolean unavailable;
    private static Method callback;
    private static Constructor<?> controllerConstructor;
    private TaczDeadEyeFeedback() {}

    public static void play(ItemStack firedGun, boolean firstRound) {
        var player = Minecraft.getInstance().player;
        if (player == null || unavailable || !ItemStack.isSameItem(player.getMainHandItem(), firedGun)) return;
        try {
            Class<?> gunApi = Class.forName("com.tacz.guns.api.item.IGun");
            Object gun = gunApi.getMethod("getIGunOrNull", ItemStack.class).invoke(null, firedGun);
            if (gun == null) return;
            Object id = gunApi.getMethod("getGunId", ItemStack.class).invoke(gun, firedGun);
            Object heldId = gunApi.getMethod("getGunId", ItemStack.class).invoke(gun, player.getMainHandItem());
            Object displayId = gunApi.getMethod("getGunDisplayId", ItemStack.class).invoke(gun, firedGun);
            Object heldDisplay = gunApi.getMethod("getGunDisplayId", ItemStack.class).invoke(gun, player.getMainHandItem());
            if (!id.equals(heldId) || !java.util.Objects.equals(displayId, heldDisplay)) return;
            Class<?> timeless = Class.forName("com.tacz.guns.api.TimelessAPI");
            Object display = ((Optional<?>) timeless.getMethod("getGunDisplay", ItemStack.class).invoke(null, firedGun)).orElse(null);
            Object index = ((Optional<?>) timeless.getMethod("getCommonGunIndex", net.minecraft.resources.ResourceLocation.class)
                    .invoke(null, id)).orElse(null);
            if (display == null || index == null) return;
            Object gunData = index.getClass().getMethod("getGunData").invoke(index);
            Class<?> operatorApi = Class.forName("com.tacz.guns.api.client.gameplay.IClientPlayerGunOperator");
            Object operator = operatorApi.getMethod("fromLocalPlayer", LocalPlayer.class).invoke(null, player);
            Object data = operatorApi.getMethod("getDataHolder").invoke(operator);
            if (callback == null) {
                Class<?> controller = Class.forName("com.tacz.guns.client.gameplay.LocalPlayerShoot");
                Class<?> holder = Class.forName("com.tacz.guns.client.gameplay.LocalPlayerDataHolder");
                controllerConstructor = controller.getConstructor(holder, LocalPlayer.class);
                // This callback posts GunFireEvent, triggers the gun's Lua animation and selects its
                // native suppressed/unsuppressed sound. It neither consumes ammo nor sends a shot.
                callback = controller.getDeclaredMethod("lambda$doShoot$1", ItemStack.class,
                        Class.forName("com.tacz.guns.client.resource.GunDisplayInstance"),
                        Class.forName("com.tacz.guns.resource.pojo.data.gun.GunData"));
                callback.setAccessible(true);
            }
            // TACZ records cooldown once per trigger, not again for each delayed burst round.
            if (firstRound) {
                var timestamp = data.getClass().getField("clientShootTimestamp");
                data.getClass().getField("clientLastShootTimestamp").setLong(data, timestamp.getLong(data));
                timestamp.setLong(data, System.currentTimeMillis());
            }
            callback.invoke(controllerConstructor.newInstance(data, player), firedGun, display, gunData);
        } catch (ReflectiveOperationException | LinkageError exception) {
            unavailable = true;
            TimeStopMod.LOGGER.error("Cannot play TACZ's native Dead Eye shot feedback", exception);
        }
    }
}
