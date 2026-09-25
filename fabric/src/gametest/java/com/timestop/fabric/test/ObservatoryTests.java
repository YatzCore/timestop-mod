package com.timestop.fabric.test;

import com.timestop.worldgen.*;
import com.timestop.pedestal.*;
import com.timestop.item.ModItems;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.*;
import net.minecraft.core.registries.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.levelgen.*;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.level.levelgen.structure.*;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.storage.loot.*;
import net.minecraft.world.level.storage.loot.parameters.*;
import net.minecraft.world.phys.*;
import java.util.*;

public class ObservatoryTests implements FabricGameTest {
    @GameTest(template=EMPTY_STRUCTURE,batch="observatory_commands",timeoutTicks=300)
    public void vanillaTemplateCommandsPreserveLootAndPedestals(GameTestHelper h) {
        var level=h.getLevel(); int index=0;
        for(String variant:List.of("highland","forest","cherry","floral","windswept","acropolis")) {
            int x=2304+96*index++;
            int chunkSpan=variant.equals("acropolis")?5:3;
            for(int dx=0;dx<chunkSpan;dx++) for(int dz=0;dz<chunkSpan;dz++) level.getChunk((x>>4)+dx,80+dz);
            int result=level.getServer().getCommands().performPrefixedCommand(level.getServer().createCommandSourceStack().withSuppressedOutput(),
                    "place template timestop:ruined_observatory/"+variant+" "+x+" 130 1280 none none 1.0 42");
            h.assertTrue(result==1,"Vanilla /place template failed for "+variant);
            var pedestalPos=variant.equals("acropolis")?new BlockPos(x+22,149,1302):new BlockPos(x+22,139,1306);
            var pedestal=(PedestalBlockEntity)level.getBlockEntity(pedestalPos);
            h.assertTrue(pedestal!=null && pedestal.getOwner()==null && pedestal.getWatch().isEmpty(),"Command placement changed pedestal state");
            var chest=(ChestBlockEntity)level.getBlockEntity(new BlockPos(x+23,131,1301));
            h.assertTrue(chest!=null && chest.saveWithoutMetadata().getString("LootTable").equals("timestop:chests/observatory_archive"),"Command placement must retain deferred archive loot");
        }
        h.succeed();
    }

