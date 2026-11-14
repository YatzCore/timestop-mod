package com.timestop.mixin;

import com.timestop.core.rewind.RewindPlayerInteraction;
import com.timestop.core.rewind.TickRecorder;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerPlayerGameMode;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerPlayerGameMode.class)
public abstract class ServerPlayerGameModeMixin implements RewindPlayerInteraction {
    @Shadow protected ServerLevel level;
    @Shadow @Final protected ServerPlayer player;
    @Shadow private boolean isDestroyingBlock;
    @Shadow private boolean hasDelayedDestroy;
    @Shadow private BlockPos destroyPos;
    @Shadow private BlockPos delayedDestroyPos;
    @Shadow private int lastSentState;

    @Inject(
            method = "useItemOn(Lnet/minecraft/server/level/ServerPlayer;Lnet/minecraft/world/level/Level;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/InteractionHand;Lnet/minecraft/world/phys/BlockHitResult;)Lnet/minecraft/world/InteractionResult;",
            at = @At("HEAD")
    )
    private void timestop$observeOnUseItemOn(
            ServerPlayer player,
            Level level,
            ItemStack stack,
            InteractionHand hand,
            BlockHitResult hitResult,
            CallbackInfoReturnable<InteractionResult> cir
    ) {
        if (!level.isClientSide) {
            BlockPos pos = hitResult.getBlockPos();
            BlockEntity be = level.getBlockEntity(pos);
            if (be != null) {
                TickRecorder.getInstance().observeBlockEntity(be);
            }
        }
    }

    @Override
    public void timestop$resetBreaking() {
        if (isDestroyingBlock) level.destroyBlockProgress(player.getId(), destroyPos, -1);
        if (hasDelayedDestroy) level.destroyBlockProgress(player.getId(), delayedDestroyPos, -1);
        isDestroyingBlock = false;
        hasDelayedDestroy = false;
        lastSentState = -1;
    }
}
