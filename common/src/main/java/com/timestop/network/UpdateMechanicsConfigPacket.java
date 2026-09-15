package com.timestop.network;

import com.timestop.TimeStopMod;
import com.timestop.config.TimeStopConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

public class UpdateMechanicsConfigPacket implements IServerboundPacket {
    public static final ResourceLocation ID = new ResourceLocation(TimeStopMod.MOD_ID, "update_mechanics_config");

    private final boolean enableWaterWalkingInStasis;
    private final boolean allowPlayerProjectilesInStasis;

    public UpdateMechanicsConfigPacket(boolean enableWaterWalkingInStasis, boolean allowPlayerProjectilesInStasis) {
        this.enableWaterWalkingInStasis = enableWaterWalkingInStasis;
        this.allowPlayerProjectilesInStasis = allowPlayerProjectilesInStasis;
    }

    public UpdateMechanicsConfigPacket(FriendlyByteBuf buf) {
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
    public void handle(ServerPlayer player) {
        if (player == null || (!player.hasPermissions(2) && !player.server.isSingleplayerOwner(player.getGameProfile()))) {
            if (player != null) {
                player.displayClientMessage(Component.literal("Operator (Level 2) permission required to modify mechanics!").withStyle(ChatFormatting.RED), true);
            }
            return;
        }

        TimeStopConfig.COMMON.enableWaterWalkingInStasis.set(this.enableWaterWalkingInStasis);
        TimeStopConfig.COMMON.allowPlayerProjectilesInStasis.set(this.allowPlayerProjectilesInStasis);
        TimeStopConfig.save();

        ModMessages.sendToClients(new SyncMechanicsConfigPacket(this.enableWaterWalkingInStasis, this.allowPlayerProjectilesInStasis));
        player.displayClientMessage(Component.literal("[Temporal Engine] Mechanics configuration updated!").withStyle(ChatFormatting.AQUA), true);
    }
}
