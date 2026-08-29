package com.minebro.core.anim;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AnimClockTest {

    private static final float EPS = 1e-5f;

    @Test
    void progressRunsFromZeroToOneAcrossTheDuration() {
        assertEquals(0.0f, AnimClock.progress(1000, 1000, 200), EPS);
        assertEquals(0.5f, AnimClock.progress(1100, 1000, 200), EPS);
        assertEquals(1.0f, AnimClock.progress(1200, 1000, 200), EPS);
    }

    @Test
    void progressStaysClampedBeforeTheStartAndLongAfterTheEnd() {
        assertEquals(0.0f, AnimClock.progress(500, 1000, 200), EPS);
        assertEquals(1.0f, AnimClock.progress(999_999, 1000, 200), EPS);
    }

    /** A zeroed duration is how a caller disables an animation; it must settle, not divide by zero. */
    @Test
    void nonPositiveDurationReportsFinishedInsteadOfNaN() {
        assertEquals(1.0f, AnimClock.progress(1000, 1000, 0), EPS);
        assertEquals(1.0f, AnimClock.progress(1000, 1000, -5), EPS);
    }

    @Test
    void runningIsTrueOnlyInsideTheWindow() {
        assertTrue(AnimClock.running(1050, 1000, 200));
        assertFalse(AnimClock.running(1200, 1000, 200));
        assertFalse(AnimClock.running(1500, 1000, 200));
    }

    /** A never-started timeline is stamped 0 and must not read as "running since the epoch". */
    @Test
    void runningIsFalseForATimelineThatNeverStarted() {
        assertFalse(AnimClock.running(1050, 0, 200));
    }

    @Test
    void loopPhaseWrapsAroundTheCycle() {
        assertEquals(0.0f, AnimClock.loopPhase(0, 0, 700), EPS);
        assertEquals(0.5f, AnimClock.loopPhase(350, 0, 700), EPS);
        assertEquals(0.0f, AnimClock.loopPhase(700, 0, 700), EPS);
        assertEquals(0.5f, AnimClock.loopPhase(1050, 0, 700), EPS);
    }

    /** A backwards clock step must wrap forward, or the orbit dot jumps to the wrong side. */
    @Test
    void loopPhaseStaysPositiveForNegativeElapsed() {
        float phase = AnimClock.loopPhase(-350, 0, 700);
        assertTrue(phase >= 0.0f && phase < 1.0f, "phase went negative: " + phase);
        assertEquals(0.5f, phase, EPS);
    }

    @Test
    void loopPhaseHandlesADegenerateZeroPeriod() {
        assertEquals(0.0f, AnimClock.loopPhase(123, 0, 0), EPS);
    }

    /** THINKING: 3 rune positions over a 900ms cycle (§13.2). */
    @Test
    void stepIndexSnapsToWholePositionsAndWraps() {
        assertEquals(0, AnimClock.stepIndex(0, 0, 900, 3));
        assertEquals(0, AnimClock.stepIndex(299, 0, 900, 3));
        assertEquals(1, AnimClock.stepIndex(300, 0, 900, 3));
        assertEquals(2, AnimClock.stepIndex(600, 0, 900, 3));
        assertEquals(2, AnimClock.stepIndex(899, 0, 900, 3));
        assertEquals(0, AnimClock.stepIndex(900, 0, 900, 3));
    }

    @Test
    void stepIndexNeverEscapesTheStepCount() {
        for (long t = 0; t < 5000; t += 7) {
            int index = AnimClock.stepIndex(t, 0, 900, 3);
            assertTrue(index >= 0 && index < 3, "step out of range at t=" + t + ": " + index);
        }
    }

    @Test
    void stepIndexIsZeroForADegenerateStepCount() {
        assertEquals(0, AnimClock.stepIndex(500, 0, 900, 1));
        assertEquals(0, AnimClock.stepIndex(500, 0, 900, 0));
    }
}
