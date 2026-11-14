package com.timestop.fabric.test;
public class NativeTaczClientRegistration implements net.fabricmc.api.ClientModInitializer {
    @Override public void onInitializeClient(){net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register(mc->com.timestop.test.NativeTaczClientChecks.tick());}
}
