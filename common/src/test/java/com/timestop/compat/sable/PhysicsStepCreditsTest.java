package com.timestop.compat.sable;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PhysicsStepCreditsTest {
    @Test
    void requestedTimeIsConservedAcrossRatesAndServerPacing() {
        double quantum = .005;
        for (double rate : new double[]{.01, .05, .25, 1, 5, 50}) {
            for (double interval : new double[]{.01, .05, .2, 1}) {
                var clock = new PhysicsStepCredits();
                long steps = 0;
                for (int tick = 0; tick < 10000; tick++) steps += clock.advance(rate, interval, quantum, 10001);
                double requested = 10000 * rate * interval;
                assertEquals(requested, steps * quantum + clock.pendingSeconds(), 1e-7);
                assertTrue(clock.pendingSeconds() < quantum + 1e-9);
            }
        }
    }

    @Test
    void globalSlowMotionDoesNotApplyTheRateTwice() {
        var clock = new PhysicsStepCredits();
        assertEquals(10, clock.advance(.25, .2, .005, 100));
        assertEquals(10, clock.advance(5, .01, .005, 100));
    }

    @Test
    void freezeDoesNotSpendBacklogOrAccumulateElapsedTime() {
        var clock = new PhysicsStepCredits();
        assertEquals(2, clock.advance(50, .05, .005, 2));
        double debt = clock.pendingSeconds();
        for (int tick = 0; tick < 1000; tick++) assertEquals(0, clock.advance(0, 1, .005, 100));
        assertEquals(debt, clock.pendingSeconds());
    }

    @Test
    void workBudgetRetainsUnspentTimeAndSnapshotResumesExactly() {
        var clock = new PhysicsStepCredits();
        assertEquals(3, clock.advance(5, .05, .005, 3));
        assertEquals(.235, clock.pendingSeconds(), 1e-12);
        var restored = new PhysicsStepCredits();
        restored.restore(clock.pendingSeconds());
        for (int i = 0; i < 100; i++) {
            assertEquals(clock.advance(.25, .05, .005, 3), restored.advance(.25, .05, .005, 3));
            assertEquals(clock.pendingSeconds(), restored.pendingSeconds());
        }
    }

    @Test
    void invalidValuesCannotPoisonTheClock() {
        var clock = new PhysicsStepCredits();
        assertThrows(IllegalArgumentException.class, () -> clock.advance(Double.NaN, .05, .005, 10));
        assertThrows(IllegalArgumentException.class, () -> clock.advance(-1, .05, .005, 10));
        assertThrows(IllegalArgumentException.class, () -> clock.advance(1, .05, 0, 10));
        assertThrows(IllegalArgumentException.class, () -> clock.restore(Double.POSITIVE_INFINITY));
        assertEquals(10, clock.advance(1, .05, .005, 10));
    }
}
