package com.timestop.network;

import com.timestop.TimeStopMod;
import com.timestop.core.TimeStopManager;
import net.minecraft.ChatFormatting;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import javax.annotation.Nullable;

public class ToggleProjectileFlowPacket implements IServerboundPacket {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(TimeStopMod.MOD_ID, "toggle_projectile_flow");

    @Nullable
    private final TimeStopManager.ProjectileStasisMode targetMode;

    public ToggleProjectileFlowPacket() {
        this.targetMode = null;
    }

    public ToggleProjectileFlowPacket(@Nullable TimeStopManager.ProjectileStasisMode targetMode) {
        this.targetMode = targetMode;
    }

    public ToggleProjectileFlowPacket(FriendlyByteBuf buf) {
        if (buf.readBoolean()) {
            this.targetMode = buf.readEnum(TimeStopManager.ProjectileStasisMode.class);
        } else {
            this.targetMode = null;
        }
    }

    @Override
    public ResourceLocation getId() {
        return ID;
    }

    @Override
    public void toBytes(FriendlyByteBuf buf) {
        buf.writeBoolean(this.targetMode != null);
        if (this.targetMode != null) {
            buf.writeEnum(this.targetMode);
        }
    }

    @Override
    public void handle(ServerPlayer player) {
        if (player != null && player.isAlive()) {
            TimeStopManager.ProjectileStasisMode next = (this.targetMode != null)
                    ? this.targetMode
                    : (TimeStopManager.getProjectileStasisMode() == TimeStopManager.ProjectileStasisMode.FLOWING
                            ? TimeStopManager.ProjectileStasisMode.SUSPENDED
                            : TimeStopManager.ProjectileStasisMode.FLOWING);

            TimeStopManager.setProjectileStasisMode(next);

            Component msg = Component.literal("[Temporal Ballistics] ").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
                    .append(Component.literal("Projectile Stasis Mode: ").withStyle(ChatFormatting.WHITE))
                    .append(Component.literal(next == TimeStopManager.ProjectileStasisMode.FLOWING ? "FLOWING (Instant Bullets)" : "SUSPENDED (Matrix Clusters)")
                            .withStyle(next == TimeStopManager.ProjectileStasisMode.FLOWING ? ChatFormatting.GREEN : ChatFormatting.AQUA));
            player.displayClientMessage(msg, true);
        }
    }
}
