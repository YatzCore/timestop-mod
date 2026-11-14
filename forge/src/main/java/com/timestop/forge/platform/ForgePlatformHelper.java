package com.timestop.forge.platform;

import com.timestop.platform.IPlatformHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.server.ServerLifecycleHooks;

public class ForgePlatformHelper implements IPlatformHelper {
    @Override
    public float getPredictionStepHeight(Entity entity) {
        float base = entity.maxUpStep();
        // getAttribute() creates a cached instance. getAttributeValue() reads the existing value or supplier.
        if (entity instanceof net.minecraft.world.entity.LivingEntity living
                && living.getAttributes().hasAttribute(net.minecraftforge.common.ForgeMod.STEP_HEIGHT_ADDITION.get()))
            base += (float) living.getAttributeValue(net.minecraftforge.common.ForgeMod.STEP_HEIGHT_ADDITION.get());
        return Math.max(0, base);
    }
    @Override
    public java.util.Map<String, net.minecraft.world.item.ItemStack> getEquippedWatchSlots(net.minecraft.world.entity.player.Player player) {
        if (player == null || !isModLoaded("curios")) return java.util.Map.of();
        return CuriosWatchCompat.getEquippedWatchSlots(player);
    }

    @Override
    public String getPlatformName() {
        return "Forge";
    }

    @Override
    public boolean isModLoaded(String modId) {
        return ModList.get().isLoaded(modId);
    }

    @Override
    public boolean isDevelopmentEnvironment() {
        return !FMLEnvironment.production;
    }

    @Override
    public CompoundTag getPersistentData(Entity entity) {
        return entity.getPersistentData();
    }

    @Override
    public MinecraftServer getCurrentServer() {
        return ServerLifecycleHooks.getCurrentServer();
    }
}