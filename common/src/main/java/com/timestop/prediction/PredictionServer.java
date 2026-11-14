package com.timestop.prediction;

import com.timestop.core.*;
import com.timestop.network.ModMessages;
import com.timestop.network.PredictionSnapshotPacket;
import java.util.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.Vec3;

/** Authoritative seeds sent solely to the player who activated prediction. */
public final class PredictionServer {
    private static final Map<Entity, Vec3> LIVING_STEPS = new WeakHashMap<>();
    private static final Map<Entity, Vec3> PLAYER_POSITIONS = new WeakHashMap<>();
    private static final Map<UUID, Long> LAST_SEND = new HashMap<>();
    private static final Set<UUID> VIEWERS = new HashSet<>();
    private static MinecraftServer currentServer;
    private PredictionServer() {}

    public static boolean ownsPrediction(Player player) {
        if (player == null || !player.isAlive() || player.isSpectator()) return false;
        if (TimeStopManager.isGlobalTimeStopActive()) {
            return TimeStopManager.getCurrentMode() == TimeMode.TIME_PREDICTION
                    && player.getUUID().equals(TimeStopManager.getInitiatorUuid());
        }
        TemporalBubble bubble = TemporalBubbleManager.getPlayerBubble(player.getUUID());
        return bubble != null && bubble.getMode() == TimeMode.TIME_PREDICTION
                && bubble.getDimension().equals(player.level().dimension());
    }
    public static void recordStep(Entity entity) {
        if (!VIEWERS.isEmpty() && entity instanceof LivingEntity) {
            Vec3 previous=entity instanceof Player?PLAYER_POSITIONS.put(entity,entity.position()):new Vec3(entity.xo,entity.yo,entity.zo);
            if(previous!=null) LIVING_STEPS.put(entity,entity.position().subtract(previous));
        }
    }
    public static double timeRate(Entity entity) {
        if (TimeStopManager.isGlobalTimeStopActive()) {
            if (TimeStopManager.getCurrentMode() == TimeMode.TIME_PREDICTION) return PredictionTime.rate(entity);
            if (entity instanceof Player && TimeStopManager.isEntityExempt(entity)) return 1;
            if (TimeStopManager.getCurrentMode() == TimeMode.TIME_STOP && !TimeStopManager.isEntityExempt(entity)) return 0;
            return 50.0 / TimeStopManager.getServerTickMs();
        }
        TemporalBubble bubble = TemporalBubbleManager.getDominantBubble(entity.level().dimension(),
                entity.getX(), entity.getY() + entity.getBbHeight() * .5, entity.getZ());
        return bubble == null ? 1 : bubble.getTimeDilationFactor(entity);
    }
    public static PredictionMotion capture(Entity entity) {
        boolean free = TimeStopManager.isEntityExempt(entity);
        float phase = entity.level() instanceof PredictionTickClock clock
                ? clock.timestop$predictionPhase(entity) : 0;
        return PredictionPhysics.capture(entity, LIVING_STEPS.get(entity), free, phase, timeRate(entity));
    }
    public static void tick(MinecraftServer server) {
        if (currentServer != server) { clear(); currentServer = server; }
        long now = net.minecraft.Util.getMillis();
        Set<UUID> active = new HashSet<>();
        for (ServerPlayer owner : server.getPlayerList().getPlayers()) {
            UUID id = owner.getUUID();
            if (!ownsPrediction(owner)) {
                if (VIEWERS.contains(id)) ModMessages.sendToPlayer(PredictionSnapshotPacket.empty(owner), owner);
                continue;
            }
            active.add(id);
            if (now - LAST_SEND.getOrDefault(id, 0L) < 100) continue;
            LAST_SEND.put(id, now);
            ModMessages.sendToPlayer(snapshot(owner), owner);
        }
        VIEWERS.retainAll(active); VIEWERS.addAll(active);
        LAST_SEND.keySet().retainAll(active);
        if (active.isEmpty()) {LIVING_STEPS.clear();PLAYER_POSITIONS.clear();}
    }
    public static PredictionSnapshotPacket snapshot(ServerPlayer owner) {
        List<Entity> candidates = owner.serverLevel().getEntities(owner, owner.getBoundingBox().inflate(256),
                e -> eligible(owner,e));
        candidates.sort(Comparator.comparingDouble(e -> priority(owner,e)));
        List<PredictionMotion> motions = new ArrayList<>(); Map<Integer,Entity> entities = new HashMap<>();
        int projectiles=0,living=0;
        for (Entity e : candidates) {
            if ((e instanceof Projectile || e instanceof net.minecraft.world.entity.item.PrimedTnt) && ++projectiles>32 || e instanceof LivingEntity && ++living>16) continue;
            var motion=capture(e);
            if (motion.finite()) {motions.add(motion);entities.put(e.getId(),e);}
        }
        motions.add(capture(owner));entities.put(owner.getId(),owner);
        Map<Integer,PredictionEngine.Path> targets=new HashMap<>();
        List<PredictionEngine.Path> forecasts=new ArrayList<>();
        for(var motion:motions) if(motion.living()) {
            Entity e=entities.get(motion.entityId());
            var path=PredictionEngine.forecast(owner.level(),motion,p -> timeRateAt(e,p),Map.of());
            targets.put(motion.entityId(),path);forecasts.add(path.compact());
        }
        int count=0;
        for(var motion:motions) if(!motion.living() && count++<8) {
            Entity e=entities.get(motion.entityId());
            forecasts.add(PredictionEngine.forecast(owner.level(),motion,p -> timeRateAt(e,p),targets).compact());
        }
        // Prepared shots have stable negative IDs and never create a live projectile.
        List<PredictionMotion> prepared = new ArrayList<>();
        for (var e : candidates) if (e instanceof net.minecraft.world.entity.Mob mob && prepared.size() < 3
                && mob.distanceToSqr(owner) <= 48 * 48) {
            var motion = PredictionPreparedShots.capture(mob);
            if (motion == null || !motion.finite()) continue;
            prepared.add(motion);
            forecasts.add(PredictionEngine.forecast(owner.level(), motion, p -> {
                if (TimeStopManager.isGlobalTimeStopActive()) return motion.timeRate();
                var b = TemporalBubbleManager.getDominantBubble(owner.level().dimension(), p.add(0,.25,0));
                return b == null ? 1 : b.getTimeDilationFactor(mob);
            }, targets).compact());
        }
        motions.addAll(prepared);
        var bubble=TemporalBubbleManager.getPlayerBubble(owner.getUUID());
        UUID field=bubble==null?owner.getUUID():bubble.getId();
        return new PredictionSnapshotPacket(field,owner.level().dimension().location(),750,motions,
                TaczPredictionCompat.heldProfile(owner),forecasts,PredictionTeleports.previews(owner),
                PredictionOutcomes.events(owner.level(),forecasts,targets,owner.getId(),owner.position()));
    }
    public static double timeRateAt(Entity entity, Vec3 position) {
        if(TimeStopManager.isGlobalTimeStopActive()) return timeRate(entity);
        var bubble=TemporalBubbleManager.getDominantBubble(entity.level().dimension(),position.add(0,entity.getBbHeight()*.5,0));
        return bubble==null?1:bubble.getTimeDilationFactor(entity);
    }
    public static boolean eligible(ServerPlayer owner, Entity entity) {
        if (!entity.isAlive() || entity.isSpectator() || entity.isInvisibleTo(owner)) return false;
        if(entity instanceof Projectile p) {
            return entity.distanceToSqr(owner)<=256*256
                    && com.timestop.combat.ProjectileCombatHelper.isActiveInFlight(p)
                    && (p.getOwner()==owner || PredictionEngine.loadedSegment(owner.level(),owner.getEyePosition(),entity.getEyePosition()) && owner.hasLineOfSight(entity));
        }
        return (entity instanceof LivingEntity || entity instanceof net.minecraft.world.entity.item.PrimedTnt) && entity.distanceToSqr(owner)<=256*256
                && PredictionEngine.loadedSegment(owner.level(),owner.getEyePosition(),entity.getEyePosition()) && owner.hasLineOfSight(entity);
    }
    private static double priority(ServerPlayer owner, Entity entity) {
        Vec3 delta=entity.position().subtract(owner.getEyePosition());
        double distance=delta.lengthSqr(), focus=distance<1e-8?1:owner.getLookAngle().dot(delta.normalize());
        if(entity instanceof net.minecraft.world.entity.item.PrimedTnt) return distance-50000;
        if(entity instanceof net.minecraft.world.entity.monster.Creeper c && (c.isIgnited() || c.getSwellDir()>0)) return distance-40000;
        if(entity instanceof Projectile p) {
            double approach=entity.getDeltaMovement().normalize().dot(delta.normalize().reverse());
            return distance-focus*400-Math.max(0,approach)*2500-(p.getOwner()==owner?100000:0);
        }
        // Keep targets along the shot direction even when nearer mobs surround the owner.
        return distance*(.1+4*(1-Math.max(0,focus)*Math.max(0,focus)))+(focus<0?10000:0);
    }
    public static void clear() {
        LIVING_STEPS.clear(); PLAYER_POSITIONS.clear(); LAST_SEND.clear(); VIEWERS.clear(); PredictionTeleports.clear(); currentServer = null;
    }
}
