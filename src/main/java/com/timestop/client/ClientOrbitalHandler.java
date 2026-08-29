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

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ClientOrbitalHandler {

    public static class ClientOrbitEntry {
        public final UUID playerUuid;
        public int index;
        public int total;

        public ClientOrbitEntry(UUID playerUuid, int index, int total) {
            this.playerUuid = playerUuid;
            this.index = index;
            this.total = total;
        }
    }

    private static final Map<Integer, ClientOrbitEntry> clientOrbitMap = new ConcurrentHashMap<>();

    public static void registerOrbit(int entityId, UUID playerUuid, int index, int total) {
        clientOrbitMap.put(entityId, new ClientOrbitEntry(playerUuid, index, total));
    }

    public static void unregisterOrbit(int entityId) {
        clientOrbitMap.remove(entityId);
    }

    public static boolean isOrbiting(int entityId) {
        return clientOrbitMap.containsKey(entityId);
    }

    public static void clear() {
        clientOrbitMap.clear();
    }

    @SubscribeEvent
    public void onLoggingOut(net.minecraftforge.client.event.ClientPlayerNetworkEvent.LoggingOut event) {
        clear();
        CapturedProjectilesOverlay.setOrbitCount(0);
        WeatherFreezeManager.reset();
        com.timestop.combat.DeadEyeManager.removeDeadEyeShader();
    }

    @SubscribeEvent
    public void onRenderTick(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.START || clientOrbitMap.isEmpty()) return;

        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) return;

        float partialTick = event.renderTickTime;
        double angularSpeed = 0.09;
        double orbitRadius = OrbitalProjectileManager.ORBIT_RADIUS;

        for (Map.Entry<Integer, ClientOrbitEntry> entry : clientOrbitMap.entrySet()) {
            int entityId = entry.getKey();
            ClientOrbitEntry orbit = entry.getValue();

            Entity entity = level.getEntity(entityId);
            if (!(entity instanceof Projectile proj) || !proj.isAlive()) {
                clientOrbitMap.remove(entityId);
                continue;
            }

            Player player = level.getPlayerByUUID(orbit.playerUuid);
            if (player == null || !player.isAlive()) {
                clientOrbitMap.remove(entityId);
                continue;
            }

            int index = orbit.index;
            int total = Math.max(1, orbit.total);

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
