package com.timestop.core.rewind;

public final class BlockRebuildMotion {
    public static final int DURATION = 12;
    public static final int MAX_BLOCKS = 48;
    private BlockRebuildMotion() {}
    public static float progress(float age) { return Math.max(0, Math.min(1, age / DURATION)); }
    public static float converge(float progress) { return 1 - (float) Math.pow(1 - progress, 3); }
    public static float coreAlpha(float progress) { return Math.max(0, Math.min(1, (progress - 0.35F) / 0.5F)); }
    public static float fragmentAlpha(float progress) { return (1 - coreAlpha(progress)) * Math.min(1, progress * 8); }
}
