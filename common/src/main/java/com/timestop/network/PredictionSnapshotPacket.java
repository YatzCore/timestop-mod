package com.timestop.network;

import com.timestop.TimeStopMod;
import com.timestop.prediction.*;
import java.util.*;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

public record PredictionSnapshotPacket(UUID field, ResourceLocation dimension, long validityMs,
        List<PredictionMotion> motions, PredictionMotion gun,
        List<PredictionEngine.Path> forecasts, List<PredictionTeleports.Preview> teleports,
        List<PredictionOutcomes.Event> outcomes) implements IClientboundPacket {
    public static final ResourceLocation ID = new ResourceLocation(TimeStopMod.MOD_ID, "prediction_snapshot");
    public PredictionSnapshotPacket {
        motions = List.copyOf(motions); forecasts = List.copyOf(forecasts); teleports = List.copyOf(teleports);
        outcomes = List.copyOf(outcomes);
        if (motions.size()>52 || forecasts.size()>28 || teleports.size()>4 || outcomes.size()>4) throw new IllegalArgumentException("Prediction snapshot exceeds limit");
    }
    public PredictionSnapshotPacket(UUID field, ResourceLocation dimension, long validityMs,
            List<PredictionMotion> motions, PredictionMotion gun, List<PredictionEngine.Path> forecasts,
            List<PredictionTeleports.Preview> teleports) {
        this(field,dimension,validityMs,motions,gun,forecasts,teleports,List.of());
    }
    public PredictionSnapshotPacket(UUID field, ResourceLocation dimension, long validityMs,
            List<PredictionMotion> motions, PredictionMotion gun) {
        this(field,dimension,validityMs,motions,gun,List.of(),List.of());
    }
    public PredictionSnapshotPacket(FriendlyByteBuf buf) { this(read(buf)); }
    private PredictionSnapshotPacket(PredictionSnapshotPacket p) {
        this(p.field,p.dimension,p.validityMs,p.motions,p.gun,p.forecasts,p.teleports,p.outcomes);
    }
    private static int count(FriendlyByteBuf buf,int max) {
        int count=buf.readVarInt();
        if (count<0 || count>max) throw new IllegalArgumentException("Invalid prediction snapshot size");
        return count;
    }
    private static PredictionSnapshotPacket read(FriendlyByteBuf buf) {
        UUID field=buf.readUUID(); var dimension=buf.readResourceLocation();
        long validity=Math.max(750,Math.min(15000,buf.readVarLong()));
        List<PredictionMotion> motions=new ArrayList<>(); Map<Integer,PredictionMotion> byId=new HashMap<>();
        int count=count(buf,52);
        for(int i=0;i<count;i++) {
            var motion=PredictionMotion.read(buf);
            if (!motion.finite()) throw new IllegalArgumentException("Nonfinite prediction motion");
            motions.add(motion); byId.put(motion.entityId(),motion);
        }
        var gun=buf.readBoolean()?PredictionMotion.read(buf):null;
        if(gun!=null && !gun.finite()) throw new IllegalArgumentException("Invalid gun forecast");
        List<PredictionEngine.Path> forecasts=new ArrayList<>(); count=count(buf,28);
        for(int i=0;i<count;i++) {
            var motion=byId.get(buf.readVarInt());
            if(motion==null) throw new IllegalArgumentException("Missing prediction entity");
            boolean impact=buf.readBoolean(); int hit=buf.readVarInt(),size=count(buf,193);
            var normal=PredictionMotion.readVec(buf);
            if(!Double.isFinite(normal.lengthSqr()) || normal.lengthSqr()>1.001) throw new IllegalArgumentException("Invalid contact normal");
            if(size==0) throw new IllegalArgumentException("Empty prediction path");
            List<PredictionEngine.Point> points=new ArrayList<>(); double last=-1;
            for(int j=0;j<size;j++) {
                Vec3Offset offset=new Vec3Offset(buf.readFloat(),buf.readFloat(),buf.readFloat());
                double time=buf.readFloat();
                if(!offset.finite() || !Double.isFinite(time) || time<last || time>60.01)
                    throw new IllegalArgumentException("Invalid prediction point");
                points.add(new PredictionEngine.Point(motion.position().add(offset.x,offset.y,offset.z),time)); last=time;
            }
            forecasts.add(new PredictionEngine.Path(motion,points,impact,hit,normal));
        }
        List<PredictionTeleports.Preview> teleports=new ArrayList<>(); count=count(buf,4);
        for(int i=0;i<count;i++) {
            int id=buf.readVarInt(); var from=PredictionMotion.readVec(buf); var to=PredictionMotion.readVec(buf); int ticks=buf.readVarInt();
            if(!Double.isFinite(from.lengthSqr()) || !Double.isFinite(to.lengthSqr()) || ticks<0 || ticks>12)
                throw new IllegalArgumentException("Invalid teleport preview");
            teleports.add(new PredictionTeleports.Preview(id,from,to,ticks));
        }
        List<PredictionOutcomes.Event> outcomes=new ArrayList<>();count=count(buf,4);
        for(int i=0;i<count;i++) {
            var event=PredictionOutcomes.Event.read(buf);
            if(!byId.containsKey(event.sourceId())) throw new IllegalArgumentException("Missing outcome source");
            outcomes.add(event);
        }
        return new PredictionSnapshotPacket(field,dimension,validity,motions,gun,forecasts,teleports,outcomes);
    }
    private record Vec3Offset(float x,float y,float z) {
        boolean finite() { return Float.isFinite(x)&&Float.isFinite(y)&&Float.isFinite(z)&&Math.abs(x)<=256.01&&Math.abs(y)<=256.01&&Math.abs(z)<=256.01; }
    }
    public static PredictionSnapshotPacket empty(ServerPlayer player) {
        return new PredictionSnapshotPacket(player.getUUID(),player.level().dimension().location(),750,List.of(),null);
    }
    @Override public ResourceLocation getId() { return ID; }
    @Override public void toBytes(FriendlyByteBuf buf) {
        buf.writeUUID(field);buf.writeResourceLocation(dimension);buf.writeVarLong(validityMs);
        buf.writeVarInt(motions.size());motions.forEach(m -> m.write(buf));
        buf.writeBoolean(gun!=null);if(gun!=null)gun.write(buf);
        buf.writeVarInt(forecasts.size());
        for(var full:forecasts) {
            var path=full.compact(); buf.writeVarInt(path.motion().entityId());buf.writeBoolean(path.impact());buf.writeVarInt(path.hitEntity());
            buf.writeVarInt(path.points().size());
            PredictionMotion.writeVec(buf,path.normal());
            for(var point:path.points()) {
                var delta=point.position().subtract(path.motion().position());
                buf.writeFloat((float)delta.x);buf.writeFloat((float)delta.y);buf.writeFloat((float)delta.z);buf.writeFloat((float)point.seconds());
            }
        }
        buf.writeVarInt(teleports.size());
        for(var p:teleports) {buf.writeVarInt(p.entityId());PredictionMotion.writeVec(buf,p.origin());PredictionMotion.writeVec(buf,p.destination());buf.writeVarInt(p.ticksLeft());}
        buf.writeVarInt(outcomes.size());outcomes.forEach(e -> e.write(buf));
    }
    @Override public void handleClient() { com.timestop.client.PredictionClient.receive(this); }
}
