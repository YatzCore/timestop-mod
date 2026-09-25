package com.timestop.fabric.test;

import com.timestop.worldgen.*;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.*;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.util.RandomSource;
import java.nio.file.Files;

/** Opt-in inspection world. No test code is packaged in either release JAR. */
public class ObservatoryVisualQa implements ClientModInitializer {
    private int phase,boot,ticks,shot=-1;
    private volatile boolean ready;
    private float yaw,pitch;
    private final boolean natural=Boolean.getBoolean("timestop.observatoryNaturalQa");
    private final BlockPos[] origins={new BlockPos(0,70,0),new BlockPos(128,70,0)};
    private final Rotation[] rotations={Rotation.NONE,Rotation.NONE};
    private final java.util.Map<Integer,String> naturalSnapshots=new java.util.HashMap<>();
    // Camera feet and look target, relative to template origin. Both variants use identical framing.
    private static final double[][] VIEWS={
        {61,36,65,22,17,21}, {-18,38,-24,22,18,19}, {28,10,30,22,12,21},
        {11,10,36,5,11,26}, {40,10,26,35,11,22}, {28,1.5,31,22,2.5,21},
        {23,20,8,37,25,8}, {61,36,65,22,17,21}, {28,10,30,22,12,21}
    };
    private static final String[] NAMES={"exterior","dome-breach","observation-hall","workshop","library","archive","upper-walkway","night-exterior","night-hall"};

