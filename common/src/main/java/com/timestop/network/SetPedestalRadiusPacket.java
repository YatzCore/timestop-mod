package com.timestop.network;

import com.timestop.pedestal.PedestalMenu;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/** Vanilla menu button IDs are signed bytes; radii need an integer payload. */
public record SetPedestalRadiusPacket(int containerId, int radius) implements IServerboundPacket {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("timestop", "set_pedestal_radius");
    public SetPedestalRadiusPacket(FriendlyByteBuf buffer) { this(buffer.readVarInt(), buffer.readVarInt()); }
    @Override public ResourceLocation getId() { return ID; }
    @Override public void toBytes(FriendlyByteBuf buffer) { buffer.writeVarInt(containerId); buffer.writeVarInt(radius); }
    @Override public void handle(ServerPlayer player) {
        if (player.containerMenu instanceof PedestalMenu menu && menu.containerId == containerId) menu.setRadius(player, radius);
    }
}
