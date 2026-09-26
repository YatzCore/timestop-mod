package com.timestop.client.renderer;

import com.timestop.pedestal.PedestalBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import java.util.WeakHashMap;

/** Presentation time is independent of world ticks and the mod's time dilation. */
public final class ArmillaryAnimation {
    private static final WeakHashMap<PedestalBlockEntity, State> STATES = new WeakHashMap<>();
    private static ClientLevel level;
    private static long lastNanos;
    private static double seconds;
    private ArmillaryAnimation() {}

    public static void frame() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != level) { clear(); level = mc.level; }
        long now = System.nanoTime();
        if (lastNanos != 0 && level != null && !mc.isPaused())
            seconds += Math.min(.1, (now - lastNanos) / 1_000_000_000.0);
        lastNanos = now;
        STATES.keySet().removeIf(p -> p.isRemoved() || p.getLevel() != level
                || !level.hasChunkAt(p.getBlockPos()) || level.getBlockEntity(p.getBlockPos()) != p);
    }

    public static double seconds() { return seconds; }
    public static int trackedCount() { return STATES.size(); }
    public static void clear() { STATES.clear(); level = null; seconds = 0; lastNanos = 0; }

    public static double phase(PedestalBlockEntity p) {
        return Math.floorMod(p.getBlockPos().asLong() * 31 + p.getBlockPos().hashCode(), 4096) / 4096.0 * 360;
    }

    public static double watchY(PedestalBlockEntity p) {
        return ArmillaryGeometry.forTier(p.pedestalTier()).centerY()
                + Math.sin(seconds * .9 + Math.toRadians(phase(p))) * .01;
    }

    public static State state(PedestalBlockEntity p) {
        State state = STATES.computeIfAbsent(p, key -> new State(seconds, phase(p), !p.getWatch().isEmpty(), p.isActive()));
        state.advance(seconds, !p.getWatch().isEmpty(), p.isActive());
        return state;
    }

    public static final class State {
        public final double[] angles = new double[3];
        public double watchAngle, glow, speed;
        private double last;
        private State(double time, double phase, boolean occupied, boolean active) {
            last = time; watchAngle = phase;
            if (occupied) for (int i=0; i<3; i++) angles[i] = phase * (i+1);
            speed = occupied ? (active ? 3 : 1) : 0; glow = active ? 1 : 0;
        }
        private void advance(double time, boolean occupied, boolean active) {
            double dt = Math.min(.1, Math.max(0, time-last)); last = time;
            double blend = 1-Math.exp(-dt*4);
            speed += ((occupied ? (active ? 3 : 1) : 0)-speed)*blend;
            glow += ((active && occupied ? 1 : 0)-glow)*blend;
            watchAngle = (watchAngle + dt*30*speed) % 360;
            for (int i=0; i<3; i++) {
                if (occupied) angles[i] = (angles[i]+dt*speed*(i==0 ? 12 : i==1 ? -17 : 23)) % 360;
                else {
                    double delta = Math.IEEEremainder(-angles[i], 360);
                    angles[i] += delta*(1-Math.exp(-dt*2));
                }
            }
        }
    }
}
