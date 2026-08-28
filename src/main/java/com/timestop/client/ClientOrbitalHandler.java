package com.timestop.client;

import com.timestop.combat.OrbitalProjectileManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.UUID;

public class ClientOrbitalHandler {

    @SubscribeEvent
    public void onRenderTick(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;

        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) return;

        float partialTick = event.renderTickTime;
        double angularSpeed = 0.09;
        double orbitRadius = OrbitalProjectileManager.ORBIT_RADIUS;

        for (Entity entity : level.entitiesForRendering()) {
            if (!(entity instanceof Projectile proj)) continue;
            if (!proj.getPersistentData().contains("OrbitedPlayerUuid")) continue;

            UUID playerUuid = proj.getPersistentData().getUUID("OrbitedPlayerUuid");
            Player player = level.getPlayerByUUID(playerUuid);
            if (player == null || !player.isAlive()) continue;

            int index = proj.getPersistentData().getInt("OrbitIndex");
            int total = Math.max(1, proj.getPersistentData().getInt("OrbitTotal"));

            // Interpolate player render coordinates for 100% shake-free lockstep motion
            double px = Mth.lerp(partialTick, player.xOld, player.getX());
            double py = Mth.lerp(partialTick, player.yOld, player.getY());
            double pz = Mth.lerp(partialTick, player.zOld, player.getZ());

            // Smooth continuous sub-tick rotation
            double time = player.tickCount + partialTick;
            double theta = time * angularSpeed + (index * 2.0 * Math.PI / total);
            double bob = Math.sin(time * 0.15 + index) * 0.08;

            double x = px + orbitRadius * Math.cos(theta);
            double z = pz + orbitRadius * Math.sin(theta);
            double y = py + 1.15 + bob;

            double vx = -Math.sin(theta) * 0.22;
            double vz = Math.cos(theta) * 0.22;
            float yRot = (float) (Mth.atan2(vx, vz) * (180.0D / Math.PI));

            proj.setPos(x, y, z);
            proj.setYRot(yRot);
            proj.setXRot(0.0F);
            proj.yRotO = yRot;
            proj.xRotO = 0.0F;

            // Update internal old positions to prevent vanilla interpolation jitter
            proj.xo = x;
            proj.yo = y;
            proj.zo = z;
            proj.xOld = x;
            proj.yOld = y;
            proj.zOld = z;
        }
    }
}