    @GameTest(template=EMPTY_STRUCTURE,batch="observatory_templates",timeoutTicks=1600)
    public void templatesRotateClipReloadAndClaim(GameTestHelper h) {
        var level=h.getLevel(); var manager=level.getStructureManager();
        var context=StructurePieceSerializationContext.fromLevel(level);
        Set<UUID> ids=new HashSet<>(); int index=0;
        var player=RewindRuneTests.survivalPlayer(h);
        try {
            for(String variant:List.of("highland","forest","cherry","floral","windswept","acropolis")) for(Rotation rotation:Rotation.values()) {
                BlockPos origin=new BlockPos(1280+index++*96,120,1280);
                var piece=new ObservatoryPiece(manager,variant,origin,rotation);
                var saved=piece.createTag(context);
                piece=new ObservatoryPiece(context,saved);
                h.assertTrue(piece.getRotation()==rotation && saved.equals(piece.createTag(context)),"Piece state survives save/reload");
                var template=manager.getOrCreate(ObservatoryPiece.templateId(variant));
                var raw=template.save(new CompoundTag());
                validatePalette(h,variant);
                var bounds=piece.getBoundingBox();
                var chunks=new ArrayList<ChunkPos>();
                for(int x=bounds.minX()>>4;x<=bounds.maxX()>>4;x++) for(int z=bounds.minZ()>>4;z<=bounds.maxZ()>>4;z++) {
                    level.getChunk(x,z); chunks.add(new ChunkPos(x,z));
                }
                // Alternate ordering to catch dependence on a neighboring chunk having been generated first.
                if(index%2==0) Collections.reverse(chunks);
                for(var chunk:chunks) {
                    var clip=new BoundingBox(chunk.getMinBlockX(),level.getMinBuildHeight(),chunk.getMinBlockZ(),chunk.getMaxBlockX(),level.getMaxBuildHeight()-1,chunk.getMaxBlockZ());
                    var checked=(WorldGenLevel)java.lang.reflect.Proxy.newProxyInstance(WorldGenLevel.class.getClassLoader(),new Class<?>[]{WorldGenLevel.class},(proxy,method,args) -> {
                        if((method.getName().equals("setBlock") || method.getName().equals("removeBlock") || method.getName().equals("destroyBlock")) && args[0] instanceof BlockPos pos)
                            h.assertTrue(clip.isInside(pos),"Placement escaped generation chunk at "+pos);
                        try { return method.invoke(level,args); }
                        catch(java.lang.reflect.InvocationTargetException e) { throw e.getCause(); }
                    });
                    piece.postProcess(checked,level.structureManager(),level.getChunkSource().getGenerator(),RandomSource.create(42),clip,chunk,origin);
                }
                int pedestals=0,chests=0;
                for(var entry:raw.getList("blocks",Tag.TAG_COMPOUND)) {
                    var block=(CompoundTag)entry; var p=block.getList("pos",Tag.TAG_INT);
                    var local=new BlockPos(p.getInt(0),p.getInt(1),p.getInt(2));
                    var pos=StructureTemplate.transform(local,Mirror.NONE,rotation,ObservatoryPiece.pivot(variant)).offset(origin);
                    var expected=NbtUtils.readBlockState(BuiltInRegistries.BLOCK.asLookup(),raw.getList("palette",Tag.TAG_COMPOUND).getCompound(block.getInt("state"))).rotate(rotation);
                    h.assertTrue(level.getBlockState(pos).equals(expected),"Rotated block differs at "+variant+" "+rotation+" "+local+": expected "+expected+", actual "+level.getBlockState(pos));
                    if(level.getBlockEntity(pos) instanceof PedestalBlockEntity pedestal) {
                        pedestals++;
                        h.assertTrue(ids.add(pedestal.getFieldId()),"Each placed pedestal needs an independent field ID");
                        h.assertTrue(pedestal.getOwner()==null && pedestal.getWatch().isEmpty() && !pedestal.isActive(),"Generated pedestal must be empty, inactive and unowned");
                        player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(ModItems.COPPER_WATCH.get()));
                        expected.getBlock().use(expected,level,pos,player,InteractionHand.MAIN_HAND,new BlockHitResult(Vec3.atCenterOf(pos),Direction.UP,pos,false));
                        h.assertTrue(player.getUUID().equals(pedestal.getOwner()) && pedestal.getWatch().is(ModItems.COPPER_WATCH.get()),"Existing interaction claims and fills generated pedestal");
                        var support=level.getBlockState(pos.below());
                        level.setBlock(pos.below(),Blocks.REDSTONE_BLOCK.defaultBlockState(),18);
                        pedestal.configure(com.timestop.core.TimeMode.SLOW_MOTION,1); PedestalManager.serverTick();
                        h.assertTrue(pedestal.isActive(),"Claimed generated pedestal operates with redstone");
                        level.setBlock(pos.below(),support,18); PedestalManager.serverTick();
                        h.assertFalse(pedestal.isActive(),"Generated pedestal deactivates normally");
                        var persisted=pedestal.saveWithoutMetadata(); pedestal.load(persisted);
                        h.assertTrue(ids.contains(pedestal.getFieldId()),"Claimed field ID survives reload");
                        pedestal.inventory.clearContent();
                    }
                    if(level.getBlockEntity(pos) instanceof ChestBlockEntity chest) {
                        chests++;
                        h.assertTrue(chest.saveWithoutMetadata().contains("LootTable"),"Unopened chest retains persistent loot reference: "+variant+" "+rotation+" "+local+" "+chest.saveWithoutMetadata());
                        chest.unpackLootTable(null); chest.clearContent();
                        var persisted=chest.saveWithoutMetadata(); chest.load(persisted);
                        h.assertTrue(!persisted.contains("LootTable") && chest.isEmpty(),"Loot cannot replenish after save/reload");
                    }
                }
                h.assertTrue(pedestals==3 && chests==5,"Each variant must contain three pedestals and five chests");
                var foundation=StructureTemplate.transform(new BlockPos(23,0,21),Mirror.NONE,rotation,ObservatoryPiece.pivot(variant)).offset(origin);
                int maxFoundation=variant.equals("acropolis")?32:24;
                h.assertTrue(level.getBlockState(foundation.below()).is(Blocks.DEEPSLATE_BRICKS) && level.getBlockState(foundation.below(maxFoundation)).is(Blocks.DEEPSLATE_BRICKS),"Foundation did not support the archive over a void");
                h.assertTrue(level.isEmptyBlock(foundation.below(maxFoundation+1)),"Foundation support exceeded its bound");
            }
        } finally { player.discard(); }
        h.succeed();
    }

    private static void validatePalette(GameTestHelper h,String variant) {
        try(var input=ObservatoryTests.class.getResourceAsStream("/data/timestop/structures/ruined_observatory/"+variant+".nbt")) {
            var nbt=NbtIo.readCompressed(input);
            for(var tag:nbt.getList("palette",Tag.TAG_COMPOUND)) {
                var state=(CompoundTag)tag; var id=new ResourceLocation(state.getString("Name"));
                h.assertTrue(BuiltInRegistries.BLOCK.containsKey(id),"Unknown template block: "+id);
                var definition=BuiltInRegistries.BLOCK.get(id).getStateDefinition();
                var properties=state.getCompound("Properties");
                for(String key:properties.getAllKeys()) {
                    var property=definition.getProperty(key);
                    h.assertTrue(property!=null && property.getValue(properties.getString(key)).isPresent(),"Invalid block property: "+id+" "+key);
                }
            }
        } catch(java.io.IOException e) { throw new RuntimeException(e); }
    }

    @GameTest(template=EMPTY_STRUCTURE,batch="observatory_loot")
    public void archiveLootGuaranteesAndDistribution(GameTestHelper h) {
        var table=h.getLevel().getServer().getLootData().getLootTable(new ResourceLocation("timestop","chests/observatory_archive"));
        var params=new LootParams.Builder(h.getLevel()).withParameter(LootContextParams.ORIGIN,Vec3.ZERO).create(LootContextParamSets.CHEST);
        int utility=0,deflection=0,snatching=0;
        for(int seed=1;seed<=256;seed++) {
            int golden=0,blank=0,extra=0;
            for(var item:table.getRandomItems(params,seed)) {
                String id=BuiltInRegistries.ITEM.getKey(item.getItem()).toString();
                if(id.equals("timestop:chronos_watch")) golden+=item.getCount();
                if(id.equals("timestop:blank_rune")) blank+=item.getCount();
                if(id.equals("timestop:rune_deflection")) { extra+=item.getCount(); deflection++; }
                if(id.equals("timestop:rune_snatching")) { extra+=item.getCount(); snatching++; }
                h.assertFalse(id.equals("timestop:diamond_watch") || id.equals("timestop:netherite_watch") || id.equals("timestop:creative_watch"),"Forbidden watch in archive");
            }
            h.assertTrue(golden==1 && blank==1 && extra<=1,"Archive guarantees exactly one Golden Watch and blank rune, at most one utility rune");
            utility+=extra;
        }
        h.assertTrue(utility>=40 && utility<=90 && deflection>10 && snatching>10,"Utility rune distribution is inconsistent with 25% equally weighted loot");
        com.timestop.TimeStopMod.LOGGER.info("OBSERVATORY_LOOT_PASS utility={}/256 deflection={} snatching={}",utility,deflection,snatching);
        h.succeed();
    }

    @GameTest(template=EMPTY_STRUCTURE,batch="observatory_terrain",timeoutTicks=500)
    public void terrainAcrossSeeds(GameTestHelper h) {
        var level=h.getLevel(); var access=level.registryAccess();
        var generator=(NoiseBasedChunkGenerator)access.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.NORMAL).value().createWorldDimensions().overworld();
        int accepted=0,rejected=0;
        for(long seed:new long[]{42,16310624,8675309}) {
            var random=RandomState.create(generator.generatorSettings().value(),access.registryOrThrow(Registries.NOISE).asLookup(),seed);
            int seedAccepted=0;
            for(int x=-4;x<=4;x++) for(int z=-4;z<=4;z++) {
                var chunk=new ChunkPos(x*17,z*17);
                for(String variant:List.of("highland","forest","cherry","floral","windswept","acropolis")) {
                    var structure=access.registryOrThrow(Registries.STRUCTURE).get(new ResourceLocation("timestop","ruined_observatory_"+variant));
                    h.assertTrue(structure instanceof ObservatoryStructure,"Datapack structure registration missing");
                    var start=structure.generate(access,generator,generator.getBiomeSource(),random,level.getStructureManager(),seed,chunk,0,level,biome -> true);
                    if(!start.isValid()) { rejected++; continue; }
                    accepted++; seedAccepted++;
                    int min=999,max=-999;
                    int radius = variant.equals("acropolis") ? 30 : 20;
                    int step = radius / 2;
                    for(int dx=-radius;dx<=radius;dx+=step) for(int dz=-radius;dz<=radius;dz+=step) {
                        int xx=chunk.getMiddleBlockX()+dx,zz=chunk.getMiddleBlockZ()+dz;
                        int surface=generator.getBaseHeight(xx,zz,Heightmap.Types.WORLD_SURFACE_WG,level,random);
                        int floor=generator.getBaseHeight(xx,zz,Heightmap.Types.OCEAN_FLOOR_WG,level,random);
                        h.assertTrue(surface==floor && surface>generator.getSeaLevel(),"Water-covered site accepted");
                        min=Math.min(min,surface); max=Math.max(max,surface);
                    }
                    int maxRelief = variant.equals("acropolis") ? 22 : 12;
                    h.assertTrue(max-min<=maxRelief,"Excessive slope accepted");
                    var saved=start.createTag(StructurePieceSerializationContext.fromLevel(level),chunk);
                    var restored=StructureStart.loadStaticStart(StructurePieceSerializationContext.fromLevel(level),saved,seed);
                    h.assertTrue(restored!=null && restored.isValid() && restored.getBoundingBox().equals(start.getBoundingBox()),"Structure start save/reload failed");
                }
            }
            h.assertTrue(seedAccepted>0,"No suitable terrain found for seed "+seed);
        }
        h.assertTrue(rejected>0,"Terrain test did not exercise rejection");
        com.timestop.TimeStopMod.LOGGER.info("OBSERVATORY_TERRAIN_PASS accepted={} rejected={} three seeds",accepted,rejected);
        h.succeed();
    }
}
