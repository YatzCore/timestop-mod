package com.timestop.client;

import com.timestop.core.ClientTimeStopManager;
import com.timestop.core.TimeMode;

public class WeatherFreezeManager {

    private static double accumulatedRainTime = 0.0;
    private static long lastNanoTime = 0;
    private static boolean initialized = false;

    public static void update(int actualTicks, float partialTick) {
        long now = System.nanoTime();
        if (!initialized) {
            accumulatedRainTime = actualTicks + partialTick;
            lastNanoTime = now;
            initialized = true;
            return;
        }

        double dtSeconds = (now - lastNanoTime) / 1_000_000_000.0;
        lastNanoTime = now;
        if (dtSeconds < 0.0) dtSeconds = 0.0;
        if (dtSeconds > 0.1) dtSeconds = 0.1;

        if (!ClientTimeStopManager.isTimeStopped()) {
            accumulatedRainTime = actualTicks + partialTick;
        } else {
            TimeMode mode = ClientTimeStopManager.getCurrentMode();
            switch (mode) {
                case TIME_STOP:
                    // 100% frozen in space - zero advance
                    break;
                case SLOW_MOTION:
                case MATRIX:
                    // Cinematic 0.2x speed falling rain
                    accumulatedRainTime += dtSeconds * 20.0 * 0.20;
                    break;
                case SUPERHOT:
                    // Rain falls only when player moves
                    float activity = ClientTimeStopManager.getSuperhotActivity();
                    accumulatedRainTime += dtSeconds * 20.0 * (0.05 + activity * 0.95);
                    break;
                case FAST_FORWARD:
                    accumulatedRainTime += dtSeconds * 20.0 * 4.0;
                    break;
                default:
                    accumulatedRainTime += dtSeconds * 20.0;
                    break;
            }
        }
    }

    public static int getEffectiveTicks(int actualTicks) {
        if (!ClientTimeStopManager.isTimeStopped()) {
            return actualTicks;
        }
        return (int) Math.floor(accumulatedRainTime);
    }

    public static float getEffectivePartialTick(float actualPartialTick) {
        if (!ClientTimeStopManager.isTimeStopped()) {
            return actualPartialTick;
        }
        return (float) (accumulatedRainTime - Math.floor(accumulatedRainTime));
    }

    public static void reset() {
        initialized = false;
        accumulatedRainTime = 0.0;
        lastNanoTime = 0;
    }
}
