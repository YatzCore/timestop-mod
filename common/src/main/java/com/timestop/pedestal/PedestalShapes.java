package com.timestop.pedestal;
import com.timestop.item.WatchTier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.shapes.*;
/** Generated: fixed interaction volume; moving overhang has no collision. */
public final class PedestalShapes {
private static final VoxelShape COPPER = Block.box(1.5999999999999996,0,1.5999999999999996,14.4,14.4,14.4);
private static final VoxelShape GILDED = Block.box(0,0,0,16,16,16);
private static final VoxelShape DIAMOND = Block.box(0,0,0,16,16,16);
private static final VoxelShape NETHERITE = Block.box(0,0,0,16,16,16);
private static final VoxelShape CREATIVE = Block.box(0,0,0,16,16,16);
public static VoxelShape forTier(WatchTier tier) { return switch(tier) {case COPPER -> COPPER; case GILDED -> GILDED; case DIAMOND -> DIAMOND; case NETHERITE -> NETHERITE; case CREATIVE -> CREATIVE;}; }
}