    @Override public void onInitializeClient() {
        if(Boolean.getBoolean("timestop.observatoryVisualQa")) ClientTickEvents.END_CLIENT_TICK.register(this::tick);
    }
    private void tick(Minecraft mc) {
        if(phase==0 && ++boot>80 && mc.level==null && mc.getOverlay()==null && !(mc.screen instanceof TitleScreen)) mc.setScreen(new TitleScreen());
        if(phase==0 && mc.screen instanceof TitleScreen && mc.getOverlay()==null) {
            phase=1; mc.options.pauseOnLostFocus=false; mc.options.hideGui=true;
            mc.options.renderDistance().set(9); mc.options.gamma().set(.65); mc.options.fov().set(65);
            mc.options.enableVsync().set(false); mc.options.framerateLimit().set(60);
            mc.getTutorial().setStep(net.minecraft.client.tutorial.TutorialSteps.NONE);
            if(Files.exists(mc.gameDirectory.toPath().resolve("saves/ObservatoryQA/level.dat"))) mc.createWorldOpenFlows().loadLevel(mc.screen,"ObservatoryQA");
            else {
                var rules=new GameRules(); rules.getRule(GameRules.RULE_DAYLIGHT).set(false,null); rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false,null);
                mc.createWorldOpenFlows().createFreshLevel("ObservatoryQA",new LevelSettings("Observatory QA",GameType.SPECTATOR,false,Difficulty.PEACEFUL,true,rules,WorldDataConfiguration.DEFAULT),
                    new WorldOptions(16310624,natural,false),access -> access.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(natural?WorldPresets.NORMAL:WorldPresets.FLAT).value().createWorldDimensions());
            }
        }
        if(mc.level==null || mc.player==null || mc.getSingleplayerServer()==null) return;
        if(phase==1) {
            phase=2;
            mc.getSingleplayerServer().execute(() -> {
                var level=mc.getSingleplayerServer().overworld();
                for(int variant=0;variant<2;variant++) {
                    if(natural) {
                        String name=variant==0?"highland":"forest";
                        var key=net.minecraft.resources.ResourceKey.create(Registries.STRUCTURE,new net.minecraft.resources.ResourceLocation("timestop","ruined_observatory_"+name));
                        var holder=level.registryAccess().registryOrThrow(Registries.STRUCTURE).getHolderOrThrow(key);
                        var found=level.getChunkSource().getGenerator().findNearestMapStructure(level,net.minecraft.core.HolderSet.direct(holder),BlockPos.ZERO,100,false);
                        if(found==null) throw new IllegalStateException("No natural "+name+" observatory found");
                        var start=level.getChunkAt(found.getFirst()).getStartForStructure(holder.value());
                        if(start==null || !start.isValid()) throw new IllegalStateException("Located observatory has no start");
                        var piece=start.getPieces().get(0);
                        var tag=piece.createTag(net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext.fromLevel(level));
                        origins[variant]=new BlockPos(tag.getInt("OriginX"),tag.getInt("OriginY"),tag.getInt("OriginZ"));
                        rotations[variant]=piece.getRotation();
                        var b=piece.getBoundingBox();
                        for(int x=(b.minX()>>4)-1;x<=(b.maxX()>>4)+1;x++) for(int z=(b.minZ()>>4)-1;z<=(b.maxZ()>>4)+1;z++) level.getChunk(x,z);
                        com.timestop.TimeStopMod.LOGGER.info("OBSERVATORY_NATURAL_FOUND variant={} origin={} rotation={} biome={}",name,origins[variant],rotations[variant],level.getBiome(origins[variant].offset(22,8,22)).unwrapKey());
                        auditNatural(level,variant);
                        continue;
                    }
                    int offset=variant*128;
                    for(int x=-36;x<=80;x++) for(int z=-36;z<=80;z++) for(int y=64;y<=75;y++)
                        level.setBlock(new BlockPos(x+offset,y,z),(y==75?Blocks.GRASS_BLOCK:y>=73?Blocks.DIRT:Blocks.STONE).defaultBlockState(),2);
                    var origin=new BlockPos(offset,70,0);
                    var piece=new ObservatoryPiece(level.getStructureManager(),variant==0?"highland":"forest",origin,Rotation.NONE);
                    var b=piece.getBoundingBox();
                    for(int x=b.minX()>>4;x<=b.maxX()>>4;x++) for(int z=b.minZ()>>4;z<=b.maxZ()>>4;z++) {
                        var chunk=new ChunkPos(x,z);
                        piece.postProcess(level,level.structureManager(),level.getChunkSource().getGenerator(),RandomSource.create(42),
                            new BoundingBox(chunk.getMinBlockX(),-64,chunk.getMinBlockZ(),chunk.getMaxBlockX(),319,chunk.getMaxBlockZ()),chunk,origin);
                    }
                }
                level.setDayTime(6000); ready=true;
            });
        }
        if(!ready)return;
        if(ticks==0) {
            shot++;
            if(shot>=18) {
                ready=false;
                mc.getSingleplayerServer().execute(() -> {
                    if(natural) for(int variant=0;variant<2;variant++) auditNatural(mc.getSingleplayerServer().overworld(),variant);
                    com.timestop.TimeStopMod.LOGGER.info("OBSERVATORY_VISUAL_QA_COMPLETE"); mc.execute(mc::stop);
                });
                return;
            }
            var view=VIEWS[shot%9].clone(); int variant=shot/9;
            for(int index:new int[]{0,3}) {
                double x=view[index],z=view[index+2];
                switch(rotations[variant]) {
                    case CLOCKWISE_90 -> { view[index]=44-z; view[index+2]=x; }
                    case CLOCKWISE_180 -> { view[index]=44-x; view[index+2]=44-z; }
                    case COUNTERCLOCKWISE_90 -> { view[index]=z; view[index+2]=44-x; }
                    default -> {}
                }
            }
            double dx=view[3]-view[0],dy=view[4]-(view[1]+1.62),dz=view[5]-view[2];
            yaw=(float)Math.toDegrees(Math.atan2(-dx,dz)); pitch=(float)-Math.toDegrees(Math.atan2(dy,Math.hypot(dx,dz)));
            mc.getSingleplayerServer().execute(() -> {
                var server=mc.getSingleplayerServer(); var player=server.getPlayerList().getPlayer(mc.player.getUUID());
                player.setGameMode(GameType.SPECTATOR); server.overworld().setDayTime(shot%9>=7?18000:6000);
                player.connection.teleport(view[0]+origins[variant].getX(),view[1]+origins[variant].getY(),view[2]+origins[variant].getZ(),yaw,pitch);
            });
        }
        ticks++;
        mc.player.setYRot(yaw); mc.player.setYHeadRot(yaw); mc.player.setXRot(pitch);
        int duration=natural && shot%9==0?600:100;
        if(ticks==duration-10) {
            mc.getToasts().clear();
            String name=(shot<9?"highland-":"forest-")+NAMES[shot%9]+".png";
            Screenshot.grab(mc.gameDirectory,name,mc.getMainRenderTarget(),message -> com.timestop.TimeStopMod.LOGGER.info("Observatory QA: {}",message.getString()));
        }
        if(ticks>=duration) ticks=0;
    }

    private void auditNatural(net.minecraft.server.level.ServerLevel level,int variant) {
        var snapshot=new StringBuilder(); var unique=new java.util.HashSet<java.util.UUID>();
        for(BlockPos local:java.util.List.of(new BlockPos(22,9,26),new BlockPos(6,27,8),new BlockPos(36,35,8),
                new BlockPos(3,9,36),new BlockPos(34,9,21),new BlockPos(5,19,6),new BlockPos(15,9,12),new BlockPos(23,1,21))) {
            var pos=net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.transform(local,Mirror.NONE,rotations[variant],ObservatoryPiece.PIVOT).offset(origins[variant]);
            level.getChunkAt(pos);
            var entity=level.getBlockEntity(pos);
            if(entity instanceof com.timestop.pedestal.PedestalBlockEntity pedestal) {
                if(pedestal.getOwner()!=null || !pedestal.getWatch().isEmpty() || pedestal.isActive() || !unique.add(pedestal.getFieldId()))
                    throw new IllegalStateException("Invalid natural pedestal at "+pos);
                snapshot.append(pos).append(pedestal.getFieldId()).append('\n');
            } else if(entity instanceof net.minecraft.world.level.block.entity.ChestBlockEntity chest) {
                var tag=chest.saveWithoutMetadata();
                if(!tag.contains("LootTable")) throw new IllegalStateException("Natural chest rolled before opening: "+pos);
                snapshot.append(pos).append(tag).append('\n');
            } else throw new IllegalStateException("Missing natural block entity at "+pos);
        }
        if(unique.size()!=3) throw new IllegalStateException("Missing natural pedestals");
        var previous=naturalSnapshots.putIfAbsent(variant,snapshot.toString());
        if(previous!=null && !previous.equals(snapshot.toString())) throw new IllegalStateException("Natural block entity state changed after chunk reload");
        com.timestop.TimeStopMod.LOGGER.info("OBSERVATORY_NATURAL_AUDIT_PASS variant={} reloaded={}",variant,previous!=null);
    }
}
