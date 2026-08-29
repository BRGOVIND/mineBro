package com.minebro.core.anim;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ColorLerpTest {

    private static final int AMBER = 0xFFE8A93C;
    private static final int GREEN = 0xFF6FCB6A;

    @Test
    void blendReturnsTheEndpointsExactly() {
        assertEquals(AMBER, ColorLerp.blend(AMBER, GREEN, 0.0f));
        assertEquals(GREEN, ColorLerp.blend(AMBER, GREEN, 1.0f));
    }

    @Test
    void blendClampsBeyondTheEndpoints() {
        assertEquals(AMBER, ColorLerp.blend(AMBER, GREEN, -2.0f));
        assertEquals(GREEN, ColorLerp.blend(AMBER, GREEN, 3.0f));
    }

    @Test
    void blendMovesEachChannelTowardTheTarget() {
        int mid = ColorLerp.blend(0xFF000000, 0xFFFFFFFF, 0.5f);
        assertEquals(0xFF, (mid >>> 24) & 0xFF, "alpha should be carried through");
        assertEquals(128, (mid >>> 16) & 0xFF);
        assertEquals(128, (mid >>> 8) & 0xFF);
        assertEquals(128, mid & 0xFF);
    }

    @Test
    void blendInterpolatesAlphaToo() {
        int faded = ColorLerp.blend(0x00E8A93C, 0xFFE8A93C, 0.5f);
        assertEquals(128, (faded >>> 24) & 0xFF);
    }

    @Test
    void blendIsAHardCutOnlyAtTheEndpoints() {
        int quarter = ColorLerp.blend(AMBER, GREEN, 0.25f);
        assertNotEquals(AMBER, quarter);
        assertNotEquals(GREEN, quarter);
    }

    @Test
    void brightenLiftsRgbTowardWhite() {
        int lit = ColorLerp.brighten(AMBER, 0.5f);
        assertTrue(((lit >>> 16) & 0xFF) > ((AMBER >>> 16) & 0xFF));
        assertTrue(((lit >>> 8) & 0xFF) > ((AMBER >>> 8) & 0xFF));
        assertTrue((lit & 0xFF) > (AMBER & 0xFF));
    }

    @Test
    void brightenByZeroIsANoOp() {
        assertEquals(AMBER, ColorLerp.brighten(AMBER, 0.0f));
    }

    @Test
    void brightenFullyReachesWhiteWithoutTouchingAlpha() {
        assertEquals(0xFFFFFFFF, ColorLerp.brighten(AMBER, 1.0f));
    }

    /** Brightening a dimmed OFFLINE glyph must not quietly make it opaque again. */
    @Test
    void brightenPreservesAlphaOnATranslucentColour() {
        int translucent = 0x59E8A93C;
        int lit = ColorLerp.brighten(translucent, 0.8f);
        assertEquals(0x59, (lit >>> 24) & 0xFF);
    }

    @Test
    void withAlphaScaleDimsAlphaAndLeavesRgbAlone() {
        int dimmed = ColorLerp.withAlphaScale(AMBER, 0.35f);
        assertEquals(Math.round(0xFF * 0.35f), (dimmed >>> 24) & 0xFF);
        assertEquals(AMBER & 0x00FFFFFF, dimmed & 0x00FFFFFF);
    }

    @Test
    void withAlphaScaleClampsItsFactor() {
        assertEquals(0xFF, (ColorLerp.withAlphaScale(AMBER, 5.0f) >>> 24) & 0xFF);
        assertEquals(0x00, (ColorLerp.withAlphaScale(AMBER, -1.0f) >>> 24) & 0xFF);
    }
}
