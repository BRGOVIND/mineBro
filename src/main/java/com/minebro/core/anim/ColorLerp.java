package com.minebro.core.anim;

/**
 * ARGB blending for the avatar's state transitions. DESIGN.md §3.1 requires every state colour
 * change to be a crossfade with a 150ms floor, "never a hard cut" - at a 16px sprite size a hard
 * swap at 60fps reads as a glitch rather than as a state change.
 *
 * <p>Colours are packed 0xAARRGGBB ints, the format {@code GuiGraphics#fill} takes directly.
 */
public final class ColorLerp {

    /** Blends {@code from} toward {@code to}, channel-wise, including alpha. */
    public static int blend(int from, int to, float t) {
        float x = Easing.clamp01(t);
        int a = channel(from, 24, to, x);
        int r = channel(from, 16, to, x);
        int g = channel(from, 8, to, x);
        int b = channel(from, 0, to, x);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    /**
     * Lifts RGB toward white by {@code amount} while leaving alpha untouched - the "glow" stand-in
     * for a sprite that has no separate glow layer. Alpha is deliberately preserved: brightening a
     * translucent OFFLINE glyph must not quietly make it opaque again.
     */
    public static int brighten(int argb, float amount) {
        int alpha = (argb >>> 24) & 0xFF;
        int opaqueWhite = 0xFFFFFFFF;
        int lifted = blend(argb | 0xFF000000, opaqueWhite, amount);
        return (alpha << 24) | (lifted & 0x00FFFFFF);
    }

    /** Scales alpha by {@code factor}, leaving RGB untouched (OFFLINE's fade to 35%). */
    public static int withAlphaScale(int argb, float factor) {
        int alpha = Math.round(((argb >>> 24) & 0xFF) * Easing.clamp01(factor));
        return (alpha << 24) | (argb & 0x00FFFFFF);
    }

    private static int channel(int from, int shift, int to, float t) {
        int a = (from >>> shift) & 0xFF;
        int b = (to >>> shift) & 0xFF;
        return Math.round(a + (b - a) * t);
    }

    private ColorLerp() {}
}
