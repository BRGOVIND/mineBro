package com.minebro.core.anim;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EasingTest {

    private static final float EPS = 1e-5f;

    @Test
    void everyCurveIsPinnedAtBothEndpoints() {
        assertEquals(0.0f, Easing.linear(0.0f), EPS);
        assertEquals(1.0f, Easing.linear(1.0f), EPS);
        assertEquals(0.0f, Easing.easeIn(0.0f), EPS);
        assertEquals(1.0f, Easing.easeIn(1.0f), EPS);
        assertEquals(0.0f, Easing.easeOut(0.0f), EPS);
        assertEquals(1.0f, Easing.easeOut(1.0f), EPS);
        assertEquals(0.0f, Easing.sineInOut(0.0f), EPS);
        assertEquals(1.0f, Easing.sineInOut(1.0f), EPS);
    }

    @Test
    void inputIsClampedSoAnOverrunSettlesInsteadOfOvershooting() {
        assertEquals(0.0f, Easing.easeOut(-3.0f), EPS);
        assertEquals(1.0f, Easing.easeOut(4.0f), EPS);
        assertEquals(0.0f, Easing.sineInOut(-0.001f), EPS);
        assertEquals(1.0f, Easing.sineInOut(99.0f), EPS);
    }

    /** §13.1 bans overshoot curves outright, so no curve may ever leave 0..1. */
    @Test
    void noCurveOvershootsItsRange() {
        for (int i = 0; i <= 100; i++) {
            float t = i / 100.0f;
            for (float v : new float[] {
                    Easing.linear(t), Easing.easeIn(t), Easing.easeOut(t), Easing.sineInOut(t)}) {
                assertTrue(v >= 0.0f && v <= 1.0f, "curve left 0..1 at t=" + t + ": " + v);
            }
        }
    }

    @Test
    void everyCurveIsMonotonicallyIncreasing() {
        float prevIn = -1, prevOut = -1, prevSine = -1;
        for (int i = 0; i <= 100; i++) {
            float t = i / 100.0f;
            float in = Easing.easeIn(t);
            float out = Easing.easeOut(t);
            float sine = Easing.sineInOut(t);
            assertTrue(in >= prevIn, "easeIn dipped at t=" + t);
            assertTrue(out >= prevOut, "easeOut dipped at t=" + t);
            assertTrue(sine >= prevSine, "sineInOut dipped at t=" + t);
            prevIn = in;
            prevOut = out;
            prevSine = sine;
        }
    }

    @Test
    void easeOutLeadsLinearAndEaseInTrailsIt() {
        float t = 0.25f;
        assertTrue(Easing.easeOut(t) > Easing.linear(t), "easeOut should start fast");
        assertTrue(Easing.easeIn(t) < Easing.linear(t), "easeIn should start slow");
    }

    @Test
    void nanProgressDegradesToTheStartRatherThanPropagating() {
        assertEquals(0.0f, Easing.clamp01(Float.NaN), EPS);
    }

    @Test
    void lerpClampsSoAnOverrunCannotPassTheTarget() {
        assertEquals(10.0f, Easing.lerp(0.0f, 10.0f, 1.0f), EPS);
        assertEquals(10.0f, Easing.lerp(0.0f, 10.0f, 2.5f), EPS);
        assertEquals(0.0f, Easing.lerp(0.0f, 10.0f, -1.0f), EPS);
        assertEquals(5.0f, Easing.lerp(0.0f, 10.0f, 0.5f), EPS);
    }
}
