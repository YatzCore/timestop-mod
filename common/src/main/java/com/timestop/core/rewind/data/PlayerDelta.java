package com.timestop.core.rewind.data;

import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Captures comprehensive player state for accurate temporal rollback.
 * Uses SharedInventory to deduplicate unchanged inventory contents across frames.
 */
public class PlayerDelta {
    private final UUID playerUuid;
    private final float health;
    private final int foodLevel;
    private final float saturationLevel;
    private final int totalExperience;
    private final int selectedSlot;
    private final int experienceLevel;
    private final float experienceProgress;
    private final int fireTicks;
    private final List<MobEffectInstance> activeEffects;
    private final double posX;
    private final double posY;
    private final double posZ;
    private final float yaw;
    private final float pitch;
    private final Vec3 motion;
    private final ResourceKey<Level> dimension;

    @Nullable private final SharedInventory sharedInventory;
    @Nullable private final List<ItemStack> legacyInventory;
    @Nullable private final ItemStack legacyCarried;

    public PlayerDelta(
            UUID playerUuid,
            float health,
            int foodLevel,
            float saturationLevel,
            int totalExperience,
            int selectedSlot,
            ItemStack carried,
            int experienceLevel,
            float experienceProgress,
            int fireTicks,
            List<ItemStack> inventory,
            List<MobEffectInstance> activeEffects,
            double posX,
            double posY,
            double posZ,
            float yaw,
            float pitch,
            Vec3 motion,
            ResourceKey<Level> dimension
    ) {
        this.playerUuid = playerUuid;
        this.health = health;
        this.foodLevel = foodLevel;
        this.saturationLevel = saturationLevel;
        this.totalExperience = totalExperience;
        this.selectedSlot = selectedSlot;
        this.experienceLevel = experienceLevel;
        this.experienceProgress = experienceProgress;
        this.fireTicks = fireTicks;
        this.activeEffects = activeEffects != null ? activeEffects : Collections.emptyList();
        this.posX = posX;
        this.posY = posY;
        this.posZ = posZ;
        this.yaw = yaw;
        this.pitch = pitch;
        this.motion = motion;
        this.dimension = dimension;
        this.sharedInventory = null;
        this.legacyInventory = inventory;
        this.legacyCarried = carried;
    }

    public PlayerDelta(
            UUID playerUuid,
            float health,
            int foodLevel,
            float saturationLevel,
            int totalExperience,
            int selectedSlot,
            int experienceLevel,
            float experienceProgress,
            int fireTicks,
            SharedInventory sharedInventory,
            List<MobEffectInstance> activeEffects,
            double posX,
            double posY,
            double posZ,
            float yaw,
            float pitch,
            Vec3 motion,
            ResourceKey<Level> dimension
    ) {
        this.playerUuid = playerUuid;
        this.health = health;
        this.foodLevel = foodLevel;
        this.saturationLevel = saturationLevel;
        this.totalExperience = totalExperience;
        this.selectedSlot = selectedSlot;
        this.experienceLevel = experienceLevel;
        this.experienceProgress = experienceProgress;
        this.fireTicks = fireTicks;
        this.activeEffects = activeEffects != null ? activeEffects : Collections.emptyList();
        this.posX = posX;
        this.posY = posY;
        this.posZ = posZ;
        this.yaw = yaw;
        this.pitch = pitch;
        this.motion = motion;
        this.dimension = dimension;
        this.sharedInventory = sharedInventory;
        this.legacyInventory = null;
        this.legacyCarried = null;
    }

    public static PlayerDelta fromPlayer(ServerPlayer player) {
        return fromPlayer(player, null);
    }

    public static PlayerDelta fromPlayer(ServerPlayer player, @Nullable SharedInventory cachedInventory) {
        SharedInventory inv = (cachedInventory != null && cachedInventory.matches(player))
                ? cachedInventory
                : SharedInventory.capture(player);

        List<MobEffectInstance> effects = new ArrayList<>();
        for (MobEffectInstance effect : player.getActiveEffects()) {
            effects.add(new MobEffectInstance(effect));
        }

        return new PlayerDelta(
                player.getUUID(),
                player.getHealth(),
                player.getFoodData().getFoodLevel(),
                player.getFoodData().getSaturationLevel(),
                player.totalExperience,
                player.getInventory().selected,
                player.experienceLevel,
                player.experienceProgress,
                player.getRemainingFireTicks(),
                inv,
                effects,
                player.getX(),
                player.getY(),
                player.getZ(),
                player.getYRot(),
                player.getXRot(),
                player.getDeltaMovement(),
                player.level().dimension()
        );
    }

