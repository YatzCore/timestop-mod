package com.timestop.network;

import com.timestop.TimeStopMod;
import com.timestop.combat.OrbitalProjectileManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

public class SingleFireProjectilePacket implements IServerboundPacket {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(TimeStopMod.MOD_ID, "single_fire_projectile");

    public SingleFireProjectilePacket() {}

    public SingleFireProjectilePacket(FriendlyByteBuf buf) {}

    @Override
    public ResourceLocation getId() {
        return ID;
    }

    @Override
    public void toBytes(FriendlyByteBuf buf) {}

    @Override
    public void handle(ServerPlayer player) {
        if (player != null && player.isAlive()) {
            OrbitalProjectileManager.launchSingleProjectile(player);
        }
    }
}
