package com.timestop.network;

import com.timestop.core.TimeMode;
import com.timestop.core.TimeStopManager;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.network.CustomPayloadEvent;


public class SlapProjectilePacket {
    private final int entityId;
    private final Vec3 lookDirection;

    public SlapProjectilePacket(int entityId, Vec3 lookDirection) {
        this.entityId = entityId;
        this.lookDirection = lookDirection;
    }

    public SlapProjectilePacket(FriendlyByteBuf buf) {
        this.entityId = buf.readVarInt();
        this.lookDirection = new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble());
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeVarInt(this.entityId);
        buf.writeDouble(this.lookDirection.x);
        buf.writeDouble(this.lookDirection.y);
        buf.writeDouble(this.lookDirection.z);
    }

    public void handle(CustomPayloadEvent.Context context) {       context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null) return;

            Entity entity = player.serverLevel().getEntity(this.entityId);
            if (entity instanceof Projectile projectile
                    && com.timestop.combat.ProjectileInteraction.redirect(player, projectile)) {
                player.swing(InteractionHand.MAIN_HAND, true);
            }
        });
        context.setPacketHandled(true);
    }
}
