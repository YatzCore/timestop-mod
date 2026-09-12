package com.timestop.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;
import java.util.function.Supplier;

/** One confirmed native round; cosmetic only and never asks TACZ to fire again. */
public record DeadEyeGunFeedbackPacket(ItemStack gun, boolean firstRound) {
    public DeadEyeGunFeedbackPacket { gun = gun.copy(); }
    public DeadEyeGunFeedbackPacket(FriendlyByteBuf buf) { this(buf.readItem(), buf.readBoolean()); }
    public void toBytes(FriendlyByteBuf buf) { buf.writeItem(gun); buf.writeBoolean(firstRound); }
    public void handle(Supplier<NetworkEvent.Context> supplier) {
        var context = supplier.get();
        context.enqueueWork(() -> com.timestop.client.TaczDeadEyeFeedback.play(gun, firstRound));
        context.setPacketHandled(true);
    }
}
