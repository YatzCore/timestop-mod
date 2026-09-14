package com.timestop.network;

import com.timestop.TimeStopMod;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

public record DeadEyeGunFeedbackPacket(ItemStack gun, boolean firstRound) implements IClientboundPacket {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(TimeStopMod.MOD_ID, "dead_eye_gun_feedback");

    public DeadEyeGunFeedbackPacket(ItemStack gun, boolean firstRound) {
        this.gun = gun != null ? gun.copy() : ItemStack.EMPTY;
        this.firstRound = firstRound;
    }

    public DeadEyeGunFeedbackPacket(FriendlyByteBuf buf) {
        this(ItemStack.EMPTY, buf.readBoolean());
    }

    @Override
    public ResourceLocation getId() { return ID; }

    @Override
    public void toBytes(FriendlyByteBuf buf) {
        buf.writeBoolean(firstRound);
    }

    @Override
    public void handleClient() {
        var player = net.minecraft.client.Minecraft.getInstance().player;
        if (player != null) {
            com.timestop.client.TaczDeadEyeFeedback.play(!gun.isEmpty() ? gun : player.getMainHandItem(), firstRound);
        }
    }
}
