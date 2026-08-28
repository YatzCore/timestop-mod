package com.timestop.network;

import com.timestop.combat.OrbitalProjectileManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class ReleaseProjectilesPacket {

    public ReleaseProjectilesPacket() {}

    public ReleaseProjectilesPacket(FriendlyByteBuf buf) {}

    public void toBytes(FriendlyByteBuf buf) {}

    public boolean handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context ctx = supplier.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player != null && player.isAlive()) {
                OrbitalProjectileManager.launchOrbitingProjectiles(player);
            }
        });
        return true;
    }
}
