package com.timestop.prediction;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.function.ToDoubleFunction;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.*;

/** Forecasts copied numbers against loaded collision geometry; no live entity is ticked or moved. */
public final class PredictionEngine {
    public static final double HORIZON = 2.0;
    public static final double RANGE = 48.0;
    public record Point(Vec3 position, double seconds) {}
    public record Path(PredictionMotion motion, List<Point> points, boolean impact, int hitEntity, Vec3 normal) {
        public Path { points = List.copyOf(points); }
        public Path(PredictionMotion motion, List<Point> points, boolean impact, int hitEntity) {
            this(motion,points,impact,hitEntity,Vec3.ZERO);
        }
        public Point end() { return points.get(points.size() - 1); }
        public Vec3 positionAt(double seconds) {
            if (seconds <= 0) return points.get(0).position();
            int low = 0, high = points.size() - 1;
            while (low < high) {
                int mid = (low + high) >>> 1;
                if (points.get(mid).seconds() < seconds) low = mid + 1; else high = mid;
            }
            if (low == 0 || seconds >= end().seconds()) return points.get(low).position();
            Point a = points.get(low - 1), b = points.get(low);
            return a.position().lerp(b.position(), (seconds - a.seconds()) / Math.max(1e-9, b.seconds() - a.seconds()));
        }
        /** Advance the visible origin without shifting the authoritative contact or its clock. */
        public Path trimFrom(Vec3 origin) {
            if (points.size()<2) return this;
            int first=1;double nearest=Double.MAX_VALUE, time=points.get(0).seconds();
            for(int i=1;i<points.size();i++) {
                Point a=points.get(i-1), b=points.get(i);Vec3 delta=b.position().subtract(a.position());
                double fraction=Math.max(0,Math.min(1,origin.subtract(a.position()).dot(delta)/Math.max(1e-9,delta.lengthSqr())));
                double distance=origin.distanceToSqr(a.position().lerp(b.position(),fraction));
                if(distance<nearest) {
                    nearest=distance;first=i;time=a.seconds()+fraction*(b.seconds()-a.seconds());
                }
            }
            var remaining=new ArrayList<Point>();remaining.add(new Point(origin,time));
            remaining.addAll(points.subList(first,points.size()));
            return new Path(motion,remaining,impact,hitEntity,normal);
        }
        /** Preserve world anchors and endpoint while bounding the owner-only packet. */
        public Path compact() {
            if (points.size() <= 193) return this;
            var retained = new java.util.TreeSet<Integer>();
            retained.add(0); retained.add(points.size()-1);
            var pending = new java.util.PriorityQueue<Span>(java.util.Comparator.comparingDouble(Span::error).reversed());
            pending.add(span(0, points.size()-1));
            while (retained.size() < 193 && !pending.isEmpty()) {
                Span next = pending.remove();
                if (next.split() < 0 || next.error() <= .0001) break;
                retained.add(next.split());
                pending.add(span(next.from(), next.split()));
                pending.add(span(next.split(), next.to()));
            }
            var result = new ArrayList<Point>();
            for (int index : retained) result.add(points.get(index));
            return new Path(motion, result, impact, hitEntity, normal);
        }
        private record Span(int from, int to, int split, double error) {}
        private Span span(int from, int to) {
            Point first=points.get(from), last=points.get(to);int split=-1;double error=0;
            for(int i=from+1;i<to;i++) {
                Point point=points.get(i);
                double fraction=(point.seconds()-first.seconds())/Math.max(1e-9,last.seconds()-first.seconds());
                double distance=point.position().distanceToSqr(first.position().lerp(last.position(),fraction));
                if(distance>error) {error=distance;split=i;}
            }
            return new Span(from,to,split,error);
        }
    }
    private PredictionEngine() {}

