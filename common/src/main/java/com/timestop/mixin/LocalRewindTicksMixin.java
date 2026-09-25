package com.timestop.mixin;
import com.timestop.core.rewind.LocalRewind;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.material.Fluid;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(ServerLevel.class)
public abstract class LocalRewindTicksMixin {
    @Inject(method="tickBlock",at=@At("HEAD"),cancellable=true)
    private void deferBlock(BlockPos pos, Block block, CallbackInfo ci) {
        var level=(ServerLevel)(Object)this;
        if(LocalRewind.contains(level.dimension(),pos) || com.timestop.core.TemporalBubbleManager.isPositionInStasis(level.dimension(), net.minecraft.world.phys.Vec3.atCenterOf(pos))) { level.scheduleTick(pos,block,1); ci.cancel(); }
    }
    @Inject(method="tickFluid",at=@At("HEAD"),cancellable=true)
    private void deferFluid(BlockPos pos, Fluid fluid, CallbackInfo ci) {
        var level=(ServerLevel)(Object)this;
        if(LocalRewind.contains(level.dimension(),pos) || com.timestop.core.TemporalBubbleManager.isPositionInStasis(level.dimension(), net.minecraft.world.phys.Vec3.atCenterOf(pos))) { level.scheduleTick(pos,fluid,1); ci.cancel(); }
    }
    @Redirect(method="tickChunk",at=@At(value="INVOKE",target="Lnet/minecraft/world/level/block/state/BlockState;randomTick(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/BlockPos;Lnet/minecraft/util/RandomSource;)V"))
    private void randomBlock(net.minecraft.world.level.block.state.BlockState state,ServerLevel level,BlockPos pos,net.minecraft.util.RandomSource random) {
        if(!LocalRewind.contains(level.dimension(),pos)) com.timestop.pedestal.PedestalWorldTime.randomBlock(state,level,pos,random);
    }
    @Redirect(method="tickChunk",at=@At(value="INVOKE",target="Lnet/minecraft/world/level/material/FluidState;randomTick(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/util/RandomSource;)V"))
    private void randomFluid(net.minecraft.world.level.material.FluidState state,net.minecraft.world.level.Level level,BlockPos pos,net.minecraft.util.RandomSource random) {
        if(!LocalRewind.contains(level.dimension(),pos) && !com.timestop.core.TemporalBubbleManager.isPositionInStasis(level.dimension(),net.minecraft.world.phys.Vec3.atCenterOf(pos)))state.randomTick(level,pos,random);
    }
}
