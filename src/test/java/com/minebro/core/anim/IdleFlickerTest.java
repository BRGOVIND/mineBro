package com.minebro.core.anim;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IdleFlickerTest {

    private static final long EPOCH = 1_000_000L;

    /** Sample the whole timeline at 1ms and pull out the start of every pulse. */
    private static List<Long> pulseStarts(long fromMillis, long toMillis) {
        List<Long> starts = new ArrayList<>();
        boolean wasLit = false;
        for (long t = fromMillis; t < toMillis; t++) {
            boolean lit = IdleFlicker.intensityAt(t, EPOCH) > 0.0f;
            if (lit && !wasLit) {
                starts.add(t);
            }
            wasLit = lit;
        }
        return starts;
    }

    @Test
    void intensityStaysInRangeEverywhere() {
        for (long t = EPOCH; t < EPOCH + 120_000; t += 3) {
            float v = IdleFlicker.intensityAt(t, EPOCH);
            assertTrue(v >= 0.0f && v <= 1.0f, "intensity left 0..1 at t=" + t + ": " + v);
        }
    }

    /** §3.1: 8-12s between pulses. Wider would feel dead, narrower would feel like a metronome. */
    @Test
    void gapsBetweenPulsesStayWithinTheEightToTwelveSecondWindow() {
        List<Long> starts = pulseStarts(EPOCH, EPOCH + 600_000);
        assertTrue(starts.size() > 40, "expected many pulses over 10 minutes, got " + starts.size());
        for (int i = 1; i < starts.size(); i++) {
            long gap = starts.get(i) - starts.get(i - 1);
            assertTrue(gap >= 8_000 && gap <= 12_000, "gap out of the 8-12s window: " + gap);
        }
    }

    /** The whole point of the jitter: consecutive gaps must not be identical. */
    @Test
    void gapsAreIrregularRatherThanAMetronome() {
        List<Long> starts = pulseStarts(EPOCH, EPOCH + 600_000);
        long firstGap = starts.get(1) - starts.get(0);
        boolean sawDifferentGap = false;
        for (int i = 2; i < starts.size(); i++) {
            if (starts.get(i) - starts.get(i - 1) != firstGap) {
                sawDifferentGap = true;
                break;
            }
        }
        assertTrue(sawDifferentGap, "every gap was identical - the jitter is not working");
    }

    @Test
    void eachPulseIsShortAndPeaksInTheMiddle() {
        List<Long> starts = pulseStarts(EPOCH, EPOCH + 60_000);
        long start = starts.get(0);

        long litSamples = 0;
        for (long t = start; IdleFlicker.intensityAt(t, EPOCH) > 0.0f; t++) {
            litSamples++;
        }
        // The 120ms window from §13.2, minus its two zero-valued endpoints: the sine is exactly 0
        // at both ends of the pulse, so only the interior samples read as lit.
        assertTrue(litSamples > 110 && litSamples <= 120, "pulse ran for " + litSamples + "ms");

        float peak = IdleFlicker.intensityAt(start + 60, EPOCH);
        assertTrue(peak > 0.99f, "pulse should peak mid-way, got " + peak);
        assertTrue(IdleFlicker.intensityAt(start + 5, EPOCH) < peak, "should ramp in");
        assertTrue(IdleFlicker.intensityAt(start + 115, EPOCH) < peak, "should ramp out");
    }

    /** Same clock in, same schedule out - the renderer may sample at any rate. */
    @Test
    void theScheduleIsDeterministic() {
        for (long t = EPOCH; t < EPOCH + 40_000; t += 17) {
            assertEquals(IdleFlicker.intensityAt(t, EPOCH), IdleFlicker.intensityAt(t, EPOCH));
        }
    }

    @Test
    void jitterStaysWithinItsCeilingIncludingNegativeSlots() {
        for (long slot = -50; slot < 50; slot++) {
            long jitter = IdleFlicker.jitterFor(slot);
            assertTrue(jitter >= 0 && jitter <= 2_000, "jitter out of range for slot " + slot);
        }
    }

    @Test
    void aClockBeforeTheEpochStillBehaves() {
        for (long t = EPOCH - 30_000; t < EPOCH; t += 11) {
            float v = IdleFlicker.intensityAt(t, EPOCH);
            assertTrue(v >= 0.0f && v <= 1.0f, "intensity left 0..1 before the epoch at t=" + t);
        }
    }
}
