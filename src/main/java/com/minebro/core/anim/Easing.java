package com.minebro.core.anim;

/**
 * The only three easing families the design doc allows (DESIGN.md §13.1): {@code linear} for
 * mechanical motion, {@code sine} in/out for ambient motion, and plain stepped transitions
 * (which need no curve at all - see {@link AnimClock#stepIndex}). Overshoot curves
 * (back/elastic/bounce) are deliberately absent: §2.3's "not childish" rule bans them outright,
 * so there is no method here to reach for by accident.
 *
 * <p>Every method takes and returns a normalized 0..1 progress value and clamps its input, so
 * callers can pass a raw elapsed ratio without guarding the endpoints themselves.
 */
public final class Easing {

    public static float linear(float t) {
        return clamp01(t);
    }

    /** Decelerating - the default for one-shot entries (flash-in, crossfade-in, panel open). */
    public static float easeOut(float t) {
        float x = clamp01(t);
        return 1.0f - (1.0f - x) * (1.0f - x);
    }

    /** Accelerating - used for exits (crossfade back to IDLE, panel close). */
    public static float easeIn(float t) {
        float x = clamp01(t);
        return x * x;
    }

    /** Symmetric organic curve - the idle "candle flicker" and state colour crossfades. */
    public static float sineInOut(float t) {
        float x = clamp01(t);
        return (float) (0.5 - 0.5 * Math.cos(Math.PI * x));
    }

    public static float clamp01(float t) {
        if (Float.isNaN(t)) {
            return 0.0f;
        }
        return t < 0.0f ? 0.0f : (t > 1.0f ? 1.0f : t);
    }

    /** Linear interpolation, clamped on {@code t} so an overrun can't push past the target. */
    public static float lerp(float from, float to, float t) {
        return from + (to - from) * clamp01(t);
    }

    private Easing() {}
}
