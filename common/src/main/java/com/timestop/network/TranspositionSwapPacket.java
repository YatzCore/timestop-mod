package com.timestop.network;

import com.timestop.TimeStopMod;
import com.timestop.combat.TranspositionManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

public class TranspositionSwapPacket implements IServerboundPacket {
    public static final ResourceLocation ID = new ResourceLocation(TimeStopMod.MOD_ID, "transposition_swap");
    private final boolean isSneaking;

    public TranspositionSwapPacket(boolean isSneaking) {
        this.isSneaking = isSneaking;
    }

    public TranspositionSwapPacket(FriendlyByteBuf buf) {
        this.isSneaking = buf.readBoolean();
    }

    @Override
    public ResourceLocation getId() {
        return ID;
    }

    @Override
    public void toBytes(FriendlyByteBuf buf) {
        buf.writeBoolean(this.isSneaking);
    }

    @Override
    public void handle(ServerPlayer player) {
        if (player != null && player.isAlive()) {
            TranspositionManager.executeSwap(player, this.isSneaking);
        }
    }
}
