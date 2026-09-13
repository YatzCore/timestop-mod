package com.timestop.network;

import com.timestop.combat.KineticPalmManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.network.CustomPayloadEvent;


public class KineticPalmActionPacket {

    public enum Action {
        START_GUARD,
        STOP_GUARD_DROP,
        REPULSE
    }

    private final Action action;
    private final Vec3 lookVector;

    public KineticPalmActionPacket(Action action, Vec3 lookVector) {
        this.action = action;
        this.lookVector = lookVector;
    }

    public KineticPalmActionPacket(FriendlyByteBuf buf) {
        this.action = buf.readEnum(Action.class);
        this.lookVector = new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble());
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeEnum(this.action);
        buf.writeDouble(this.lookVector.x);
        buf.writeDouble(this.lookVector.y);
        buf.writeDouble(this.lookVector.z);
    }

    public void handle(CustomPayloadEvent.Context context) {       context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player != null && player.isAlive()) {
                switch (this.action) {
                    case START_GUARD:
                        KineticPalmManager.setGuarding(player, true);
                        break;
                    case STOP_GUARD_DROP:
                        KineticPalmManager.setGuarding(player, false);
                        KineticPalmManager.dischargeDrop(player);
                        break;
                    case REPULSE:
                        KineticPalmManager.dischargeRepulse(player, this.lookVector);
                        KineticPalmManager.setGuarding(player, false);
                        break;
                }
            }
        });
        context.setPacketHandled(true);
    }
}
