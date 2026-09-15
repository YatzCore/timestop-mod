package com.timestop.network;

import com.timestop.TimeStopMod;
import com.timestop.config.TimeStopConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

public class UpdateSpeedConfigPacket implements IServerboundPacket {
    public static final ResourceLocation ID = new ResourceLocation(TimeStopMod.MOD_ID, "update_speed_config");

    private final double fastForward;
    private final double slowMotion;
    private final double matrix;
    private final double superhotIdle;
    private final double decelerationDrag;
    private final boolean resetDefaults;

    public UpdateSpeedConfigPacket(double fastForward, double slowMotion, double matrix, double superhotIdle, double decelerationDrag) {
        this.fastForward = fastForward;
        this.slowMotion = slowMotion;
        this.matrix = matrix;
        this.superhotIdle = superhotIdle;
        this.decelerationDrag = decelerationDrag;
        this.resetDefaults = false;
    }

    public static UpdateSpeedConfigPacket reset() {
        return new UpdateSpeedConfigPacket(true);
    }

    private UpdateSpeedConfigPacket(boolean reset) {
        this.fastForward = 5.0;
        this.slowMotion = 0.25;
        this.matrix = 0.25;
        this.superhotIdle = 0.05;
        this.decelerationDrag = 0.10;
        this.resetDefaults = reset;
    }

    public UpdateSpeedConfigPacket(FriendlyByteBuf buf) {
        this.resetDefaults = buf.readBoolean();
        this.fastForward = buf.readDouble();
        this.slowMotion = buf.readDouble();
        this.matrix = buf.readDouble();
        this.superhotIdle = buf.readDouble();
        this.decelerationDrag = buf.readDouble();
    }

    @Override
    public ResourceLocation getId() {
        return ID;
    }

    @Override
    public void toBytes(FriendlyByteBuf buf) {
        buf.writeBoolean(this.resetDefaults);
        buf.writeDouble(this.fastForward);
        buf.writeDouble(this.slowMotion);
        buf.writeDouble(this.matrix);
        buf.writeDouble(this.superhotIdle);
        buf.writeDouble(this.decelerationDrag);
    }

    @Override
    public void handle(ServerPlayer player) {
        if (player == null || (!player.hasPermissions(2) && !player.server.isSingleplayerOwner(player.getGameProfile()))) {
            if (player != null) {
                player.displayClientMessage(Component.literal("Operator (Level 2) permission required to modify temporal speeds!").withStyle(ChatFormatting.RED), true);
            }
            return;
        }

        if (this.resetDefaults) {
            TimeStopConfig.resetSpeedsToDefaults();
            TimeStopConfig.save();
            ModMessages.sendToClients(SyncSpeedConfigPacket.current());
            player.displayClientMessage(Component.literal("[Temporal Engine] Speeds restored to default calibration!").withStyle(ChatFormatting.GREEN), true);
        } else {
            TimeStopConfig.COMMON.fastForwardRate.set(TimeStopConfig.clampFastForward(this.fastForward));
            TimeStopConfig.COMMON.slowMotionRate.set(TimeStopConfig.clampSlowMotion(this.slowMotion));
            TimeStopConfig.COMMON.matrixRate.set(TimeStopConfig.clampMatrix(this.matrix));
            TimeStopConfig.COMMON.superhotIdleRate.set(TimeStopConfig.clampSuperhotIdle(this.superhotIdle));
            TimeStopConfig.COMMON.decelerationDrag.set(TimeStopConfig.clampDecelerationDrag(this.decelerationDrag));
            TimeStopConfig.save();
            ModMessages.sendToClients(SyncSpeedConfigPacket.current());
            player.displayClientMessage(Component.literal("[Temporal Engine] Speeds recalibrated successfully!").withStyle(ChatFormatting.AQUA), true);
        }
    }
}
