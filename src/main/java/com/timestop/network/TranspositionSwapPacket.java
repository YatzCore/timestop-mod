package com.timestop.network;

import com.timestop.combat.TranspositionManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class TranspositionSwapPacket {

    private final boolean isSneaking;

    public TranspositionSwapPacket(boolean isSneaking) {
        this.isSneaking = isSneaking;
    }

    public TranspositionSwapPacket(FriendlyByteBuf buf) {
        this.isSneaking = buf.readBoolean();
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeBoolean(this.isSneaking);
    }

    public boolean handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context ctx = supplier.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player != null && player.isAlive()) {
                TranspositionManager.executeSwap(player, this.isSneaking);
            }
        });
        return true;
    }
}
