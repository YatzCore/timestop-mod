package com.timestop.network;

import com.timestop.TimeStopMod;
import com.timestop.config.TimeStopConfig;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

public class SyncMechanicsConfigPacket implements IClientboundPacket {
    public static final ResourceLocation ID = new ResourceLocation(TimeStopMod.MOD_ID, "sync_mechanics_config");

    private final boolean enableWaterWalkingInStasis;
    private final boolean allowPlayerProjectilesInStasis;

    public SyncMechanicsConfigPacket(boolean enableWaterWalkingInStasis, boolean allowPlayerProjectilesInStasis) {
        this.enableWaterWalkingInStasis = enableWaterWalkingInStasis;
        this.allowPlayerProjectilesInStasis = allowPlayerProjectilesInStasis;
    }

    public static SyncMechanicsConfigPacket current() {
        return new SyncMechanicsConfigPacket(
                TimeStopConfig.COMMON.enableWaterWalkingInStasis.get(),
                TimeStopConfig.COMMON.allowPlayerProjectilesInStasis.get()
        );
    }

    public SyncMechanicsConfigPacket(FriendlyByteBuf buf) {
        this.enableWaterWalkingInStasis = buf.readBoolean();
        this.allowPlayerProjectilesInStasis = buf.readBoolean();
    }

    @Override
    public ResourceLocation getId() {
        return ID;
    }

    @Override
    public void toBytes(FriendlyByteBuf buf) {
        buf.writeBoolean(this.enableWaterWalkingInStasis);
        buf.writeBoolean(this.allowPlayerProjectilesInStasis);
    }

    @Override
    public void handleClient() {
        TimeStopConfig.COMMON.enableWaterWalkingInStasis.set(this.enableWaterWalkingInStasis);
        TimeStopConfig.COMMON.allowPlayerProjectilesInStasis.set(this.allowPlayerProjectilesInStasis);

        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.screen instanceof com.timestop.client.gui.TimeStopSettingsScreen settingsScreen) {
            settingsScreen.onMechanicsConfigSynced();
        }
    }
}
