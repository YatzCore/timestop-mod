package com.timestop.worldgen;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;
import java.util.Arrays;
import java.util.Optional;

public final class ObservatoryStructure extends Structure {
    public static final java.util.Set<String> VARIANTS = java.util.Set.of("highland", "forest", "cherry", "floral", "windswept", "acropolis");
    private static final Codec<String> VARIANT = Codec.STRING.comapFlatMap(
            value -> VARIANTS.contains(value) ? DataResult.success(value)
                    : DataResult.error(() -> "Unknown observatory variant: " + value), value -> value);
    public static final Codec<ObservatoryStructure> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            settingsCodec(instance), VARIANT.fieldOf("variant").forGetter(s -> s.variant)
    ).apply(instance, ObservatoryStructure::new));
    private final String variant;

    public ObservatoryStructure(StructureSettings settings, String variant) { super(settings); this.variant=variant; }

    @Override protected Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
        int x = context.chunkPos().getMiddleBlockX();
        int z = context.chunkPos().getMiddleBlockZ();
        boolean isAcropolis = variant.equals("acropolis");
        int radius = isAcropolis ? 30 : 20;
        int step = radius / 2;
        int[] heights=new int[25]; int i=0;
        for(int dx=-radius;dx<=radius;dx+=step) for(int dz=-radius;dz<=radius;dz+=step) {
            int surface=context.chunkGenerator().getBaseHeight(x+dx,z+dz,Heightmap.Types.WORLD_SURFACE_WG,context.heightAccessor(),context.randomState());
            int floor=context.chunkGenerator().getBaseHeight(x+dx,z+dz,Heightmap.Types.OCEAN_FLOOR_WG,context.heightAccessor(),context.randomState());
            if(surface>floor || surface<=context.chunkGenerator().getSeaLevel()) return Optional.empty();
            heights[i++]=surface;
        }
        Arrays.sort(heights);
        int entranceY = isAcropolis ? 12 : 8;
        int height = isAcropolis ? 55 : 40;
        int centerX = isAcropolis ? 35 : 22;
        int centerZ = isAcropolis ? 35 : 22;
        int maxRelief = isAcropolis ? 22 : 12;

        int entrance = heights[12] + (isAcropolis ? 0 : 2);
        if(heights[24]-heights[0]>maxRelief || entrance-entranceY-32<context.heightAccessor().getMinBuildHeight()
                || entrance-entranceY+height>=context.heightAccessor().getMaxBuildHeight()) return Optional.empty();
        BlockPos origin=new BlockPos(x-centerX,entrance-entranceY,z-centerZ);
        Rotation rotation=Rotation.getRandom(context.random());
        return Optional.of(new GenerationStub(new BlockPos(x,entrance,z), builder -> builder.addPiece(
                new ObservatoryPiece(context.structureTemplateManager(),variant,origin,rotation))));
    }

    @Override public StructureType<?> type() { return ModObservatories.TYPE; }
}
