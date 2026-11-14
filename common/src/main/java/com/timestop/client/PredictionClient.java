package com.timestop.client;

import com.timestop.core.*;
import com.timestop.network.PredictionSnapshotPacket;
import com.timestop.prediction.*;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.*;
import net.minecraft.world.phys.Vec3;

public final class PredictionClient {
    public record DisplayPath(PredictionEngine.Path path, boolean threat, boolean aimed) {
        public boolean prepared() { return PredictionPreparedShots.prepared(path.motion()); }
    }
    private static PredictionSnapshotPacket snapshot;
    private static long receivedAt, updatedAt;
    private static Vec3 lastVelocity = Vec3.ZERO, currentVelocity = Vec3.ZERO;
    private static int velocityTick = -1;
    private static List<DisplayPath> paths = List.of();
    public record DisplayOutcome(PredictionOutcomes.Event event,float alpha) {}
    private static final Map<Integer,DisplayOutcome> visibleOutcomes=new LinkedHashMap<>();
    private static List<PredictionOutcomes.Event> aimOutcomes=List.of();
    private static long activatedAt, fadeAt, aimUpdatedAt;
    private PredictionClient() {}
    public static void receive(PredictionSnapshotPacket packet) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || !packet.dimension().equals(mc.level.dimension().location())) return;
        if (packet.motions().isEmpty()) { clear(); return; }
        if (snapshot != null && !snapshot.field().equals(packet.field())) paths = List.of();
        if(snapshot==null || !snapshot.field().equals(packet.field())) activatedAt=net.minecraft.Util.getMillis();
        snapshot = packet; receivedAt = net.minecraft.Util.getMillis(); updatedAt = 0;
    }
    public static boolean active() {
        Minecraft mc = Minecraft.getInstance();
        if (snapshot == null || mc.player == null || mc.level == null || !mc.player.isAlive()
                || !snapshot.dimension().equals(mc.level.dimension().location())
                || net.minecraft.Util.getMillis() - receivedAt > snapshot.validityMs()) return false;
        if (ClientTimeStopManager.isGlobalTimeStopActive()) {
            return ClientTimeStopManager.getCurrentMode() == TimeMode.TIME_PREDICTION
                    && snapshot.field().equals(mc.player.getUUID());
        }
        return ClientBubbleManager.getActiveBubbles().stream().anyMatch(b -> b.mode == TimeMode.TIME_PREDICTION
                && b.bubbleId.equals(snapshot.field()) && mc.player.getUUID().equals(b.ownerUuid));
    }
    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (!active()) { paths = List.of(); aimOutcomes=List.of(); visibleOutcomes.clear(); return; }
        if (mc.isPaused()) return;
        long now = net.minecraft.Util.getMillis();
        if (now - updatedAt < 50) return;
        List<PredictionEngine.Path> candidates = new ArrayList<>(snapshot.forecasts());
        Set<Integer> hitTargets=new HashSet<>();
        for(var path:candidates) if(path.hitEntity()>=0) hitTargets.add(path.hitEntity());
        candidates.removeIf(path -> {
            var motion=path.motion(); Entity e=mc.level.getEntity(motion.entityId());
            if (PredictionPreparedShots.prepared(motion)) {
                if (!(mc.level.getEntity(motion.ownerId()) instanceof net.minecraft.world.entity.Mob shooter)
                        || !shooter.isAlive() || shooter.isInvisibleTo(mc.player)
                        || !shooter.isUsingItem() && !CrossbowItem.isCharged(shooter.getMainHandItem())) return true;
            }
            if(motion.entityId()==mc.player.getId() || path.points().size()<2) return true;
            if(e!=null && (!e.isAlive() || e.isInvisibleTo(mc.player))) return true;
            if(e instanceof net.minecraft.world.entity.projectile.Projectile p
                    && !com.timestop.combat.ProjectileCombatHelper.isActiveInFlight(p)) return true;
            if(motion.living()) {
                if(e==null) return true;
                if(hitTargets.contains(motion.entityId())) return false;
                return path.end().position().distanceToSqr(motion.position())<.0025
                        || motion.position().distanceToSqr(mc.player.position())>48*48
                        || mc.player.getLookAngle().dot(motion.position().subtract(mc.player.getEyePosition()).normalize())<0;
            }
            return false; // A server flight remains drawable past the client's entity tracking distance.
        });
        candidates.sort(Comparator.comparingDouble(p -> priority(p.motion())-(hitTargets.contains(p.motion().entityId())?100000:0)
                -(p.hitEntity()==mc.player.getId()?50000:0)-(PredictionPreparedShots.prepared(p.motion())?5000:0)));
        List<DisplayPath> result=new ArrayList<>(); int living=0,projectiles=0;
        for(var path:candidates) {
            if(path.motion().living()?++living>3:++projectiles>8) continue;
            result.add(new DisplayPath(path,path.hitEntity()==mc.player.getId(),false));
        }
        paths=List.copyOf(result);updatedAt=now;
    }
    /** Render-time origin/rotation prevents tick-space aim jitter while the camera moves. */
    public static List<DisplayPath> framePaths(float partialTick) {
        if(!active()) return List.of();
        tick();
        var result=new ArrayList<DisplayPath>();
        for(var display:paths) {
            var path=display.path();
            var entity=Minecraft.getInstance().level.getEntity(path.motion().entityId());
            if(!path.motion().living() && entity!=null) {
                path=path.trimFrom(entity.getPosition(partialTick));
            }
            result.add(new DisplayPath(path,display.threat(),false));
        }
        var mc=Minecraft.getInstance();
        if(velocityTick!=mc.player.tickCount) {
            lastVelocity=velocityTick<0?mc.player.getDeltaMovement():currentVelocity; currentVelocity=mc.player.getDeltaMovement(); velocityTick=mc.player.tickCount;
        }
        var aim=aimedMotion(partialTick);
        if(aim!=null && aim.finite()) {
            Map<Integer,PredictionEngine.Path> targets=new HashMap<>();
            double age=Math.max(0,(net.minecraft.Util.getMillis()-receivedAt)/1000.0);
            for(var path:snapshot.forecasts()) if(path.motion().living()) {
                var points=new ArrayList<PredictionEngine.Point>();
                points.add(new PredictionEngine.Point(path.positionAt(age),0));
                for(var point:path.points()) if(point.seconds()>age)
                    points.add(new PredictionEngine.Point(point.position(),point.seconds()-age));
                var motion=path.motion().at(points.get(0).position(),path.motion().velocity());
                targets.put(motion.entityId(),new PredictionEngine.Path(motion,points,path.impact(),path.hitEntity(),path.normal()));
            }
            var path=PredictionEngine.forecast(mc.level,aim,p -> timeRate(aim,p),targets);
            result.add(new DisplayPath(path,false,true));
            long now=net.minecraft.Util.getMillis();
            // Exposure/effect eligibility is bounded and cached, never recalculated for each rendered frame.
            if(now-aimUpdatedAt>=50 || !aimOutcomes.isEmpty() && aimOutcomes.get(0).kind()!=aim.effects().kind()) {
                var outcome=PredictionOutcomes.event(mc.level,path,targets);
                aimOutcomes=outcome==null?List.of():List.of(outcome);aimUpdatedAt=now;
            }
            if(!path.impact()) aimOutcomes=List.of();
            else if(!aimOutcomes.isEmpty()) {
                var cached=aimOutcomes.get(0);
                var affected=cached.affected().stream().map(a -> new PredictionOutcomes.Affected(a.entityId(),
                        targets.containsKey(a.entityId())?targets.get(a.entityId()).positionAt(path.end().seconds()):a.position(),a.strength())).toList();
                // Keep the area attached to the interpolated held aim; cache only costly eligibility.
                aimOutcomes=List.of(new PredictionOutcomes.Event(-1,cached.kind(),path.end().position(),cached.radius(),
                        path.end().seconds(),cached.color(),cached.conditional(),affected));
            }
        } else {
            aimOutcomes=List.of();aimUpdatedAt=0;
        }
        return result;
    }
    public static Vec3 targetPosition(int id,double seconds,boolean aimed) {
        double age=Math.max(0,(net.minecraft.Util.getMillis()-receivedAt)/1000.0);
        for(var path:snapshot.forecasts()) if(path.motion().entityId()==id && path.motion().living())
            return path.positionAt(seconds+(aimed?age:0));
        var entity=Minecraft.getInstance().level.getEntity(id);
        return entity==null?null:entity.position();
    }
    public static List<PredictionTeleports.Preview> teleports() {
        return active()?snapshot.teleports():List.of();
    }
    public static float opacity() {
        if(!active()) return 0;
        long now=net.minecraft.Util.getMillis();
        return (float)Math.min(1,(now-activatedAt)/180.0)*(float)Math.min(1,Math.max(0,(snapshot.validityMs()-(now-receivedAt))/150.0));
    }
    public static List<DisplayOutcome> frameOutcomes() {
        if(!active()) return List.of();
        var mc=Minecraft.getInstance();long now=net.minecraft.Util.getMillis();double age=(now-receivedAt)/1000.0;
        var events=new ArrayList<PredictionOutcomes.Event>(aimOutcomes);
        for(var e:snapshot.outcomes()) {
            var source=mc.level.getEntity(e.sourceId());
            if(source!=null && !source.isAlive()) continue;
            if(e.kind()==PredictionEffects.Kind.CREEPER && (!(source instanceof net.minecraft.world.entity.monster.Creeper c) || !c.isIgnited() && c.getSwellDir()<=0)) continue;
            if(e.seconds()<age) continue;
            events.add(e);
        }
        events.sort(Comparator.comparingDouble(e -> PredictionOutcomes.priority(e,mc.player.getId(),mc.player.position())
                -(visibleOutcomes.containsKey(e.sourceId())?20:0)));
        var chosen=new LinkedHashMap<Integer,PredictionOutcomes.Event>();
        for(var e:events) {chosen.put(e.sourceId(),e);if(chosen.size()==2) break;}
        float delta=(float)Math.min(1,(now-fadeAt)/150.0);fadeAt=now;
        visibleOutcomes.replaceAll((id,o) -> new DisplayOutcome(o.event(),Math.max(0,o.alpha()-delta)));
        for(var e:chosen.values()) {
            var old=visibleOutcomes.get(e.sourceId());
            // Stable IDs preserve fades across the ten-Hz snapshot refresh.
            visibleOutcomes.put(e.sourceId(),new DisplayOutcome(e,Math.min(1,(old==null?0:old.alpha())+2*delta)));
        }
        visibleOutcomes.entrySet().removeIf(e -> e.getValue().alpha()<=0);
        // Fade replacement areas within the same strict two-area budget.
        var result=new ArrayList<DisplayOutcome>();
        for(var id:chosen.keySet()) {var o=visibleOutcomes.get(id);result.add(new DisplayOutcome(o.event(),o.alpha()*opacity()));}
        for(var o:visibleOutcomes.values()) if(result.size()<2 && !chosen.containsKey(o.event().sourceId())) result.add(new DisplayOutcome(o.event(),o.alpha()*opacity()));
        return result;
    }
    public static List<DisplayPath> paths() { return active() ? framePaths(Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false)) : List.of(); }
    private static double priority(PredictionMotion motion) {
        var player = Minecraft.getInstance().player;
        Vec3 delta = motion.position().subtract(player.getEyePosition());
        double focus = player.getLookAngle().dot(delta.normalize());
        double approach = motion.velocity().normalize().dot(delta.normalize().reverse());
        return delta.lengthSqr() - (motion.ownerId()==player.getId()?100000:0) - focus * 400 - (!motion.living() && motion.ownerId() != player.getId() ? Math.max(0, approach) * 2500 : 0);
    }
    public static double timeRate(PredictionMotion motion, Vec3 position) {
        Minecraft mc = Minecraft.getInstance();
        if (ClientTimeStopManager.isGlobalTimeStopActive()) return motion.timeRate();
        var bubble = ClientBubbleManager.getDominantBubble(position.add(0, motion.height() * .5, 0));
        if (bubble == null) return 1;
        Entity entity = mc.level.getEntity(motion.entityId());
        if (entity != null) return bubble.getTimeDilationFactor(entity);
        if (motion.free()) return 1;
        return switch (bubble.mode) {
            case TIME_PREDICTION -> com.timestop.config.TimeStopConfig.COMMON.predictionRate.get();
            case TIME_STOP, REWIND -> 0;
            case SLOW_MOTION, MATRIX -> .25;
            case SUPERHOT -> .05 + ClientBubbleManager.getSuperhotActivity(bubble.bubbleId) * .95;
            case FAST_FORWARD -> 5;
            default -> 1;
        };
    }
    private static PredictionMotion aimedMotion(float partialTick) {
        var player = Minecraft.getInstance().player;
        ItemStack stack = player.getMainHandItem();
        if (stack.isEmpty()) stack = player.getOffhandItem();
        if (player.isUsingItem()) stack = player.getUseItem();
        double speed = 0, gravity = .03, drag = .99;
        var kind = PredictionMotion.Kind.THROWABLE;
        Vec3 direction = player.getViewVector(partialTick);
        if (stack.getItem() instanceof BowItem) {
            if(player.isUsingItem()) {
                double charge=Math.max(0,player.getTicksUsingItem()-1+partialTick)/20.0;
                speed=Math.min(1,(charge*charge+2*charge)/3)*3;
            } else speed=3;
            gravity = .05; kind = PredictionMotion.Kind.ARROW;
        } else if (stack.getItem() instanceof CrossbowItem) {
            if (!CrossbowItem.isCharged(stack) || stack.getOrDefault(net.minecraft.core.component.DataComponents.CHARGED_PROJECTILES, net.minecraft.world.item.component.ChargedProjectiles.EMPTY).contains(Items.FIREWORK_ROCKET)) return null;
            speed = 3.15; gravity = .05; kind = PredictionMotion.Kind.ARROW;
        } else if (stack.getItem() instanceof TridentItem) {
            if (!player.isUsingItem() || player.getTicksUsingItem() < 10
                    || net.minecraft.world.item.enchantment.EnchantmentHelper.has(stack, net.minecraft.world.item.enchantment.EnchantmentEffectComponents.TRIDENT_SPIN_ATTACK_STRENGTH)) return null;
            speed = 2.5; gravity = .05; kind = PredictionMotion.Kind.ARROW;
        } else if (stack.is(Items.SNOWBALL) || stack.is(Items.EGG) || stack.is(Items.ENDER_PEARL)) speed = 1.5;
        else if (stack.getItem() instanceof ThrowablePotionItem || stack.is(Items.EXPERIENCE_BOTTLE)) {
            speed = stack.is(Items.EXPERIENCE_BOTTLE) ? .7 : .5;
            gravity = stack.is(Items.EXPERIENCE_BOTTLE) ? .07 : .05;
            double xRot=net.minecraft.util.Mth.lerp(partialTick,player.xRotO,player.getXRot());
            double yRot=net.minecraft.util.Mth.lerp(partialTick,player.yRotO,player.getYRot());
            double pitch = Math.toRadians(xRot - 20), yaw = Math.toRadians(yRot);
            direction = new Vec3(-Math.sin(yaw) * Math.cos(Math.toRadians(xRot)), -Math.sin(pitch),
                    Math.cos(yaw) * Math.cos(Math.toRadians(xRot))).normalize();
        } else if (snapshot.gun() != null && com.timestop.combat.TaczDeadEyeCompat.isGun(stack)) {
            var profile = snapshot.gun();
            speed = profile.velocity().length(); gravity = profile.gravity(); drag = profile.drag(); kind = profile.kind();
        }
        if (speed < .1) return null;
        boolean free = ClientTimeStopManager.arePlayerProjectilesFlowing();
        double rate = ClientTimeStopManager.isGlobalTimeStopActive()
                ? free ? 1 : com.timestop.config.TimeStopConfig.COMMON.predictionRate.get() : 1;
        Vec3 inherited = kind == PredictionMotion.Kind.NATIVE ? Vec3.ZERO : lastVelocity.lerp(currentVelocity,partialTick).multiply(1, player.onGround() ? 0 : 1, 1);
        return new PredictionMotion(-1, player.getId(), kind, player.getPosition(partialTick).add(0,player.getEyeHeight()-.1,0),
                direction.scale(speed).add(inherited), Vec3.ZERO, gravity, drag,
                stack.getItem() instanceof TridentItem ? .99 : kind == PredictionMotion.Kind.NATIVE || kind == PredictionMotion.Kind.ARROW ? .6 : .8,
                kind==PredictionMotion.Kind.THROWABLE?.25F:.1F, kind==PredictionMotion.Kind.THROWABLE?.25F:.1F,
                false, false, free, 0, rate).withEffects(PredictionEffects.item(stack));
    }
    public static void clear() { snapshot = null; paths = List.of(); receivedAt = 0; updatedAt = 0; lastVelocity = currentVelocity = Vec3.ZERO; velocityTick = -1;
        visibleOutcomes.clear();aimOutcomes=List.of();activatedAt=fadeAt=aimUpdatedAt=0; }
}
