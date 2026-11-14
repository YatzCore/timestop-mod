package com.timestop.compat.sable;

/**
 * Counts fixed physics quanta without tying simulated time to render rate or server lag.
 * The caller supplies the deliberate server tick interval, not measured wall-clock time.
 * Never spends more than its per-call budget; outstanding time is retained for later calls.
 */
public final class PhysicsStepCredits {
    private double pendingSeconds;

    public int advance(double rate, double serverIntervalSeconds, double quantumSeconds, int maxSteps) {
        if (!Double.isFinite(rate) || rate < 0 || !Double.isFinite(serverIntervalSeconds)
                || serverIntervalSeconds <= 0 || !Double.isFinite(quantumSeconds)
                || quantumSeconds <= 0 || maxSteps < 0) {
            throw new IllegalArgumentException("Invalid physics clock parameters");
        }
        double credit = rate * serverIntervalSeconds;
        if (!Double.isFinite(credit) || !Double.isFinite(pendingSeconds + credit)) {
            throw new IllegalArgumentException("Physics clock overflow");
        }
        // Stasis holds outstanding credits. It never advances to catch up while frozen.
        if (rate == 0) return 0;
        pendingSeconds += credit;
        int steps = (int) Math.min(maxSteps, Math.floor((pendingSeconds + quantumSeconds * 1e-9) / quantumSeconds));
        pendingSeconds = Math.max(0, pendingSeconds - steps * quantumSeconds);
        return steps;
    }

    public double pendingSeconds() { return pendingSeconds; }

    public void restore(double seconds) {
        if (!Double.isFinite(seconds) || seconds < 0) throw new IllegalArgumentException("Invalid physics clock state");
        pendingSeconds = seconds;
    }
}
