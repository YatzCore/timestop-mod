package com.timestop.pedestal;

import com.timestop.config.TimeStopConfig;
import com.timestop.core.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public final class PedestalWorldTime {
    public static double rate(ServerLevel level, BlockPos pos) {
        var bubble = TemporalBubbleManager.getDominantBubble(level.dimension(), Vec3.atCenterOf(pos));
        if (bubble == null) return 1;
        if (bubble.getMode() == TimeMode.TIME_STOP) return 0;
        if (!bubble.isStationary()) return 1;
        return switch (bubble.getMode()) {
            case FAST_FORWARD -> TimeStopConfig.COMMON.fastForwardRate.get();
            case SLOW_MOTION -> TimeStopConfig.COMMON.slowMotionRate.get();
            default -> 1;
        };
    }
    public static void randomBlock(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        double rate = rate(level,pos);
        int count = (int)rate + (random.nextDouble() < rate % 1 ? 1 : 0);
        for (int i=0; i<count; i++) {
            BlockState current = level.getBlockState(pos);
            if (!current.is(state.getBlock()) || !current.isRandomlyTicking()) break;
            current.randomTick(level,pos,random);
        }
    }
    private PedestalWorldTime() {}
}
