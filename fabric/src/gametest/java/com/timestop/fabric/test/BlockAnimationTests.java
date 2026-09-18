package com.timestop.fabric.test;

import com.timestop.core.rewind.BlockRebuildMotion;
import com.timestop.network.RewindBlocksPacket;
import io.netty.buffer.Unpooled;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.level.block.Blocks;
import java.util.List;

public class BlockAnimationTests implements FabricGameTest {
    @GameTest(template = EMPTY_STRUCTURE, batch = "block_animation")
    public void packetPreservesMaterialAndPositionAndRejectsOversizedBatches(GameTestHelper h) {
        var packet = new RewindBlocksPacket(h.getLevel().dimension().location(), List.of(
                new RewindBlocksPacket.Entry(new BlockPos(-20, 65, 31), Blocks.OAK_LOG.defaultBlockState()),
                new RewindBlocksPacket.Entry(new BlockPos(3, -12, 40), Blocks.GLASS.defaultBlockState())));
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            packet.toBytes(buffer);
            var decoded = new RewindBlocksPacket(buffer);
            h.assertTrue(decoded.equals(packet), "Animation packet must preserve the exact restored block materials and coordinates");
            buffer.clear();
            buffer.writeResourceLocation(packet.dimension());
            buffer.writeVarInt(BlockRebuildMotion.MAX_BLOCKS + 1);
            boolean rejected = false;
            try { new RewindBlocksPacket(buffer); } catch (IllegalArgumentException expected) { rejected = true; }
            h.assertTrue(rejected, "Oversized animation batches must be rejected before allocation");
        } finally { buffer.release(); }
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE, batch = "block_animation")
    public void reconstructionConvergesAndFinishesOpaque(GameTestHelper h) {
        float previous = 0;
        for (int frame = 0; frame <= 120; frame++) {
            float progress = BlockRebuildMotion.progress(frame / 10F);
            float inward = BlockRebuildMotion.converge(progress);
            h.assertTrue(inward >= previous && inward <= 1, "Fragments must move inward without overshooting");
            h.assertTrue(BlockRebuildMotion.coreAlpha(progress) >= 0 && BlockRebuildMotion.coreAlpha(progress) <= 1, "Opacity must stay within bounds");
            previous = inward;
        }
        h.assertTrue(BlockRebuildMotion.coreAlpha(1) == 1 && BlockRebuildMotion.fragmentAlpha(1) == 0,
                "Animation must finish with one opaque full block and no leftover fragments");
        h.assertTrue(BlockRebuildMotion.progress(100) == 1, "A late frame must settle rather than overshoot");
        h.succeed();
    }
}
