package com.timestop.worldgen;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** A single saved template piece, safely clipped to each generation chunk. */
public final class ObservatoryPiece extends StructurePiece {
    public static final BlockPos PIVOT=new BlockPos(22,0,22);
    public static final BlockPos ACROPOLIS_PIVOT=new BlockPos(35,0,35);
    private final String variant;
    private final BlockPos origin;
    private final Rotation rotation;
    private final StructureTemplate template;
    private final List<BlockPos> foundations;

    public static net.minecraft.core.Vec3i expectedSize(String variant) {
        return variant.equals("acropolis") ? new net.minecraft.core.Vec3i(70, 55, 70) : new net.minecraft.core.Vec3i(45, 40, 45);
    }

    public static BlockPos pivot(String variant) {
        return variant.equals("acropolis") ? ACROPOLIS_PIVOT : PIVOT;
    }

    private static BoundingBox makeBoundingBox(String variant, BlockPos origin) {
        var size = expectedSize(variant);
        int depth = variant.equals("acropolis") ? 32 : 24;
        return new BoundingBox(origin.getX(), origin.getY() - depth, origin.getZ(),
                origin.getX() + size.getX() - 1, origin.getY() + size.getY() - 1, origin.getZ() + size.getZ() - 1);
    }

    public ObservatoryPiece(StructureTemplateManager manager, String variant, BlockPos origin, Rotation rotation) {
        super(ModObservatories.PIECE, 0, makeBoundingBox(variant, origin));
        if(!ObservatoryStructure.VARIANTS.contains(variant)) throw new IllegalArgumentException("Unknown observatory variant: " + variant);
        this.variant = variant; this.origin = origin.immutable(); this.rotation = rotation;
        template = manager.getOrCreate(templateId(variant));
        if(!template.getSize().equals(expectedSize(variant)))
            throw new IllegalStateException("Missing or incorrectly sized observatory template: " + variant + " expected " + expectedSize(variant) + " actual " + template.getSize());
        // The lowest deepslate-brick block marks the foundation of each authored column.
        Map<Long,BlockPos> lowest=new HashMap<>();
        for(var block:template.filterBlocks(origin,settings(variant,rotation),Blocks.DEEPSLATE_BRICKS)) {
            if(variant.equals("acropolis") && block.pos().getY() > origin.getY()) continue;
            long key=ChunkPos.asLong(block.pos().getX(),block.pos().getZ());
            lowest.merge(key,block.pos(),(a,b) -> a.getY()<b.getY()?a:b);
        }
        foundations=lowest.values().stream().sorted(java.util.Comparator.comparingLong(BlockPos::asLong)).toList();
    }

    public ObservatoryPiece(StructurePieceSerializationContext context, CompoundTag tag) {
        this(context.structureTemplateManager(),tag.getString("Variant"),new BlockPos(tag.getInt("OriginX"),tag.getInt("OriginY"),tag.getInt("OriginZ")),Rotation.valueOf(tag.getString("Rotation")));
    }

    public static ResourceLocation templateId(String variant) { return new ResourceLocation("timestop","ruined_observatory/"+variant); }
    public static StructurePlaceSettings settings(Rotation rotation) { return settings("default", rotation); }
    public static StructurePlaceSettings settings(String variant, Rotation rotation) {
        // Connections are authored in the template. Neighbor shape updates can escape the generation chunk.
        return new StructurePlaceSettings().setRotation(rotation).setRotationPivot(pivot(variant)).setIgnoreEntities(true).setKeepLiquids(false).setKnownShape(true);
    }

    @Override protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
        tag.putString("Variant",variant); tag.putString("Rotation",rotation.name());
        tag.putInt("OriginX",origin.getX()); tag.putInt("OriginY",origin.getY()); tag.putInt("OriginZ",origin.getZ());
    }

    @Override public Rotation getRotation() { return rotation; }

    @Override public void postProcess(WorldGenLevel level, StructureManager manager, ChunkGenerator generator, RandomSource random,
                                      BoundingBox chunkBounds, ChunkPos chunk, BlockPos pivot) {
        // Derive a local seed instead of consuming shared generation randomness in chunk-order-dependent ways.
        RandomSource placementRandom=RandomSource.create(origin.asLong() ^ chunk.toLong() ^ variant.hashCode());
        template.placeInWorld(level,origin,origin,settings(variant,rotation).setBoundingBox(chunkBounds),placementRandom,18);
        int maxDepth = variant.equals("acropolis") ? 32 : 24;
        for(BlockPos start:foundations) {
            if(start.getX()<chunkBounds.minX() || start.getX()>chunkBounds.maxX()
                    || start.getZ()<chunkBounds.minZ() || start.getZ()>chunkBounds.maxZ()) continue;
            BlockPos.MutableBlockPos pos=start.mutable().move(Direction.DOWN);
            for(int depth=0;depth<maxDepth && pos.getY()>=level.getMinBuildHeight() && chunkBounds.isInside(pos);depth++,pos.move(Direction.DOWN)) {
                var state=level.getBlockState(pos);
                if(state.getFluidState().isEmpty() && state.isFaceSturdy(level,pos,Direction.UP)) break;
                level.setBlock(pos,Blocks.DEEPSLATE_BRICKS.defaultBlockState(),18);
            }
        }
    }
}
