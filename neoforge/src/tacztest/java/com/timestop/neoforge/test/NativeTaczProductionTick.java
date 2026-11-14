package com.timestop.neoforge.test;
@net.neoforged.fml.common.EventBusSubscriber(modid="timestop_tacz_tests")
public final class NativeTaczProductionTick {
    @net.neoforged.bus.api.SubscribeEvent
    public static void tick(net.neoforged.neoforge.event.tick.ServerTickEvent.Post event) {
        if(Boolean.getBoolean("timestop.nativeTaczPackagedTests") && net.neoforged.fml.loading.FMLLoader.isProduction())
            net.minecraft.gametest.framework.GameTestTicker.SINGLETON.tick();
    }
}
