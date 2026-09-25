package com.timestop.pedestal;

import com.timestop.item.AbstractWatchItem;
import com.timestop.item.WatchTier;
import net.minecraft.core.BlockPos;
import net.minecraft.world.*;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.*;
import javax.annotation.Nullable;

public class PedestalBlock extends BaseEntityBlock {
    public static final BooleanProperty ACTIVE = BooleanProperty.create("active");
    private final WatchTier tier;
    public PedestalBlock(WatchTier tier) {
        super(Properties.of().strength(3.5F, 9F).sound(SoundType.COPPER).noOcclusion()
                .requiresCorrectToolForDrops().pushReaction(PushReaction.BLOCK));
        this.tier = tier;
        registerDefaultState(stateDefinition.any().setValue(ACTIVE, false));
    }
    public WatchTier getTier() { return tier; }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(ACTIVE); }
    @Override public RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new PedestalBlockEntity(pos, state); }
    @Override public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) { return PedestalShapes.forTier(tier); }
    @Override public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        if (placer instanceof Player player && level.getBlockEntity(pos) instanceof PedestalBlockEntity pedestal) pedestal.setOwner(player);
    }
    @Override public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (level.getBlockEntity(pos) instanceof PedestalBlockEntity pedestal) {
            if (!level.isClientSide) {
                ItemStack held = player.getItemInHand(hand);
                if (pedestal.getWatch().isEmpty() && held.getItem() instanceof AbstractWatchItem) {
                    if (pedestal.accepts(held)) {
                        pedestal.ensureOwner(player);
                        pedestal.inventory.setItem(0, held.split(1));
                        level.playSound(null, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, com.timestop.sound.ModSounds.PEDESTAL_INSERT.get(), net.minecraft.sounds.SoundSource.BLOCKS, 1.0F, 1.0F);
                    } else {
                        player.displayClientMessage(net.minecraft.network.chat.Component.translatable("pedestal.timestop.tier_rejected"), true);
                        level.playSound(null, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, net.minecraft.sounds.SoundEvents.DISPENSER_FAIL, net.minecraft.sounds.SoundSource.BLOCKS, 1.0F, 1.2F);
                        pedestal.ensureOwner(player); player.openMenu(pedestal);
                    }
                } else { pedestal.ensureOwner(player); player.openMenu(pedestal); }
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        return InteractionResult.PASS;
    }
    @Override public void onRemove(BlockState state, Level level, BlockPos pos, BlockState replacement, boolean moving) {
        if (!state.is(replacement.getBlock())) {
            if (level.getBlockEntity(pos) instanceof PedestalBlockEntity pedestal) {
                PedestalManager.untrack(pedestal);
                if (!level.isClientSide && !com.timestop.core.rewind.RewindExecutor.isApplyingPlan()
                        && !com.timestop.core.rewind.TickRecorder.getInstance().getTimelineBuffer().isRewinding()
                        && !com.timestop.core.rewind.LocalRewind.contains(level.dimension(), pos)) Containers.dropContents(level, pos, pedestal.inventory);
            }
            super.onRemove(state, level, pos, replacement, moving);
        }
    }
}
