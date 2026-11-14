package com.timestop.parity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
@EventBusSubscriber(modid="timestop_parity_tests",value=Dist.CLIENT)
public class ParityClient {
    private static final PredictionVisualScene SCENE=new PredictionVisualScene();
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if(Boolean.getBoolean("timestop.predictionVisualQa")) SCENE.tick(net.minecraft.client.Minecraft.getInstance());
    }
}
