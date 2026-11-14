package com.timestop.platform;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.Entity;

public interface IPlatformHelper {
    /** Live functional watch stacks only; cosmetic slots never grant effects. */
    default java.util.List<net.minecraft.world.item.ItemStack> getEquippedWatches(net.minecraft.world.entity.player.Player player) {
        return java.util.List.copyOf(getEquippedWatchSlots(player).values());
    }

    /** Stable native slot names, containing live functional watch stacks only. */
    default java.util.Map<String, net.minecraft.world.item.ItemStack> getEquippedWatchSlots(net.minecraft.world.entity.player.Player player) {
        return java.util.Map.of();
    }

    default float getPredictionStepHeight(Entity entity) { return entity.maxUpStep(); }

    String getPlatformName();
    boolean isModLoaded(String modId);
    boolean isDevelopmentEnvironment();
    CompoundTag getPersistentData(Entity entity);
    MinecraftServer getCurrentServer();
}