package com.minebro.core.anim;

/**
 * Wall-clock helpers for driving animations from a render loop.
 *
 * <p>Everything here is a pure function of "what time is it now", never of "how many frames have
 * passed". The HUD renders once per frame at whatever rate the player's machine manages, so
 * frame-counted animation would run at different speeds on different hardware; the design doc's
 * timing table (DESIGN.md §13.2) is written in milliseconds and is only honoured if the clock is
 * too. This also matches how {@code HudAvatarController} already measures its own state decay.
 */
public final class AnimClock {

    /**
     * Normalized 0..1 progress through a one-shot animation, clamped at both ends. A non-positive
     * duration returns 1 (already finished) rather than dividing by zero, so a caller that disables
     * an animation by zeroing its duration gets the settled end state instead of a NaN.
     */
    public static float progress(long nowMillis, long startMillis, long durationMillis) {
        if (durationMillis <= 0) {
            return 1.0f;
        }
        long elapsed = nowMillis - startMillis;
        if (elapsed <= 0) {
            return 0.0f;
        }
        return Easing.clamp01((float) elapsed / (float) durationMillis);
    }

    /** True while a one-shot animation started at {@code startMillis} is still running. */
    public static boolean running(long nowMillis, long startMillis, long durationMillis) {
        return startMillis > 0 && nowMillis - startMillis < durationMillis;
    }

    /**
     * Position within a repeating cycle as 0..1. Negative elapsed values (a timeline stamped in the
     * future, or a clock that stepped backwards) wrap forward rather than returning a negative
     * phase, so an orbit never jumps to the wrong side of the glyph.
     */
    public static float loopPhase(long nowMillis, long epochMillis, long periodMillis) {
        if (periodMillis <= 0) {
            return 0.0f;
        }
        long into = Math.floorMod(nowMillis - epochMillis, periodMillis);
        return (float) into / (float) periodMillis;
    }

    /**
     * Which discrete step of {@code steps} a cycle is currently on. This is what makes THINKING's
     * rune rotation read as "processing" rather than "spinning" (§13.1): the caller snaps to whole
     * positions and never interpolates between them.
     */
    public static int stepIndex(long nowMillis, long epochMillis, long periodMillis, int steps) {
        if (steps <= 1) {
            return 0;
        }
        int index = (int) (loopPhase(nowMillis, epochMillis, periodMillis) * steps);
        return index >= steps ? steps - 1 : index;
    }

    private AnimClock() {}
}