    public static Path trace(Level level, PredictionMotion motion, ToDoubleFunction<Vec3> rate,
                             List<PredictionMotion> targets) {
        return trace(level, motion, rate, targetPaths(level, targets), motion.kind() == PredictionMotion.Kind.UNKNOWN ? 10 : 40, RANGE);
    }
    /** Display the whole loaded projectile flight and the mob's current navigation plan. */
    public static Path forecast(Level level, PredictionMotion motion, ToDoubleFunction<Vec3> rate,
                                List<PredictionMotion> targets) {
        return forecast(level, motion, rate, targetPaths(level, targets));
    }
    public static Path forecast(Level level, PredictionMotion motion, ToDoubleFunction<Vec3> rate,
                                Map<Integer, Path> targets) {
        if(motion.kind()==PredictionMotion.Kind.TNT) return PredictionTnt.forecast(level,motion,rate);
        int steps = motion.living() ? (motion.route().isEmpty() ? 60 : 120)
                : motion.kind() == PredictionMotion.Kind.UNKNOWN ? 120 : 1200;
        return trace(level, motion, rate, targets, steps, motion.living() ? RANGE : 256);
    }
    private static Map<Integer, Path> targetPaths(Level level, List<PredictionMotion> targets) {
        Map<Integer, Path> result = new HashMap<>();
        for (var target : targets) if (target.living())
            result.put(target.entityId(), forecast(level, target, p -> target.timeRate(), Map.of()));
        return result;
    }
    private static Path trace(Level level, PredictionMotion motion, ToDoubleFunction<Vec3> rate,
                              Map<Integer, Path> targets, int steps, double range) {
        List<Point> points = new ArrayList<>();
        points.add(new Point(motion.position(), 0));
        if (!motion.finite() || !level.hasChunkAt(BlockPos.containing(motion.position()))) return new Path(motion, points, false, -1);
        if (motion.living() && motion.velocity().horizontalDistanceSqr()<1e-8 && motion.routeSpeed()==0
                && ((motion.noGravity() || motion.gravity()==0) && Math.abs(motion.velocity().y)<1e-4
                    || motion.grounded() && motion.velocity().y<=0 && !level.noCollision(bounds(motion,motion.position()).move(0,-.001,0)))) {
            points.add(new Point(motion.position(),steps*.05));
            return new Path(motion,points,false,-1);
        }
        Vec3 pos = motion.position(), velocity = motion.velocity();
        double credit = motion.phase();
        boolean grounded = motion.grounded();
        int routeIndex = 0, jumpedNode = -1;
        while (routeIndex < motion.route().size()-1) {
            Vec3 toNode = motion.route().get(routeIndex).subtract(pos).multiply(1,0,1);
            if (toNode.lengthSqr() > .25 && toNode.dot(velocity.multiply(1,0,1)) >= 0) break;
            routeIndex++;
        }
        var fields = decelerationFields(level, motion);
        for (int tick = 1; tick <= steps; tick++) {
            double seconds = tick * .05;
            double factor = Math.max(0, Math.min(50, rate.applyAsDouble(pos)));
            credit += factor;
            int nativeSteps = Math.min(50, (int) (credit + 1e-7));
            credit -= nativeSteps;
            boolean continuous = motion.living() || motion.kind() != PredictionMotion.Kind.UNKNOWN;
            if (continuous) { nativeSteps = factor > 0 ? 1 : 0; credit = 0; }
            for (int step = 0; step < nativeSteps; step++) {
                if (motion.living() && grounded && !motion.noGravity() && motion.routeSpeed()>0
                        && routeIndex < motion.route().size() && jumpedNode != routeIndex) {
                    Vec3 node = motion.route().get(routeIndex).subtract(pos);
                    // Native ground MoveControl requests a jump for a nearby higher navigation node.
                    if (node.y > motion.stepHeight() && node.horizontalDistanceSqr() < Math.max(1, motion.width())) {
                        float jumpFactor = level.getBlockState(BlockPos.containing(pos)).getBlock().getJumpFactor();
                        if (jumpFactor == 1) jumpFactor = level.getBlockState(BlockPos.containing(pos.add(0,-.5,0))).getBlock().getJumpFactor();
                        velocity = new Vec3(velocity.x, .42F*jumpFactor+motion.jumpBoost(), velocity.z);
                        grounded=false; jumpedNode=routeIndex;
                    }
                }
                double beforeVertical = velocity.y;
                double movementScale = motion.living() ? 1 : com.timestop.combat.DecelerationFieldManager.movementScale(
                        pos, velocity, fields, com.timestop.config.TimeStopConfig.COMMON.decelerationDrag.get());
                if (continuous) movementScale *= factor;
                Vec3 next = pos.add(velocity.scale(movementScale));
                if (motion.living() && !motion.route().isEmpty()) {
                    double remaining = motion.routeSpeed() * movementScale;
                    next = pos.add(0, velocity.y * movementScale, 0);
                    while (remaining > 1e-6 && routeIndex < motion.route().size()) {
                        Vec3 delta = motion.route().get(routeIndex).subtract(next);
                        if (!motion.noGravity()) delta = delta.multiply(1, 0, 1);
                        double distance = delta.length();
                        if (distance <= remaining) { next = next.add(delta); routeIndex++; remaining -= distance; }
                        else { next = next.add(delta.scale(remaining / distance)); remaining = 0; }
                    }
                }
                double fullDistance=pos.distanceTo(next);
                boolean rangeEnd = next.distanceToSqr(motion.position()) > range * range;
                if (rangeEnd) {
                    Vec3 delta=next.subtract(pos), relative=pos.subtract(motion.position());
                    double aa=delta.lengthSqr(), bb=2*relative.dot(delta), cc=relative.lengthSqr()-range*range;
                    double fraction=aa<1e-12?0:(-bb+Math.sqrt(Math.max(0,bb*bb-4*aa*cc)))/(2*aa);
                    next=pos.lerp(next,Math.max(0,Math.min(1,fraction)));
                }
                double loaded=loadedFraction(level,pos,next);
                boolean loadedEnd=loaded<1;
                if(loadedEnd) next=pos.lerp(next,loaded);
                if(loadedEnd && next.distanceToSqr(pos)<1e-12) return new Path(motion,points,false,-1);
                double duration=fullDistance<1e-9?.05:.05*pos.distanceTo(next)/fullDistance;
                double endSeconds=seconds-.05+duration;
                Vec3 origin = motion.living() ? pos.add(0, motion.height() * .5, 0) : pos;
                Vec3 dest = motion.living() ? next.add(0, motion.height() * .5, 0) : next;
                BlockHitResult wall = motion.living() ? BlockHitResult.miss(dest, Direction.UP, BlockPos.containing(dest)) : clip(level, origin, dest);
                Vec3 hit = wall.getType() == HitResult.Type.MISS ? null : wall.getLocation();
                int hitEntity = -1;
                double hitSeconds=motion.living()?seconds:endSeconds;
                if(hit!=null && !motion.living())
                    hitSeconds=seconds-.05+duration*pos.distanceTo(hit)/Math.max(1e-9,pos.distanceTo(next));
                if (!motion.living()) {
                    double nearest = hit == null ? Double.MAX_VALUE : pos.distanceToSqr(hit);
                    for (Path targetPath : targets.values()) {
                        PredictionMotion target = targetPath.motion();
                        if (target.entityId() == motion.ownerId() || target.entityId() == motion.entityId()) continue;
                        // Native arrows/tridents are normally dodged by Endermen, rather than embedded.
                        if (target.kind()==PredictionMotion.Kind.ENDERMAN && motion.kind()==PredictionMotion.Kind.ARROW) continue;
                        // Use exactly the same curved navigation forecast as the visible future body.
                        // Sweep relative motion, so a target crossing between ticks cannot be missed.
                        boolean plannedStop=!target.route().isEmpty()
                                && targetPath.end().position().distanceToSqr(target.route().get(target.route().size()-1))<.01;
                        boolean stationary=target.velocity().lengthSqr()<1e-8 && target.routeSpeed()==0;
                        if(endSeconds>targetPath.end().seconds() && !plannedStop && !stationary && !targetPath.impact()) continue;
                        Vec3 before = targetPath.positionAt(seconds - .05), after = targetPath.positionAt(endSeconds);
                        Vec3 relativeEnd = next.subtract(after.subtract(before));
                        AABB box = bounds(target, before).inflate(motion.kind()==PredictionMotion.Kind.NATIVE ? motion.width()*.5 : .3);
                        var intersection = box.contains(pos) ? java.util.Optional.of(pos) : box.clip(pos, relativeEnd);
                        if (intersection.isPresent()) {
                            double relativeLength = pos.distanceTo(relativeEnd);
                            double fraction = relativeLength < 1e-9 ? 0 : pos.distanceTo(intersection.get()) / relativeLength;
                            Vec3 contact = pos.lerp(next, Math.min(1, fraction));
                            if (pos.distanceToSqr(contact) < nearest) {
                                hit = contact; nearest = pos.distanceToSqr(hit); hitEntity = target.entityId();
                                hitSeconds=seconds-.05+duration*Math.min(1,fraction);
                            }
                        }
                    }
                }
                if (hit != null) {
                    if (motion.living()) hit = hit.subtract(0, motion.height() * .5, 0);
                    points.add(new Point(hit, hitSeconds));
                    return new Path(motion, points, true, hitEntity,
                            hitEntity<0?Vec3.atLowerCornerOf(wall.getDirection().getNormal()):Vec3.ZERO);
                }
                if (motion.living()) {
                    Vec3 requested = next.subtract(pos);
                    AABB box = bounds(motion, pos);
                    double stepHeight = motion.stepHeight();
                    if (!loadedBounds(level, box.expandTowards(requested).inflate(stepHeight + 1))) {
                        return new Path(motion, points, false, -1);
                    }
                    Vec3 movement = livingCollision(level, box, requested, grounded, stepHeight);
                    next = pos.add(movement);
                    if (movement.y != requested.y) velocity = new Vec3(velocity.x, 0, velocity.z);
                    grounded = velocity.y <= 0 && !level.noCollision(bounds(motion, next).move(0, -.001, 0));
                }
                pos = next;
                if(rangeEnd || loadedEnd) { points.add(new Point(pos,endSeconds));return new Path(motion,points,false,-1); }
                if (motion.living() && !motion.route().isEmpty() && routeIndex >= motion.route().size()) {
                    velocity = new Vec3(0, velocity.y, 0);
                }
                boolean water = level.getFluidState(BlockPos.containing(pos)).is(FluidTags.WATER);
                double drag = water ? motion.waterDrag() : motion.drag();
                double elapsedDrag = 1 + (drag - 1) * movementScale;
                if (motion.living()) {
                    // The actual prediction travel hook blends PRE-collision velocity with native POST-travel velocity.
                    double gravity = motion.noGravity() ? 0 : motion.gravity();
                    velocity = new Vec3(velocity.x,
                            beforeVertical + ((velocity.y - gravity) * drag - beforeVertical) * movementScale, velocity.z);
                } else if (motion.kind() == PredictionMotion.Kind.FIREBALL) {
                    velocity = velocity.scale(elapsedDrag).add(motion.acceleration().scale(drag * movementScale));
                } else {
                    double gravity = motion.noGravity() ? 0 : motion.gravity();
                    if (water && motion.kind() == PredictionMotion.Kind.NATIVE) gravity *= .6;
                    velocity = velocity.scale(elapsedDrag).add(0, -gravity * movementScale, 0);
                }
            }
            points.add(new Point(pos, seconds));
        }
        return new Path(motion, points, false, -1);
    }
    /** Native body collision and step selection, applied only to copied positions and loaded terrain. */
    private static Vec3 livingCollision(Level level, AABB box, Vec3 requested, boolean grounded, double stepHeight) {
        var shapes = List.of(level.getWorldBorder().getCollisionShape());
        Vec3 movement = Entity.collideBoundingBox(null, requested, box, level, shapes);
        boolean horizontal = requested.x != movement.x || requested.z != movement.z;
        if (stepHeight > 0 && horizontal && (grounded || requested.y < 0 && requested.y != movement.y)) {
            Vec3 step = Entity.collideBoundingBox(null, new Vec3(requested.x, stepHeight, requested.z), box, level, shapes);
            Vec3 up = Entity.collideBoundingBox(null, new Vec3(0, stepHeight, 0), box.expandTowards(requested.x, 0, requested.z), level, shapes);
            if (up.y < stepHeight) {
                Vec3 alternative = Entity.collideBoundingBox(null, new Vec3(requested.x, 0, requested.z), box.move(up), level, shapes).add(up);
                if (alternative.horizontalDistanceSqr() > step.horizontalDistanceSqr()) step = alternative;
            }
            if (step.horizontalDistanceSqr() > movement.horizontalDistanceSqr()) {
                movement = step.add(Entity.collideBoundingBox(null, new Vec3(0, requested.y-step.y, 0), box.move(step), level, shapes));
            }
        }
        return movement;
    }
    private static boolean loadedBounds(Level level, AABB box) {
        for (int x=net.minecraft.util.Mth.floor(box.minX)>>4; x<=net.minecraft.util.Mth.floor(box.maxX)>>4; x++)
            for (int z=net.minecraft.util.Mth.floor(box.minZ)>>4; z<=net.minecraft.util.Mth.floor(box.maxZ)>>4; z++)
                if (!level.hasChunk(x,z)) return false;
        return true;
    }
    public static AABB bounds(PredictionMotion motion, Vec3 pos) {
        double r = motion.width() * .5;
        return new AABB(pos.x-r, pos.y, pos.z-r, pos.x+r, pos.y+motion.height(), pos.z+r);
    }
    public static BlockHitResult clip(Level level, Vec3 from, Vec3 to) {
        // Aim previews have no live projectile. Use the empty collision context without creating one.
        return BlockGetter.traverseBlocks(from, to, level, (world, pos) -> {
            var state = world.getBlockState(pos);
            var shape = state.getCollisionShape(world, pos, CollisionContext.empty());
            return world.clipWithInteractionOverride(from, to, pos, shape, state);
        }, world -> BlockHitResult.miss(to, Direction.getNearest(from.x-to.x, from.y-to.y, from.z-to.z), BlockPos.containing(to)));
    }
    public static boolean loadedSegment(Level level, Vec3 a, Vec3 b) {
        return loadedFraction(level,a,b)>=1;
    }
    private static double loadedFraction(Level level, Vec3 a, Vec3 b) {
        // Check every crossed chunk interval, including thin intervals beside diagonal corners.
        List<Double> cuts=new ArrayList<>();cuts.add(0.0);cuts.add(1.0);
        for(int axis=0;axis<2;axis++) {
            double from=axis==0?a.x:a.z, to=axis==0?b.x:b.z, delta=to-from;
            if(Math.abs(delta)<1e-9) continue;
            int low=(int)Math.floor(Math.min(from,to)/16), high=(int)Math.floor(Math.max(from,to)/16);
            for(int chunk=low+1;chunk<=high;chunk++) cuts.add((chunk*16-from)/delta);
        }
        cuts.sort(Double::compare);
        if(!level.hasChunkAt(BlockPos.containing(a))) return 0;
        double margin=1e-6/Math.max(1,a.distanceTo(b));
        for(int i=1;i<cuts.size();i++)
            if(!level.hasChunkAt(BlockPos.containing(a.lerp(b,(cuts.get(i-1)+cuts.get(i))*.5))))
                return Math.max(0,cuts.get(i-1)-margin);
        return level.hasChunkAt(BlockPos.containing(b))?1:Math.max(0,1-margin);
    }
    private static List<com.timestop.combat.DecelerationFieldManager.Sphere> decelerationFields(Level level, PredictionMotion motion) {
        List<com.timestop.combat.DecelerationFieldManager.Sphere> fields = new ArrayList<>();
        if (motion.living()) return fields;
        if (level.isClientSide) {
            for (var bubble : com.timestop.core.ClientBubbleManager.getActiveBubbles()) {
                if (bubble.mode == com.timestop.core.TimeMode.DECELERATION_FIELD
                        && bubble.dimensionId.equals(level.dimension().location().toString()))
                    fields.add(new com.timestop.combat.DecelerationFieldManager.Sphere(bubble.getCenter(), bubble.radius));
            }
        } else {
            for (var bubble : com.timestop.core.TemporalBubbleManager.getActiveBubbles().values()) {
                if (bubble.getMode() == com.timestop.core.TimeMode.DECELERATION_FIELD && bubble.getDimension().equals(level.dimension()))
                    fields.add(new com.timestop.combat.DecelerationFieldManager.Sphere(bubble.getCenter(), bubble.getRadius()));
            }
        }
        for (var player : level.players()) {
            if (player.getId() == motion.ownerId() || !player.isAlive() || player.isSpectator()) continue;
            double radius = com.timestop.combat.DecelerationFieldManager.getDecelerationRadius(player);
            if (radius > 0) fields.add(new com.timestop.combat.DecelerationFieldManager.Sphere(
                    player.position().add(0, player.getEyeHeight() * .5, 0), radius));
        }
        return fields;
    }
}
