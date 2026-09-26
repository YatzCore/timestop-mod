package com.timestop.worldgen;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;

/** Loader-neutral registry values; the two entry points register these instances. */
public final class ModObservatories {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("timestop", "ruined_observatory");
    public static final StructureType<ObservatoryStructure> TYPE = () -> ObservatoryStructure.CODEC;
    public static final StructurePieceType PIECE = ObservatoryPiece::new;
    private ModObservatories() {}
}
