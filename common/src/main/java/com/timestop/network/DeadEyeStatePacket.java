package com.timestop.network;

import com.timestop.TimeStopMod;
import com.timestop.combat.DeadEyeManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

public class DeadEyeStatePacket implements IServerboundPacket {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(TimeStopMod.MOD_ID, "dead_eye_state");
    private final boolean active;

    public DeadEyeStatePacket(boolean active) {
        this.active = active;
    }

    public DeadEyeStatePacket(FriendlyByteBuf buf) {
        this.active = buf.readBoolean();
    }

    @Override
    public ResourceLocation getId() {
        return ID;
    }

    @Override
    public void toBytes(FriendlyByteBuf buf) {
        buf.writeBoolean(this.active);
    }

    @Override
    public void handle(ServerPlayer player) {
        if (player != null) {
            DeadEyeManager.handleStateChange(player, this.active);
        }
    }
}
