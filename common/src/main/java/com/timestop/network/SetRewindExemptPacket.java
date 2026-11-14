package com.timestop.network;

import com.timestop.TimeStopMod;
import com.timestop.core.TimeStopManager;
import com.timestop.item.AbstractWatchItem;
import com.timestop.item.WatchTier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import com.timestop.item.WatchTarget;
import net.minecraft.world.item.ItemStack;

public class SetRewindExemptPacket implements IServerboundPacket {
    public static final ResourceLocation ID = new ResourceLocation(TimeStopMod.MOD_ID, "set_rewind_exempt");
    private final WatchTarget target;
    private final boolean exempt;

    public SetRewindExemptPacket(InteractionHand hand, boolean exempt) {
        this(WatchTarget.inHand(hand), exempt);
    }

    public SetRewindExemptPacket(WatchTarget target, boolean exempt) {
        this.target = target;
        this.exempt = exempt;
    }

    public SetRewindExemptPacket(FriendlyByteBuf buf) {
        this.target = WatchTarget.read(buf);
        this.exempt = buf.readBoolean();
    }

    @Override
    public ResourceLocation getId() {
        return ID;
    }

    @Override
    public void toBytes(FriendlyByteBuf buf) {
        this.target.write(buf);
        buf.writeBoolean(this.exempt);
    }

    @Override
    public void handle(ServerPlayer player) {
        if (player != null) {
            if (!TimeStopManager.isRewindExemptAllowed()) {
                return;
            }
            ItemStack watchStack = this.target.resolve(player);
            if (watchStack.getItem() instanceof AbstractWatchItem watch && watch.getTier() == WatchTier.CREATIVE) {
                AbstractWatchItem.setRewindInitiatorExempt(watchStack, this.exempt);
                player.inventoryMenu.broadcastChanges();
                player.containerMenu.broadcastChanges();

                if (this.exempt) {
                    player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                            SoundEvents.ARMOR_EQUIP_NETHERITE, SoundSource.PLAYERS, 0.9F, 1.2F);
                } else {
                    player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                            SoundEvents.ARMOR_EQUIP_GENERIC, SoundSource.PLAYERS, 0.7F, 0.8F);
                }
            }
        }
    }
}
