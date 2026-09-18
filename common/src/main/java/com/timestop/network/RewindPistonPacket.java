package com.timestop.network;
import com.timestop.TimeStopMod;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
public record RewindPistonPacket(ResourceLocation dimension, BlockPos pos, CompoundTag state) implements IClientboundPacket {
 public static final ResourceLocation ID=new ResourceLocation(TimeStopMod.MOD_ID,"rewind_piston");
 public RewindPistonPacket(FriendlyByteBuf buf){this(buf.readResourceLocation(),buf.readBlockPos(),buf.readNbt());}
 public ResourceLocation getId(){return ID;}
 public void toBytes(FriendlyByteBuf buf){buf.writeResourceLocation(dimension);buf.writeBlockPos(pos);buf.writeNbt(state);}
 public void handleClient(){
  var level=net.minecraft.client.Minecraft.getInstance().level;
  if(level==null || state==null || !level.dimension().location().equals(dimension) || !level.hasChunkAt(pos)
    || !level.getBlockState(pos).is(net.minecraft.world.level.block.Blocks.MOVING_PISTON))return;
  var be=net.minecraft.world.level.block.entity.BlockEntity.loadStatic(pos,level.getBlockState(pos),state.copy());
  if(be instanceof net.minecraft.world.level.block.piston.PistonMovingBlockEntity)level.setBlockEntity(be);
 }
}
