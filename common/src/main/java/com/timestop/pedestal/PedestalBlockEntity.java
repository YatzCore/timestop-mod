package com.timestop.pedestal;

import com.timestop.core.TimeMode;
import com.timestop.item.AbstractWatchItem;
import com.timestop.item.WatchTier;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import java.util.UUID;

public class PedestalBlockEntity extends BlockEntity implements MenuProvider {
    public static final TimeMode[] MODES = {TimeMode.SLOW_MOTION, TimeMode.FAST_FORWARD, TimeMode.DECELERATION_FIELD, TimeMode.TIME_STOP};
    public final SimpleContainer inventory = new SimpleContainer(1) {
        @Override public int getMaxStackSize() { return 1; }
        @Override public void setChanged() {
            super.setChanged();
            if (!loading) {
                if (getWatch().getItem() != previousWatch) {
                    previousWatch = getWatch().getItem(); radius = maxRadius(); mode = TimeMode.SLOW_MOTION;
                }
                changed();
                if (getWatch().isEmpty()) {
                    if (previousWatch != null && level != null && !level.isClientSide) {
                        level.playSound(null, worldPosition.getX() + 0.5, worldPosition.getY() + 0.5, worldPosition.getZ() + 0.5,
                                com.timestop.sound.ModSounds.PEDESTAL_INSERT.get(), net.minecraft.sounds.SoundSource.BLOCKS, 0.8F, 0.85F);
                    }
                    PedestalManager.stop(PedestalBlockEntity.this);
                }
            }
        }
    };
    private boolean loading;
    private net.minecraft.world.item.Item previousWatch;
    private UUID owner;
    private String ownerName = "";
    private TimeMode mode = TimeMode.SLOW_MOTION;
    private int radius = 1;
    private boolean disarmed;
    private UUID fieldId = UUID.randomUUID();
    public PedestalBlockEntity(BlockPos pos, BlockState state) { super(ModPedestals.ENTITY.get(), pos, state); }
    public ItemStack getWatch() { return inventory.getItem(0); }
    public WatchTier pedestalTier() { return ((PedestalBlock)getBlockState().getBlock()).getTier(); }
    public WatchTier watchTier() { return getWatch().getItem() instanceof AbstractWatchItem w ? w.getTier() : WatchTier.COPPER; }
    public boolean accepts(ItemStack stack) { return stack.getItem() instanceof AbstractWatchItem w && w.getTier().getTierLevel() <= pedestalTier().getTierLevel(); }
    public boolean supports(TimeMode selected) {
        return !getWatch().isEmpty() && accepts(getWatch()) && java.util.Arrays.asList(MODES).contains(selected) && watchTier().getUnlockedModes().contains(selected);
    }
    public int maxRadius() { return getWatch().isEmpty() ? 1 : Math.max(1, Math.min(32767, (int)watchTier().getBubbleRadius())); }
    public int getRadius() { return Math.max(1, Math.min(radius, maxRadius())); }
    public TimeMode getMode() { return supports(mode) ? mode : TimeMode.SLOW_MOTION; }
    public void configure(TimeMode selected, int radius) {
        if (!supports(selected)) return;
        this.mode = selected; this.radius = Math.max(1, Math.min(radius, maxRadius())); changed();
    }
    public UUID getOwner() { return owner; }
    public String getOwnerName() { return ownerName; }
    public UUID getFieldId() { return fieldId; }
    public boolean isDisarmed() { return disarmed; }
    public void setDisarmed(boolean value) { if (disarmed != value) { disarmed = value; changed(); } }
    public void setOwner(Player player) { owner = player.getUUID(); ownerName = player.getGameProfile().getName(); changed(); }
    public void ensureOwner(Player player) { if (owner == null) setOwner(player); }
    public boolean isPowered() { return level != null && level.hasNeighborSignal(worldPosition); }
    public boolean isActive() { return getBlockState().getValue(PedestalBlock.ACTIVE); }
    public void setActive(boolean active) {
        if (level != null && !isRemoved() && isActive() != active && level.getBlockState(worldPosition).getBlock() instanceof PedestalBlock)
            level.setBlock(worldPosition, getBlockState().setValue(PedestalBlock.ACTIVE, active), 2);
    }
    public void changed() {
        setChanged();
        if (level != null && !level.isClientSide) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 2);
    }
    @Override public void setLevel(Level level) { super.setLevel(level); if (!level.isClientSide) PedestalManager.track(this); }
    @Override public void clearRemoved() { super.clearRemoved(); if (level != null && !level.isClientSide) PedestalManager.track(this); }
    @Override public void setRemoved() { PedestalManager.untrack(this); super.setRemoved(); }
    @Override public Component getDisplayName() { return getBlockState().getBlock().getName(); }
    @Override public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) { return new PedestalMenu(id, inventory, this); }
    @Override protected void saveAdditional(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (!getWatch().isEmpty()) tag.put("Watch", getWatch().save(registries));
        if (owner != null) tag.putUUID("Owner", owner);
        tag.putString("OwnerName", ownerName); tag.putUUID("FieldId", fieldId);
        tag.putString("Mode", mode.name()); tag.putInt("Radius", radius); tag.putBoolean("Disarmed", disarmed);
    }
    @Override public void loadAdditional(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries); loading = true;
        inventory.setItem(0, ItemStack.parseOptional(registries, tag.getCompound("Watch"))); previousWatch = getWatch().getItem();
        owner = tag.hasUUID("Owner") ? tag.getUUID("Owner") : null; ownerName = tag.getString("OwnerName");
        fieldId = tag.hasUUID("FieldId") ? tag.getUUID("FieldId") : UUID.randomUUID();
        try { mode = TimeMode.valueOf(tag.getString("Mode")); } catch (IllegalArgumentException e) { mode = TimeMode.SLOW_MOTION; }
        radius = Math.max(1, tag.getInt("Radius")); disarmed = tag.getBoolean("Disarmed"); loading = false;
        if (level != null && !level.isClientSide) { PedestalManager.track(this); changed(); }
    }
    @Override public CompoundTag getUpdateTag(net.minecraft.core.HolderLookup.Provider registries) { return saveWithoutMetadata(registries); }
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }
}
