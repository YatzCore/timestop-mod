package com.timestop.fabric.test;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
public class PredictionVisualQa implements ClientModInitializer {
    public void onInitializeClient() {
        if(Boolean.getBoolean("timestop.predictionVisualQa")) {
            var scene=new com.timestop.parity.PredictionVisualScene();
            ClientTickEvents.END_CLIENT_TICK.register(scene::tick);
        }
    }
}
