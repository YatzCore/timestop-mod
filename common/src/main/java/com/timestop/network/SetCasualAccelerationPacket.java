package com.timestop.network;

import com.timestop.TimeStopMod;
import com.timestop.core.PersonalTime;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/** A player's own Casual mode preference (client config), sent on change, join and respawn. */
public class SetCasualAccelerationPacket implements IServerboundPacket {
    public static final ResourceLocation ID = new ResourceLocation(TimeStopMod.MOD_ID, "set_casual_acceleration");
    private final boolean casual;
    public SetCasualAccelerationPacket(boolean casual) { this.casual = casual; }
    public SetCasualAccelerationPacket(FriendlyByteBuf buf) { this(buf.readBoolean()); }
    @Override public ResourceLocation getId() { return ID; }
    @Override public void toBytes(FriendlyByteBuf buf) { buf.writeBoolean(casual); }
    @Override public void handle(ServerPlayer player) {
        if (player != null) PersonalTime.setCasual(player.getUUID(), casual);
    }
}
