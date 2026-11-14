package com.timestop.network;

import com.timestop.TimeStopMod;
import com.timestop.item.AbstractWatchItem;
import com.timestop.item.rune.RuneType;
import com.timestop.item.rune.TemporalRuneItem;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import com.timestop.item.WatchTarget;
import net.minecraft.world.item.ItemStack;

public class SocketRunePacket implements IServerboundPacket {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(TimeStopMod.MOD_ID, "socket_rune");
    private final WatchTarget target;

    public SocketRunePacket(InteractionHand hand) {
        this(WatchTarget.inHand(hand));
    }

    public SocketRunePacket(WatchTarget target) {
        this.target = target;
    }

    public SocketRunePacket(FriendlyByteBuf buf) {
        this.target = WatchTarget.read(buf);
    }

    @Override
    public ResourceLocation getId() {
        return ID;
    }

    @Override
    public void toBytes(FriendlyByteBuf buf) {
        this.target.write(buf);
    }

    @Override
    public void handle(ServerPlayer player) {
        if (player != null) {
            ItemStack watchStack = this.target.resolve(player);
            int slot = -1;
            RuneType type = RuneType.BLANK;

            for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
                ItemStack s = player.getInventory().getItem(i);
                if (s.getItem() instanceof TemporalRuneItem runeItem && runeItem.getType() != RuneType.BLANK) {
                    slot = i;
                    type = runeItem.getType();
                    break;
                }
            }

            com.timestop.item.RuneSocketTransactions.apply(player, watchStack, slot, type);
            player.inventoryMenu.broadcastChanges();
            player.containerMenu.broadcastChanges();
            ModMessages.sendToPlayer(new SyncRuneSocketPacket(this.target, AbstractWatchItem.getSocketedRune(watchStack)), player);
        }
    }
}