    public UUID playerUuid() { return playerUuid; }
    public float health() { return health; }
    public int foodLevel() { return foodLevel; }
    public float saturationLevel() { return saturationLevel; }
    public int totalExperience() { return totalExperience; }
    public int selectedSlot() { return selectedSlot; }
    public int experienceLevel() { return experienceLevel; }
    public float experienceProgress() { return experienceProgress; }
    public int fireTicks() { return fireTicks; }
    public List<MobEffectInstance> activeEffects() { return activeEffects; }
    public double posX() { return posX; }
    public double posY() { return posY; }
    public double posZ() { return posZ; }
    public float yaw() { return yaw; }
    public float pitch() { return pitch; }
    public Vec3 motion() { return motion; }
    public ResourceKey<Level> dimension() { return dimension; }

    @Nullable public SharedInventory sharedInventory() { return sharedInventory; }

    public ItemStack carried() {
        return sharedInventory != null ? sharedInventory.carried() : (legacyCarried != null ? legacyCarried : ItemStack.EMPTY);
    }

    public List<ItemStack> inventory() {
        return sharedInventory != null ? sharedInventory.items() : (legacyInventory != null ? legacyInventory : Collections.emptyList());
    }

    public void restoreTo(ServerPlayer player, boolean rollbackInventory) {
        var watchPreferences = com.timestop.core.rewind.WatchPreferences.capture(player);
        // Using an item or a stale container after its inventory was replaced can swallow clicks.
        player.stopUsingItem();
        ((com.timestop.core.rewind.RewindPlayerInteraction) player.gameMode).timestop$resetBreaking();
        if (rollbackInventory && player.containerMenu != player.inventoryMenu) {
            player.containerMenu.setCarried(ItemStack.EMPTY);
            player.closeContainer();
        }
        player.getFoodData().setFoodLevel(this.foodLevel);
        player.getFoodData().setSaturation(this.saturationLevel);
        player.setExperienceLevels(this.experienceLevel);
        player.experienceProgress = this.experienceProgress;
        player.totalExperience = this.totalExperience;
        player.connection.send(new net.minecraft.network.protocol.game.ClientboundSetExperiencePacket(experienceProgress, totalExperience, experienceLevel));
        player.setRemainingFireTicks(this.fireTicks);

        player.removeAllEffects();
        for (MobEffectInstance effect : this.activeEffects) {
            player.addEffect(new MobEffectInstance(effect));
        }

        player.setHealth(Math.max(2.0F, Math.min(player.getMaxHealth(), this.health)));
        if (rollbackInventory) {
            player.getInventory().clearContent();
            List<ItemStack> inv = inventory();
            for (int i = 0; i < Math.min(inv.size(), player.getInventory().getContainerSize()); i++) {
                player.getInventory().setItem(i, com.timestop.core.rewind.WatchPreferences.restore(inv.get(i), watchPreferences));
            }
            player.getInventory().selected = Math.max(0, Math.min(8, selectedSlot));
            player.connection.send(new net.minecraft.network.protocol.game.ClientboundSetCarriedItemPacket(player.getInventory().selected));
            player.containerMenu.setCarried(com.timestop.core.rewind.WatchPreferences.restore(carried(), watchPreferences));
            com.timestop.combat.RewindRuneManager.applyRuneConsumption(player);
            player.getInventory().setChanged();
            player.inventoryMenu.broadcastFullState();
            if (player.containerMenu != player.inventoryMenu) player.containerMenu.broadcastFullState();
        }

        var targetLevel = player.server.getLevel(dimension);
        if (targetLevel != null) player.teleportTo(targetLevel, posX, posY, posZ, yaw, pitch);
        player.fallDistance = 0;
        player.setDeltaMovement(motion);
        player.connection.send(new net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket(player));
    }

    public int estimateMemoryBytes() {
        int bytes = 160 + activeEffects.size() * 24;
        if (sharedInventory == null && legacyInventory != null) {
            bytes += legacyInventory.size() * 64;
            if (legacyCarried != null && !legacyCarried.isEmpty()) {
                bytes += 64;
            }
        }
        return bytes;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PlayerDelta that)) return false;
        return Float.compare(that.health, health) == 0 &&
                foodLevel == that.foodLevel &&
                Float.compare(that.saturationLevel, saturationLevel) == 0 &&
                totalExperience == that.totalExperience &&
                selectedSlot == that.selectedSlot &&
                experienceLevel == that.experienceLevel &&
                Float.compare(that.experienceProgress, experienceProgress) == 0 &&
                fireTicks == that.fireTicks &&
                Double.compare(that.posX, posX) == 0 &&
                Double.compare(that.posY, posY) == 0 &&
                Double.compare(that.posZ, posZ) == 0 &&
                Float.compare(that.yaw, yaw) == 0 &&
                Float.compare(that.pitch, pitch) == 0 &&
                Objects.equals(playerUuid, that.playerUuid) &&
                Objects.equals(dimension, that.dimension);
    }

    @Override
    public int hashCode() {
        return Objects.hash(playerUuid, health, foodLevel, totalExperience, posX, posY, posZ, dimension);
    }
}
