package com.timestop.forge.test;
@net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid="timestop_tacz_tests",value=net.minecraftforge.api.distmarker.Dist.CLIENT)
public class NativeTaczClientRegistration {
    @net.minecraftforge.eventbus.api.SubscribeEvent
    public static void tick(net.minecraftforge.event.TickEvent.ClientTickEvent event){if(event.phase==net.minecraftforge.event.TickEvent.Phase.END)com.timestop.test.NativeTaczClientChecks.tick();}
}
