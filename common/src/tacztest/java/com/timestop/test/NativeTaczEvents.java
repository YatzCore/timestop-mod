package com.timestop.test;

import net.minecraft.world.entity.LivingEntity;
import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/** Test listeners use each installed port's native event system. */
public final class NativeTaczEvents {
    public static AutoCloseable cancel(String eventName, LivingEntity shooter) throws Exception {
        Class<?> event=Class.forName("com.tacz.guns.api.event.common."+eventName);
        var active=new AtomicBoolean(true);
        Consumer<Object> listener=value->{try {
            if(active.get() && event.getMethod("getShooter").invoke(value)==shooter)
                event.getMethod("setCanceled",boolean.class).invoke(value,true);
        }catch(ReflectiveOperationException e){throw new RuntimeException(e);}};
        if(com.timestop.platform.Services.PLATFORM.isModLoaded("fabricloader")) {
            Object callbacks=event.getField("CALLBACK").get(null);
            Class<?> callback=Class.forName(event.getName()+"$Callback");
            Object proxy=Proxy.newProxyInstance(callback.getClassLoader(),new Class<?>[]{callback},(p,m,args)->{listener.accept(args[0]);return null;});
            Class.forName("net.fabricmc.fabric.api.event.Event").getMethod("register",Object.class).invoke(callbacks,proxy);
            return ()->active.set(false);
        }
        boolean neo=com.timestop.platform.Services.PLATFORM.isModLoaded("neoforge");
        Object bus=Class.forName(neo?"net.neoforged.neoforge.common.NeoForge":"net.minecraftforge.common.MinecraftForge").getField("EVENT_BUS").get(null);
        Class<?> busApi=Class.forName(neo?"net.neoforged.bus.api.IEventBus":"net.minecraftforge.eventbus.api.IEventBus");
        if(neo)busApi.getMethod("addListener",Class.class,Consumer.class).invoke(bus,event,listener);
        else {
            Class<?> priority=Class.forName("net.minecraftforge.eventbus.api.EventPriority");
            busApi.getMethod("addListener",priority,boolean.class,Class.class,Consumer.class).invoke(bus,priority.getField("NORMAL").get(null),false,event,listener);
        }
        return ()->{active.set(false);busApi.getMethod("unregister",Object.class).invoke(bus,listener);};
    }
}
