package com.timestop.neoforge.test;
@net.neoforged.fml.common.EventBusSubscriber(modid="timestop_tacz_tests",value=net.neoforged.api.distmarker.Dist.CLIENT)
public class NativeTaczClientRegistration {
    @net.neoforged.bus.api.SubscribeEvent
    public static void tick(net.neoforged.neoforge.client.event.ClientTickEvent.Post event){com.timestop.test.NativeTaczClientChecks.tick();}
}
