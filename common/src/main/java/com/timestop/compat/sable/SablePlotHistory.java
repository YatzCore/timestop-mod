package com.timestop.compat.sable;

import com.mojang.serialization.Codec;
import com.timestop.combat.RewindRuneManager;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.ticks.LevelChunkTicks;

/** Stock plot NBT contains absolute block-entity/tick coordinates, unlike its chunk keys. */
final class SablePlotHistory {
    private static final Codec<PalettedContainer<BlockState>> BLOCK_STATES = PalettedContainer.codecRW(
            Block.BLOCK_STATE_REGISTRY, BlockState.CODEC, PalettedContainer.Strategy.SECTION_STATES, Blocks.AIR.defaultBlockState());

    static void validate(ServerLevel level, ServerSubLevelContainer container, CompoundTag data) {
        try {
            if (data.getInt("log_size") != container.getLogPlotSize()) throw new IllegalArgumentException("Plot size mismatch");
            if (data.getInt("data_version") < 0 || data.getInt("data_version") > 1) throw new IllegalArgumentException("Unsupported plot version");
            var biome = ResourceLocation.tryParse(data.getString("biome"));
            if (biome == null || !level.registryAccess().registryOrThrow(Registries.BIOME).containsKey(biome))
                throw new IllegalArgumentException("Missing plot biome");
            if (!data.contains("chunks", Tag.TAG_COMPOUND)) throw new IllegalArgumentException("Missing plot chunks");
            var chunks = data.getCompound("chunks");
            int size = 1 << container.getLogPlotSize();
            for (var key : chunks.getAllKeys()) {
                long packed = Long.parseLong(key);
                int x = ChunkPos.getX(packed), z = ChunkPos.getZ(packed);
                if (x < 0 || z < 0 || x >= size || z >= size) throw new IllegalArgumentException("Plot chunk out of bounds");
                var sections = chunks.getCompound(key).getCompound("sections");
                for (var sectionKey : sections.getAllKeys()) {
                    int index = Integer.parseInt(sectionKey);
                    if (index < 0 || index >= level.getSectionsCount()) throw new IllegalArgumentException("Plot section out of bounds");
                    var section = sections.getCompound(sectionKey);
                    BLOCK_STATES.parse(NbtOps.INSTANCE, section.getCompound("block_states")).getOrThrow();
                    for (var light : new String[]{"BlockLight", "SkyLight"})
                        if (section.contains(light, Tag.TAG_BYTE_ARRAY) && section.getByteArray(light).length != 2048)
                            throw new IllegalArgumentException("Invalid plot light data");
                }
            }
        } catch (RuntimeException invalid) {
            throw new IllegalStateException("Invalid structural history: " + invalid.getMessage(), invalid);
        }
    }

    static CompoundTag prepare(CompoundTag original, BlockPos previousCenter, BlockPos newCenter) {
        // Apply the same consumed-rune policy used by ordinary entity/container restoration.
        CompoundTag data = RewindRuneManager.restorationNbt(original);
        BlockPos offset = newCenter.subtract(previousCenter);
        int plotWidth = 16 << data.getInt("log_size");
        data.putInt("plot_x", data.getInt("plot_x") + offset.getX() / plotWidth);
        data.putInt("plot_z", data.getInt("plot_z") + offset.getZ() / plotWidth);
        var chunks = data.getCompound("chunks");
        for (var key : chunks.getAllKeys()) {
            var chunk = chunks.getCompound(key);
            for (var listName : new String[]{"block_entities", "block_ticks", "fluid_ticks"}) {
                var list = chunk.getList(listName, Tag.TAG_COMPOUND);
                for (var item : list) {
                    var entry = (CompoundTag) item;
                    entry.putInt("x", entry.getInt("x") + offset.getX());
                    entry.putInt("y", entry.getInt("y") + offset.getY());
                    entry.putInt("z", entry.getInt("z") + offset.getZ());
                }
            }
        }
        return data;
    }

