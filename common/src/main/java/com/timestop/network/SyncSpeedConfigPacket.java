package com.timestop.network;

import com.timestop.TimeStopMod;
import com.timestop.config.TimeStopConfig;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

public class SyncSpeedConfigPacket implements IClientboundPacket {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(TimeStopMod.MOD_ID, "sync_speed_config");

    private final double fastForward;
    private final double slowMotion;
    private final double matrix;
    private final double superhotIdle;
    private final double decelerationDrag;
    private final double rewindSpeed;
    private double prediction = 0.25;
    private boolean waterWalking = true;

    public SyncSpeedConfigPacket(double fastForward, double slowMotion, double matrix, double superhotIdle, double decelerationDrag, double rewindSpeed) {
        this.fastForward = fastForward;
        this.slowMotion = slowMotion;
        this.matrix = matrix;
        this.superhotIdle = superhotIdle;
        this.decelerationDrag = decelerationDrag;
        this.rewindSpeed = rewindSpeed;
        this.prediction = TimeStopConfig.COMMON.predictionRate.get();
        this.waterWalking = TimeStopConfig.COMMON.enableWaterWalkingInStasis.get();
    }

    public SyncSpeedConfigPacket(double fastForward, double slowMotion, double matrix, double superhotIdle, double decelerationDrag) {
        this(fastForward,slowMotion,matrix,superhotIdle,decelerationDrag,TimeStopConfig.COMMON.rewindSpeed.get());
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
        this.prediction = buf.isReadable() ? TimeStopConfig.clampPrediction(buf.readDouble()) : 0.25;
        this.waterWalking = buf.isReadable() ? buf.readBoolean() : true;
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
        buf.writeDouble(this.prediction);
        buf.writeBoolean(this.waterWalking);
    }

    @Override
    public void handleClient() {
        // On someone else's server these values are borrowed: keep our own for the config file and for after we leave.
        if (!net.minecraft.client.Minecraft.getInstance().hasSingleplayerServer()) TimeStopConfig.beginRemoteSync();
        TimeStopConfig.COMMON.fastForwardRate.set(this.fastForward);
        TimeStopConfig.COMMON.slowMotionRate.set(this.slowMotion);
        TimeStopConfig.COMMON.matrixRate.set(this.matrix);
        TimeStopConfig.COMMON.superhotIdleRate.set(this.superhotIdle);
        TimeStopConfig.COMMON.decelerationDrag.set(this.decelerationDrag);
        TimeStopConfig.COMMON.rewindSpeed.set(this.rewindSpeed);
        TimeStopConfig.COMMON.predictionRate.set(this.prediction);
        TimeStopConfig.COMMON.enableWaterWalkingInStasis.set(this.waterWalking);

        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.screen instanceof com.timestop.client.gui.TimeStopSettingsScreen settingsScreen) {
            settingsScreen.onSpeedConfigSynced();
        }
    }
}
