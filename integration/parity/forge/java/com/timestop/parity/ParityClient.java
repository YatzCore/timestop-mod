package com.timestop.parity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
@Mod.EventBusSubscriber(modid="timestop_parity_tests",value=Dist.CLIENT)
public class ParityClient {
    private static final PredictionVisualScene SCENE=new PredictionVisualScene();
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        if(event.phase==TickEvent.Phase.END && Boolean.getBoolean("timestop.predictionVisualQa")) SCENE.tick(net.minecraft.client.Minecraft.getInstance());
    }
}
