package com.timestop.prediction;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.Vec3;

/** A prediction-only windup reveals a selected, validated destination, never a random guess. */
public final class PredictionTeleports {
    public record Preview(int entityId, Vec3 origin, Vec3 destination, int ticksLeft) {}
    private record Reservation(Vec3 destination, long due) {}
    private static final Map<EnderMan, Reservation> PENDING = new WeakHashMap<>();
    public static final int WINDUP_TICKS = 12;
    private PredictionTeleports() {}
    public static boolean pending(EnderMan mob) { return PENDING.containsKey(mob); }
    public static boolean watched(EnderMan mob) {
        return !mob.level().isClientSide && mob.level().players().stream().anyMatch(p ->
                PredictionServer.ownsPrediction(p) && p.distanceToSqr(mob) <= 48*48 && p.hasLineOfSight(mob));
    }
    public static Vec3 destination(EnderMan mob, double x, double y, double z) {
        var level = mob.level();
        var pos = BlockPos.containing(x,y,z);
        if (!level.hasChunkAt(pos) || !level.getWorldBorder().isWithinBounds(pos)) return null;
        while (pos.getY() > level.getMinBuildHeight() && !level.getBlockState(pos.below()).blocksMotion()) {
            pos = pos.below(); y--;
        }
        if (pos.getY() <= level.getMinBuildHeight()) return null;
        Vec3 target = new Vec3(x,y,z);
        var box = mob.getBoundingBox().move(target.subtract(mob.position()));
        return level.noCollision(mob, box) && !level.containsAnyLiquid(box) ? target : null;
    }
    public static boolean reserve(EnderMan mob, double x, double y, double z) {
        Vec3 target = destination(mob,x,y,z);
        if (target == null) return false;
        PENDING.put(mob, new Reservation(target, mob.level().getServer().getTickCount() + WINDUP_TICKS));
        return true;
    }
    public static List<Preview> previews(ServerPlayer player) {
        List<Preview> result = new ArrayList<>();
        for (var entry : PENDING.entrySet()) {
            var mob = entry.getKey(); var reservation = entry.getValue();
            if (mob.isAlive() && mob.level() == player.level() && player.distanceToSqr(mob) <= 48*48
                    && player.hasLineOfSight(mob)) {
                result.add(new Preview(mob.getId(),mob.position(),reservation.destination(),
                        (int)Math.max(0,reservation.due()-mob.level().getServer().getTickCount())));
                if (result.size() == 4) break;
            }
        }
        return result;
    }
    public static void tick(MinecraftServer server) {
        var entries = new ArrayList<>(PENDING.entrySet());
        for (var entry : entries) {
            var mob = entry.getKey(); var reservation = entry.getValue();
            if (mob.level().getServer() != server || !mob.isAlive()) { PENDING.remove(mob); continue; }
            // Complete an already selected vanilla action immediately if its prediction viewer leaves.
            if (server.getTickCount() < reservation.due() && watched(mob)) continue;
            PENDING.remove(mob);
            Vec3 from = mob.position(), to = reservation.destination();
            Vec3 valid=destination(mob,to.x,to.y,to.z);
            if(valid==null || valid.distanceToSqr(to)>1e-8) continue;
            if (mob.randomTeleport(to.x,to.y,to.z,true)) {
                mob.level().gameEvent(GameEvent.TELEPORT,from,GameEvent.Context.of(mob));
                if (!mob.isSilent()) {
                    mob.level().playSound(null,from.x,from.y,from.z,SoundEvents.ENDERMAN_TELEPORT,mob.getSoundSource(),1,1);
                    mob.playSound(SoundEvents.ENDERMAN_TELEPORT,1,1);
                }
            }
        }
    }
    public static void clear() { PENDING.clear(); }
}
