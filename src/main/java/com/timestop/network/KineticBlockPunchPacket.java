package com.timestop.network;

import com.timestop.combat.TemporalKineticBlockManager;
import com.timestop.core.TimeMode;
import com.timestop.core.TimeStopManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class KineticBlockPunchPacket {
    private final int entityId;
    private final Vec3 lookDirection;

    public KineticBlockPunchPacket(int entityId, Vec3 lookDirection) {
        this.entityId = entityId;
        this.lookDirection = lookDirection;
    }

    public KineticBlockPunchPacket(FriendlyByteBuf buf) {
        this.entityId = buf.readVarInt();
        this.lookDirection = new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble());
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeVarInt(this.entityId);
        buf.writeDouble(this.lookDirection.x);
        buf.writeDouble(this.lookDirection.y);
        buf.writeDouble(this.lookDirection.z);
    }

    public boolean handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null) return;

            ServerLevel level = player.serverLevel();
            if (!TimeStopManager.isGlobalTimeStopped() || TimeStopManager.getCurrentMode() != TimeMode.TIME_STOP) {
                return;
            }

            if (!TimeStopManager.isEntityExempt(player)) {
                return;
            }

            Entity entity = level.getEntity(this.entityId);
            if (entity instanceof FallingBlockEntity || entity instanceof PrimedTnt) {
                double power = player.getMainHandItem().isEmpty() ? 0.22 : 0.35;
                Vec3 impulse = this.lookDirection.scale(power);
                TemporalKineticBlockManager.recordHit(entity, impulse, player);
                player.swing(InteractionHand.MAIN_HAND, true);
            }
        });
        return true;
    }
}
