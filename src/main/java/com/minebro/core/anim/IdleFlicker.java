package com.minebro.core.anim;

/**
 * The IDLE "candle flicker" (DESIGN.md §3.1): a single 120ms brightness pulse every 8-12 seconds,
 * randomized per cycle so it never feels like a metronome. Explicitly not a continuous breathing
 * loop - the doc's reasoning is that breathing reads as "waiting for you", which is the wrong
 * signal for a tool meant to feel present but not needy.
 *
 * <p>The randomization is a deterministic hash of the cycle index rather than a {@link
 * java.util.Random} field, which buys two things: this class stays a pure function of the clock
 * (so it is unit-testable, per build.gradle's L1 rule), and the schedule cannot drift or be
 * disturbed by how often the renderer happens to sample it.
 *
 * <p>Cycles are a fixed {@value #SLOT_MILLIS}ms slot with a 0-{@value #MAX_JITTER_MILLIS}ms jitter
 * inside each. That is what produces the 8-12s spacing the spec asks for: consecutive gaps are
 * {@code SLOT + jitter(n+1) - jitter(n)}, which spans exactly 8000-12000ms. The jitter ceiling
 * also keeps a pulse from ever straddling a slot boundary, so only one slot needs checking.
 */
public final class IdleFlicker {

    private static final long SLOT_MILLIS = 10_000;
    private static final long MAX_JITTER_MILLIS = 2_000;
    private static final long PULSE_MILLIS = 120;

    /**
     * Flicker intensity at {@code now}, 0 for "resting" up to 1 at the peak of a pulse. Callers map
     * this onto the §3.1 glow range (60% -> 100% -> 60%); returning a normalized value keeps the
     * curve here and the palette decision at the call site.
     */
    public static float intensityAt(long nowMillis, long epochMillis) {
        long slot = Math.floorDiv(nowMillis - epochMillis, SLOT_MILLIS);
        long start = epochMillis + slot * SLOT_MILLIS + jitterFor(slot);
        long into = nowMillis - start;
        if (into < 0 || into >= PULSE_MILLIS) {
            return 0.0f;
        }
        // 0 -> 1 -> 0 over the pulse, sine-shaped per §13.2's "sine in-out" entry.
        return (float) Math.sin(Math.PI * ((double) into / PULSE_MILLIS));
    }

    /** Deterministic 0..MAX_JITTER_MILLIS offset for a cycle. Knuth multiplicative mix. */
    static long jitterFor(long slot) {
        long mixed = slot * 2654435761L;
        mixed ^= (mixed >>> 17);
        return Math.floorMod(mixed, MAX_JITTER_MILLIS + 1);
    }

    private IdleFlicker() {}
}
