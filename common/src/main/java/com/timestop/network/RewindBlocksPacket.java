package com.timestop.network;

import com.timestop.TimeStopMod;
import com.timestop.core.rewind.BlockRebuildMotion;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import java.util.List;
import java.util.ArrayList;

public record RewindBlocksPacket(ResourceLocation dimension, List<Entry> blocks) implements IClientboundPacket {
    public static final ResourceLocation ID = new ResourceLocation(TimeStopMod.MOD_ID, "rewind_blocks");
    public record Entry(BlockPos pos, BlockState state) {}
    public RewindBlocksPacket {
        blocks = List.copyOf(blocks);
        if (blocks.size() > BlockRebuildMotion.MAX_BLOCKS) throw new IllegalArgumentException("Too many rewind blocks");
    }
    public RewindBlocksPacket(FriendlyByteBuf buf) { this(buf.readResourceLocation(), readEntries(buf)); }
    private static List<Entry> readEntries(FriendlyByteBuf buf) {
        int count = buf.readVarInt();
        if (count < 0 || count > BlockRebuildMotion.MAX_BLOCKS) throw new IllegalArgumentException("Invalid rewind block count");
        List<Entry> result = new ArrayList<>(count);
        for (int i = 0; i < count; i++) result.add(new Entry(buf.readBlockPos(), Block.stateById(buf.readVarInt())));
        return result;
    }
    @Override public ResourceLocation getId() { return ID; }
    @Override public void toBytes(FriendlyByteBuf buf) {
        buf.writeResourceLocation(dimension);
        buf.writeVarInt(blocks.size());
        for (Entry entry : blocks) { buf.writeBlockPos(entry.pos()); buf.writeVarInt(Block.getId(entry.state())); }
    }
    @Override public void handleClient() { com.timestop.client.RewindBlockRenderer.begin(this); }
}
