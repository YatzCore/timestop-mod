package com.timestop.prediction;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.Vec3;
import java.util.List;

/** Immutable input: position, native step velocity, and native step physics. */
public record PredictionMotion(int entityId, int ownerId, Kind kind, Vec3 position, Vec3 velocity,
        Vec3 acceleration, double gravity, double drag, double waterDrag, float width, float height,
        boolean grounded, boolean noGravity, boolean free, float phase, double timeRate,
        List<Vec3> route, double routeSpeed, PredictionEffects.Profile effects, float stepHeight, float jumpBoost) {
    public PredictionMotion {
        route = List.copyOf(route);
        if (route.size() > 24) throw new IllegalArgumentException("Prediction route exceeds limit");
    }
    public PredictionMotion(int entityId, int ownerId, Kind kind, Vec3 position, Vec3 velocity,
            Vec3 acceleration, double gravity, double drag, double waterDrag, float width, float height,
            boolean grounded, boolean noGravity, boolean free, float phase, double timeRate,
            List<Vec3> route, double routeSpeed, PredictionEffects.Profile effects) {
        this(entityId,ownerId,kind,position,velocity,acceleration,gravity,drag,waterDrag,width,height,
                grounded,noGravity,free,phase,timeRate,route,routeSpeed,effects,
                kind==Kind.LIVING || kind==Kind.ENDERMAN ? .6F : 0, 0);
    }
    public PredictionMotion(int entityId, int ownerId, Kind kind, Vec3 position, Vec3 velocity,
            Vec3 acceleration, double gravity, double drag, double waterDrag, float width, float height,
            boolean grounded, boolean noGravity, boolean free, float phase, double timeRate,
            List<Vec3> route, double routeSpeed) {
        this(entityId,ownerId,kind,position,velocity,acceleration,gravity,drag,waterDrag,width,height,
                grounded,noGravity,free,phase,timeRate,route,routeSpeed,PredictionEffects.NONE);
    }
    public PredictionMotion(int entityId, int ownerId, Kind kind, Vec3 position, Vec3 velocity,
            Vec3 acceleration, double gravity, double drag, double waterDrag, float width, float height,
            boolean grounded, boolean noGravity, boolean free, float phase, double timeRate) {
        this(entityId, ownerId, kind, position, velocity, acceleration, gravity, drag, waterDrag,
                width, height, grounded, noGravity, free, phase, timeRate, List.of(), 0);
    }
    public enum Kind { LIVING, ARROW, THROWABLE, FIREBALL, NATIVE, UNKNOWN, ENDERMAN, TNT }
    public boolean living() { return kind == Kind.LIVING || kind == Kind.ENDERMAN; }
    public boolean approximate() { return living() || kind == Kind.UNKNOWN; }
    public PredictionMotion at(Vec3 position, Vec3 velocity) {
        return new PredictionMotion(entityId, ownerId, kind, position, velocity, acceleration, gravity,
                drag, waterDrag, width, height, grounded, noGravity, free, phase, timeRate, route, routeSpeed, effects, stepHeight, jumpBoost);
    }
    public PredictionMotion withRoute(List<Vec3> route, double speed) {
        return new PredictionMotion(entityId, ownerId, kind, position, velocity, acceleration, gravity,
                drag, waterDrag, width, height, grounded, noGravity, free, phase, timeRate, route, speed, effects, stepHeight, jumpBoost);
    }
    public PredictionMotion withEffects(PredictionEffects.Profile effects) {
        return new PredictionMotion(entityId,ownerId,kind,position,velocity,acceleration,gravity,drag,waterDrag,
                width,height,grounded,noGravity,free,phase,timeRate,route,routeSpeed,effects,stepHeight,jumpBoost);
    }
    public PredictionMotion withStepHeight(float value) {
        return new PredictionMotion(entityId,ownerId,kind,position,velocity,acceleration,gravity,drag,waterDrag,
                width,height,grounded,noGravity,free,phase,timeRate,route,routeSpeed,effects,value,jumpBoost);
    }
    public PredictionMotion withJumpBoost(float boost) {
        return new PredictionMotion(entityId,ownerId,kind,position,velocity,acceleration,gravity,drag,waterDrag,
                width,height,grounded,noGravity,free,phase,timeRate,route,routeSpeed,effects,stepHeight,boost);
    }
    public void write(FriendlyByteBuf buf) {
        buf.writeVarInt(entityId); buf.writeVarInt(ownerId); buf.writeEnum(kind);
        writeVec(buf, position); writeVec(buf, velocity); writeVec(buf, acceleration);
        buf.writeDouble(gravity); buf.writeDouble(drag); buf.writeDouble(waterDrag);
        buf.writeFloat(width); buf.writeFloat(height);
        buf.writeBoolean(grounded); buf.writeBoolean(noGravity); buf.writeBoolean(free);
        buf.writeFloat(phase); buf.writeDouble(timeRate);
        buf.writeVarInt(route.size()); route.forEach(v -> writeVec(buf, v)); buf.writeDouble(routeSpeed);
        effects.write(buf); buf.writeFloat(stepHeight); buf.writeFloat(jumpBoost);
    }
    public static PredictionMotion read(FriendlyByteBuf buf) {
        var motion = new PredictionMotion(buf.readVarInt(), buf.readVarInt(), buf.readEnum(Kind.class),
                readVec(buf), readVec(buf), readVec(buf), buf.readDouble(), buf.readDouble(),
                buf.readDouble(), buf.readFloat(), buf.readFloat(), buf.readBoolean(),
                buf.readBoolean(), buf.readBoolean(), buf.readFloat(), buf.readDouble());
        int count = buf.readVarInt();
        if (count < 0 || count > 24) throw new IllegalArgumentException("Invalid prediction route size");
        var route = new java.util.ArrayList<Vec3>(count);
        for (int i=0; i<count; i++) route.add(readVec(buf));
        return motion.withRoute(route, buf.readDouble()).withEffects(PredictionEffects.Profile.read(buf)).withStepHeight(buf.readFloat()).withJumpBoost(buf.readFloat());
    }
    public static void writeVec(FriendlyByteBuf buf, Vec3 v) {
        buf.writeDouble(v.x); buf.writeDouble(v.y); buf.writeDouble(v.z);
    }
    public static Vec3 readVec(FriendlyByteBuf buf) {
        return new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble());
    }
    public boolean finite() {
        return finite(position) && finite(velocity) && finite(acceleration) && Double.isFinite(gravity)
                && Double.isFinite(drag) && Double.isFinite(waterDrag) && Double.isFinite(timeRate)
                && Float.isFinite(jumpBoost) && jumpBoost >= 0 && jumpBoost < 32
                && Float.isFinite(stepHeight) && stepHeight >= 0 && stepHeight < 32
                && Float.isFinite(width) && Float.isFinite(height) && Float.isFinite(phase)
                && Double.isFinite(routeSpeed) && routeSpeed >= 0 && routeSpeed <= 10 && route.stream().allMatch(PredictionMotion::finite)
                && width > 0 && width < 32 && height > 0 && height < 32 && timeRate >= 0 && timeRate <= 50;
    }
    private static boolean finite(Vec3 v) {
        return Double.isFinite(v.x) && Double.isFinite(v.y) && Double.isFinite(v.z);
    }
}
