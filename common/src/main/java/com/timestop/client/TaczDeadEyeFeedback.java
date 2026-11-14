package com.timestop.client;

import com.timestop.TimeStopMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;
import java.lang.reflect.Method;
import java.util.Optional;

/** Native local animation and sound APIs only; no client firing request or ammunition stage. */
public final class TaczDeadEyeFeedback {
    private static boolean unavailable;
    private static Method silenceLookup;
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
            Class<?> controller = Class.forName("com.tacz.guns.client.gameplay.LocalPlayerShoot");
            Class<?> holder = Class.forName("com.tacz.guns.client.gameplay.LocalPlayerDataHolder");
            if (silenceLookup == null) {
                silenceLookup = controller.getDeclaredMethod("useSilenceSound");
                silenceLookup.setAccessible(true);
            }
            // TACZ records cooldown once per trigger, not again for each delayed burst round.
            if (firstRound) {
                var timestamp = data.getClass().getField("clientShootTimestamp");
                data.getClass().getField("clientLastShootTimestamp").setLong(data, timestamp.getLong(data));
                timestamp.setLong(data, System.currentTimeMillis());
            }
            if (!postFireEvent(player, firedGun)) return;
            Object animation = display.getClass().getMethod("getAnimationStateMachine").invoke(display);
            if (animation != null) {
                String input = (String) Class.forName("com.tacz.guns.client.animation.statemachine.GunAnimationConstant")
                        .getField("INPUT_SHOOT").get(null);
                Class.forName("com.tacz.guns.api.client.animation.statemachine.AnimationStateMachine")
                        .getMethod("trigger", String.class).invoke(animation, input);
            }
            Class<?> displayType = Class.forName("com.tacz.guns.client.resource.GunDisplayInstance");
            Class<?> gunDataType = Class.forName("com.tacz.guns.resource.pojo.data.gun.GunData");
            Class<?> sounds = Class.forName("com.tacz.guns.client.sound.SoundPlayManager");
            String inspect = (String) Class.forName("com.tacz.guns.sound.SoundManager").getField("INSPECT_SOUND").get(null);
            sounds.getMethod("stopPlayGunSound", displayType, String.class).invoke(null, display, inspect);
            Object shootController = controller.getConstructor(holder, LocalPlayer.class).newInstance(data, player);
            boolean silent = (Boolean) silenceLookup.invoke(shootController);
            sounds.getMethod(silent ? "playSilenceSound" : "playShootSound",
                    net.minecraft.world.entity.LivingEntity.class, displayType, gunDataType)
                    .invoke(null, player, display, gunData);
        } catch (ReflectiveOperationException | LinkageError exception) {
            unavailable = true;
            TimeStopMod.LOGGER.error("Cannot play TACZ's native Dead Eye shot feedback", exception);
        }
    }

    private static boolean postFireEvent(LocalPlayer player, ItemStack gun) throws ReflectiveOperationException {
        String platform = com.timestop.platform.Services.PLATFORM.getPlatformName();
        String sideName = switch (platform) {
            case "Fabric" -> "cn.sh1rocu.tacz.api.LogicalSide";
            case "NeoForge" -> "net.neoforged.fml.LogicalSide";
            default -> "net.minecraftforge.fml.LogicalSide";
        };
        Class<?> sideType = Class.forName(sideName);
        Class<?> fireType = Class.forName("com.tacz.guns.api.event.common.GunFireEvent");
        Object event = fireType.getConstructor(net.minecraft.world.entity.LivingEntity.class, ItemStack.class, sideType)
                .newInstance(player, gun, sideType.getField("CLIENT").get(null));
        if (platform.equals("Fabric")) {
            Object callbackEvent = fireType.getField("CALLBACK").get(null);
            Object invoker = Class.forName("net.fabricmc.fabric.api.event.Event").getMethod("invoker").invoke(callbackEvent);
            Class.forName("com.tacz.guns.api.event.common.GunFireEvent$Callback")
                    .getMethod("post", fireType).invoke(invoker, event);
        } else {
            boolean neo = platform.equals("NeoForge");
            Object bus = Class.forName(neo ? "net.neoforged.neoforge.common.NeoForge" : "net.minecraftforge.common.MinecraftForge")
                    .getField("EVENT_BUS").get(null);
            Class<?> busType = Class.forName(neo ? "net.neoforged.bus.api.IEventBus" : "net.minecraftforge.eventbus.api.IEventBus");
            Class<?> eventType = Class.forName(neo ? "net.neoforged.bus.api.Event" : "net.minecraftforge.eventbus.api.Event");
            busType.getMethod("post", eventType).invoke(bus, event);
        }
        return !(Boolean) fireType.getMethod("isCanceled").invoke(event);
    }
}
