package com.timestop.network;

import com.timestop.TimeStopMod;
import com.timestop.config.TimeStopConfig;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

public class SyncSpeedConfigPacket implements IClientboundPacket {
    public static final ResourceLocation ID = new ResourceLocation(TimeStopMod.MOD_ID, "sync_speed_config");

    private final double fastForward;
    private final double slowMotion;
    private final double matrix;
    private final double superhotIdle;
    private final double decelerationDrag;
    private final double rewindSpeed;

    public SyncSpeedConfigPacket(double fastForward, double slowMotion, double matrix, double superhotIdle, double decelerationDrag, double rewindSpeed) {
        this.fastForward = fastForward;
        this.slowMotion = slowMotion;
        this.matrix = matrix;
        this.superhotIdle = superhotIdle;
        this.decelerationDrag = decelerationDrag;
        this.rewindSpeed = rewindSpeed;
    }

    public static SyncSpeedConfigPacket current() {
        return new SyncSpeedConfigPacket(
                TimeStopConfig.COMMON.fastForwardRate.get(),
                TimeStopConfig.COMMON.slowMotionRate.get(),
                TimeStopConfig.COMMON.matrixRate.get(),
                TimeStopConfig.COMMON.superhotIdleRate.get(),
                TimeStopConfig.COMMON.decelerationDrag.get(),
                TimeStopConfig.COMMON.rewindSpeed.get()
        );
    }

    public SyncSpeedConfigPacket(FriendlyByteBuf buf) {
        this.fastForward = buf.readDouble();
        this.slowMotion = buf.readDouble();
        this.matrix = buf.readDouble();
        this.superhotIdle = buf.readDouble();
        this.decelerationDrag = buf.readDouble();
        this.rewindSpeed = buf.isReadable() ? buf.readDouble() : 1.0;
    }

    @Override
    public ResourceLocation getId() {
        return ID;
    }

    @Override
    public void toBytes(FriendlyByteBuf buf) {
        buf.writeDouble(this.fastForward);
        buf.writeDouble(this.slowMotion);
        buf.writeDouble(this.matrix);
        buf.writeDouble(this.superhotIdle);
        buf.writeDouble(this.decelerationDrag);
        buf.writeDouble(this.rewindSpeed);
    }

    @Override
    public void handleClient() {
        TimeStopConfig.COMMON.fastForwardRate.set(this.fastForward);
        TimeStopConfig.COMMON.slowMotionRate.set(this.slowMotion);
        TimeStopConfig.COMMON.matrixRate.set(this.matrix);
        TimeStopConfig.COMMON.superhotIdleRate.set(this.superhotIdle);
        TimeStopConfig.COMMON.decelerationDrag.set(this.decelerationDrag);
        TimeStopConfig.COMMON.rewindSpeed.set(this.rewindSpeed);

        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.screen instanceof com.timestop.client.gui.TimeStopSettingsScreen settingsScreen) {
            settingsScreen.onSpeedConfigSynced();
        }
    }
}