    static void restoreContents(dev.ryanhcode.sable.sublevel.ServerSubLevel body,
                             com.timestop.core.rewind.data.SubLevelSnapshot snapshot) {
        if (snapshot.plotData() == null || snapshot.plotCenter() == null) return;
        var plot = body.getPlot();
        var data = prepare(snapshot.plotData(), snapshot.plotCenter(), plot.getCenterBlock()).getCompound("chunks");
        var level = body.getLevel();
        for (var key : data.getAllKeys()) {
            var local = new ChunkPos(Long.parseLong(key));
            if (plot.getChunk(local) == null) plot.newEmptyChunk(plot.toGlobal(local));
        }
        record Change(BlockPos pos, BlockState state) {}
        var changes = new java.util.ArrayList<Change>();
        // Native assembly moves blocks through chunk access and bypasses vanilla Level.setBlock.
        // Reconcile the checkpoint as well as ordinary deltas; unchanged palettes are skipped.
        for (var holder : java.util.List.copyOf(plot.getLoadedChunks())) {
            var chunk = (net.minecraft.world.level.chunk.LevelChunk) holder.getChunk();
            var local = plot.toLocal(holder.getPos());
            var sections = data.getCompound(Long.toString(local.toLong())).getCompound("sections");
            for (int index = 0; index < chunk.getSectionsCount(); index++) {
                var section = chunk.getSection(index);
                var saved = sections.getCompound(Integer.toString(index));
                boolean hasTarget = saved.contains("block_states", Tag.TAG_COMPOUND);
                if (!hasTarget && section.hasOnlyAir()) continue;
                if (hasTarget && BLOCK_STATES.encodeStart(NbtOps.INSTANCE, section.getStates()).getOrThrow().equals(saved.getCompound("block_states"))) continue;
                var target = hasTarget ? BLOCK_STATES.parse(NbtOps.INSTANCE, saved.getCompound("block_states")).getOrThrow() : null;
                int yBase = chunk.getSectionYFromSectionIndex(index) << 4;
                for (int x = 0; x < 16; x++) for (int y = 0; y < 16; y++) for (int z = 0; z < 16; z++) {
                    var state = target == null ? Blocks.AIR.defaultBlockState() : target.get(x, y, z);
                    if (!section.getBlockState(x, y, z).equals(state)) changes.add(new Change(
                            new BlockPos(holder.getPos().getMinBlockX() + x, yBase + y, holder.getPos().getMinBlockZ() + z), state));
                }
            }
        }
        changes.sort(java.util.Comparator.comparing(change -> change.state().isAir()));
        for (var change : changes) {
            if (level.getBlockState(change.pos()).getBlock() != change.state().getBlock())
                net.minecraft.world.Clearable.tryClear(level.getBlockEntity(change.pos()));
            level.setBlock(change.pos(), change.state(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SUPPRESS_DROPS);
        }
        for (var holder : plot.getLoadedChunks()) {
            var chunk = (net.minecraft.world.level.chunk.LevelChunk) holder.getChunk();
            var pos = holder.getPos();
            var local = plot.toLocal(pos);
            var saved = data.getCompound(Long.toString(ChunkPos.asLong(local.x, local.z)));
            for (var raw : saved.getList("block_entities", Tag.TAG_COMPOUND)) {
                var tag = ((CompoundTag) raw).copy();
                tag.remove("keepPacked");
                var bePos = net.minecraft.world.level.block.entity.BlockEntity.getPosFromTag(tag);
                var live = level.getBlockEntity(bePos);
                if (live == null || !live.saveWithFullMetadata(level.registryAccess()).equals(tag)) {
                    if (!com.timestop.core.rewind.RewindExecutor.restoreBlockEntity(level, bePos, tag))
                        throw new IllegalStateException("Could not restore recorded plot block entity at " + bePos);
                }
            }
            // Clear queued forward ticks, including chunks added after the target frame.
            var blockQueue = (LevelChunkTicks<Block>) chunk.getBlockTicks();
            var fluidQueue = (LevelChunkTicks<net.minecraft.world.level.material.Fluid>) chunk.getFluidTicks();
            blockQueue.unpack(level.getGameTime());
            fluidQueue.unpack(level.getGameTime());
            blockQueue.removeIf(tick -> true);
            fluidQueue.removeIf(tick -> true);
            var blocks = LevelChunkTicks.load(saved.getList("block_ticks", Tag.TAG_COMPOUND),
                    id -> BuiltInRegistries.BLOCK.getOptional(ResourceLocation.tryParse(id)), pos);
            var fluids = LevelChunkTicks.load(saved.getList("fluid_ticks", Tag.TAG_COMPOUND),
                    id -> BuiltInRegistries.FLUID.getOptional(ResourceLocation.tryParse(id)), pos);
            blocks.unpack(level.getGameTime()); fluids.unpack(level.getGameTime());
            blocks.getAll().forEach(chunk.getBlockTicks()::schedule);
            fluids.getAll().forEach(chunk.getFluidTicks()::schedule);
        }
    }
}
