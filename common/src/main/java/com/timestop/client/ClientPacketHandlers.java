package com.timestop.client;

import com.timestop.client.gui.TimeModeSelectionScreen;
import com.timestop.item.AbstractWatchItem;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import java.util.UUID;

/** Client handlers are isolated so registering packets is safe on a dedicated server. */
public final class ClientPacketHandlers {
    private ClientPacketHandlers() {}

    public static void syncProjectileOwner(int entityId, UUID uuid, int ownerId) {
        var level = Minecraft.getInstance().level;
        if (level == null || !(level.getEntity(entityId) instanceof net.minecraft.world.entity.projectile.Projectile projectile)
                || !uuid.equals(projectile.getUUID())) return;
        var owner = level.getEntity(ownerId);
        if (ownerId == -1 || owner != null) projectile.setOwner(owner);
    }

    public static void syncRune(com.timestop.item.WatchTarget target, ItemStack rune) {
        var minecraft = Minecraft.getInstance();
        if (minecraft.player == null) return;
        // Vanilla container synchronization owns inventory; an acknowledgement must not modify a newly selected watch.
        if (minecraft.screen instanceof TimeModeSelectionScreen screen && screen.matchesTarget(target)) screen.onServerSync(rune);
    }

    public static void syncKineticCapture(int entityId, UUID owner, boolean captured, net.minecraft.world.phys.Vec3 position) {
        var level = Minecraft.getInstance().level;
        var entity = level != null ? level.getEntity(entityId) : null;
        if (entity == null) return;
        if (captured) {
            if (!com.timestop.platform.EntityDataHelper.getPersistentData(entity).getBoolean("KineticPalmCaptured")) {
                entity.setPos(position);
                entity.setOldPosAndRot();
                com.timestop.platform.EntityDataHelper.getPersistentData(entity).putInt("NeoWakeAge", 0);
            }
            com.timestop.platform.EntityDataHelper.getPersistentData(entity).putDouble("NeoTargetX", position.x);
            com.timestop.platform.EntityDataHelper.getPersistentData(entity).putDouble("NeoTargetY", position.y);
            com.timestop.platform.EntityDataHelper.getPersistentData(entity).putDouble("NeoTargetZ", position.z);
            com.timestop.platform.EntityDataHelper.getPersistentData(entity).putBoolean("KineticPalmCaptured", true);
            com.timestop.platform.EntityDataHelper.getPersistentData(entity).putUUID("KineticPalmOwner", owner);
            entity.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
        } else {
            entity.setPos(position);
            com.timestop.platform.EntityDataHelper.getPersistentData(entity).remove("KineticPalmCaptured");
            com.timestop.platform.EntityDataHelper.getPersistentData(entity).remove("KineticPalmOwner");
        }
    }
}
