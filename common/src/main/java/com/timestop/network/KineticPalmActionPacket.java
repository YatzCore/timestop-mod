package com.timestop.network;

import com.timestop.TimeStopMod;
import com.timestop.combat.KineticPalmManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

public class KineticPalmActionPacket implements IServerboundPacket {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(TimeStopMod.MOD_ID, "kinetic_palm_action");

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

    @Override
    public ResourceLocation getId() {
        return ID;
    }

    @Override
    public void toBytes(FriendlyByteBuf buf) {
        buf.writeEnum(this.action);
        buf.writeDouble(this.lookVector.x);
        buf.writeDouble(this.lookVector.y);
        buf.writeDouble(this.lookVector.z);
    }

    @Override
    public void handle(ServerPlayer player) {
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
    }
}
