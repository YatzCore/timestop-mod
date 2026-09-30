package com.timestop.network;

import com.timestop.TimeStopMod;
import com.timestop.item.AbstractWatchItem;
import com.timestop.item.WatchTier;
import com.timestop.item.rune.RuneType;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

public class ToggleMatrixRunePacket implements IServerboundPacket {
    public static final ResourceLocation ID = new ResourceLocation(TimeStopMod.MOD_ID, "toggle_matrix_rune");
    private final InteractionHand hand;
    private final RuneType runeType;
    private final boolean enable;
    private final boolean all;

    public ToggleMatrixRunePacket(InteractionHand hand, RuneType runeType, boolean enable, boolean all) {
        this.hand = hand;
        this.runeType = runeType != null ? runeType : RuneType.BLANK;
        this.enable = enable;
        this.all = all;
    }

    public ToggleMatrixRunePacket(FriendlyByteBuf buf) {
        this.hand = buf.readEnum(InteractionHand.class);
        this.runeType = buf.readEnum(RuneType.class);
        this.enable = buf.readBoolean();
        this.all = buf.readBoolean();
    }

    @Override
    public ResourceLocation getId() {
        return ID;
    }

    @Override
    public void toBytes(FriendlyByteBuf buf) {
        buf.writeEnum(this.hand);
        buf.writeEnum(this.runeType);
        buf.writeBoolean(this.enable);
        buf.writeBoolean(this.all);
    }

    @Override
    public void handle(ServerPlayer player) {
        if (player != null) {
            ItemStack watchStack = player.getItemInHand(this.hand);
            if (watchStack.getItem() instanceof AbstractWatchItem watch && watch.getTier() == WatchTier.CREATIVE) {
                if (this.all) {
                    AbstractWatchItem.setAllMatrixRunes(watchStack, this.enable);
                } else if (this.runeType != null && this.runeType != RuneType.BLANK) {
                    AbstractWatchItem.setMatrixRune(watchStack, this.runeType, this.enable);
                }
                player.inventoryMenu.broadcastChanges();
                player.containerMenu.broadcastChanges();

                if (this.enable) {
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
