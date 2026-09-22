package com.timestop.network;

import com.timestop.TimeStopMod;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.piston.PistonMovingBlockEntity;

public record RewindPistonPacket(ResourceLocation dimension, BlockPos pos, CompoundTag state) implements IClientboundPacket {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(TimeStopMod.MOD_ID, "rewind_piston");

    public RewindPistonPacket(FriendlyByteBuf buf) {
        this(buf.readResourceLocation(), buf.readBlockPos(), buf.readNbt());
    }

    @Override
    public ResourceLocation getId() {
        return ID;
    }

    @Override
    public void toBytes(FriendlyByteBuf buf) {
        buf.writeResourceLocation(dimension);
        buf.writeBlockPos(pos);
        buf.writeNbt(state);
    }

    @Override
    public void handleClient() {
        var level = Minecraft.getInstance().level;
        if (level == null || state == null || !level.dimension().location().equals(dimension) || !level.hasChunkAt(pos)
                || !level.getBlockState(pos).is(Blocks.MOVING_PISTON)) return;
        var be = BlockEntity.loadStatic(pos, level.getBlockState(pos), state.copy(), level.registryAccess());
        if (be instanceof PistonMovingBlockEntity) {
            level.setBlockEntity(be);
        }
    }
}
