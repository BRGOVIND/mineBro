package com.minebro.client.hud;

import com.minebro.core.anim.AnimClock;
import com.minebro.core.anim.ColorLerp;
import com.minebro.core.anim.Easing;
import com.minebro.core.anim.IdleFlicker;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Draws the avatar: the rune-orb glyph from DESIGN.md §2.1 plus every per-state animation in
 * §3.1/§13.2. One shared static routine, per §15.2 - the HUD anchors it now, and an in-screen
 * badge can call the same method later without duplicating the drawing.
 *
 * <p><b>Why this is procedural and ships no PNG.</b> §3.4 recommends resolving Phase 1's decision
 * D4 as a sprite atlas, reasoning that the rotating rune, orbit dot, and shake "are not achievable
 * with a single monospace text glyph." That is true of a <em>font glyph</em>, but not of drawing:
 * the octagon, the rune strokes, and the orbit dot are all small axis-aligned rectangles, so
 * {@code fill} draws them directly - the same call the flat placeholder badge already used. This
 * gets the full animation set without a sprite-sheet and JSON-sidecar pipeline, and it stays a
 * one-method swap if the atlas is produced later.
 *
 * <p>All motion here is gated on {@code reducedMotion} (§12.5), which suppresses looping,
 * oscillating and translating motion only. Colour crossfades deliberately survive the flag: the
 * doc classes them as low-vestibular-impact, and they are carrying state information.
 */
public final class AvatarSprite {

    public static final int TILE = 16;

    // §1.3 accent palette.
    private static final int OUTLINE = 0xFF101010;
    private static final int BRAND = 0xFFE8A93C;
    private static final int COGNITION = 0xFF4FD8D0;
    private static final int RESPOND = 0xFFFFC96B;
    private static final int POSITIVE = 0xFF6FCB6A;
    private static final int NEGATIVE = 0xFFE05555;
    private static final int NEUTRAL_OFF = 0xFF6B6B6B;

    // §13.2 timing table.
    private static final long RUNE_CYCLE_MILLIS = 900;
    private static final int RUNE_STEPS = 3;
    /**
     * How far the rune turns per step. The three strokes sit 120 degrees apart, so a third of that
     * per step means the full 900ms cycle advances them exactly onto where the next stroke started
     * - the rotation reads as continuous even though every frame snaps to one of three positions.
     */
    private static final double RUNE_STEP_DEGREES = 360.0 / RUNE_STEPS / RUNE_STEPS;
    private static final long ORBIT_CYCLE_MILLIS = 700;
    private static final long RESPOND_FLASH_MILLIS = 250;
    private static final long SHAKE_MILLIS = 150;
    private static final int SHAKE_CYCLES = 3;
    private static final long OFFLINE_FADE_MILLIS = 400;

    private static final float OFFLINE_ALPHA = 0.35f;
    private static final float BLOOM_SCALE = 0.25f;

    /**
     * Row indents for the glyph, as a count of pixels inset from each side. Two nested silhouettes:
     * the outline is 14px across with 4px corner cuts, the body 12px with 3px cuts, so the body
     * sits exactly 1px inside the outline on every edge and the octagon reads cleanly at 16px.
     */
    private static final int[] OUTLINE_ROWS = {4, 3, 2, 1, 0, 0, 0, 0, 0, 0, 1, 2, 3, 4};
    private static final int[] BODY_ROWS = {3, 2, 1, 0, 0, 0, 0, 0, 0, 1, 2, 3};

    /**
     * @param x  left edge of the 16x16 tile
     * @param y  top edge of the 16x16 tile
     * @param scale caller-supplied scale; the wake bloom multiplies on top of it
     */
    public static void draw(GuiGraphics graphics, int x, int y, float scale, AvatarState state,
                            AvatarAnimation anim, long now, boolean reducedMotion) {
        anim.observe(state, now);

        int body = bodyColour(state, anim, now, reducedMotion);
        float bloom = reducedMotion ? 0.0f : bloom(anim, now);
        float total = scale * (1.0f + BLOOM_SCALE * bloom);
        int shake = reducedMotion ? 0 : shakeOffset(state, anim, now);

        // Scale about the tile's centre (§4.1) so the bloom grows outward instead of shoving the
        // badge down and to the right.
        float centreX = x + TILE / 2.0f;
        float centreY = y + TILE / 2.0f;

        graphics.pose().pushPose();
        graphics.pose().translate(centreX + shake, centreY, 0.0f);
        graphics.pose().scale(total, total, 1.0f);
        graphics.pose().translate(-centreX, -centreY, 0.0f);

        drawRows(graphics, x + 1, y + 1, 14, OUTLINE_ROWS, alphaMatched(OUTLINE, body));
        drawRows(graphics, x + 2, y + 2, 12, BODY_ROWS, body);
        drawRune(graphics, x, y, state, anim, now, body, reducedMotion);
        if (state == AvatarState.WORKING) {
            drawOrbitDot(graphics, x, y, body, anim, now, reducedMotion);
        }

        graphics.pose().popPose();
    }

    // ------------------------------------------------------------------------ colour

    /** The glyph fill: the state's colour, crossfaded in from the previous state's, plus glow. */
    private static int bodyColour(AvatarState state, AvatarAnimation anim, long now, boolean reducedMotion) {
        int target = colourFor(state);
        int colour = ColorLerp.blend(colourFor(anim.previousState()), target, anim.stateFade(now));
        colour = ColorLerp.brighten(colour, glow(state, anim, now, reducedMotion));

        if (state == AvatarState.OFFLINE) {
            float fade = Easing.easeOut(AnimClock.progress(now, anim.stateChangedAt(), OFFLINE_FADE_MILLIS));
            colour = ColorLerp.withAlphaScale(colour, Easing.lerp(1.0f, OFFLINE_ALPHA, fade));
        }
        return colour;
    }

    /**
     * How far toward white the glyph is lifted, 0..1. This stands in for the spec's soft radial
     * glow: there is no separate glow layer to fade, so "brighter" is expressed on the fill itself.
     */
    private static float glow(AvatarState state, AvatarAnimation anim, long now, boolean reducedMotion) {
        float amount = switch (state) {
            // §3.1's candle flicker - a rare single blip, never a continuous breathing loop.
            case IDLE -> reducedMotion ? 0.0f : IdleFlicker.intensityAt(now, anim.epoch()) * 0.35f;
            // One-shot flash to full, settling to a rest slightly brighter than IDLE's.
            case RESPONDING -> Easing.lerp(0.60f, 0.20f,
                    Easing.easeOut(AnimClock.progress(now, anim.stateChangedAt(), RESPOND_FLASH_MILLIS)));
            default -> 0.0f;
        };
        if (!reducedMotion) {
            amount += bloom(anim, now) * 0.45f;
        }
        return Easing.clamp01(amount);
    }

    private static int colourFor(AvatarState state) {
        return switch (state) {
            case OFFLINE -> NEUTRAL_OFF;
            case IDLE, WORKING, THINKING -> BRAND;
            case RESPONDING -> RESPOND;
            case SUCCESS -> POSITIVE;
            case ERROR -> NEGATIVE;
        };
    }

    /** Keeps the outline as transparent as the body, so OFFLINE fades as one piece. */
    private static int alphaMatched(int colour, int reference) {
        return ((reference >>> 24) << 24) | (colour & 0x00FFFFFF);
    }

    // ------------------------------------------------------------------------ motion

    /** 0 -> 1 -> 0 over the wake window, so the badge swells and settles rather than snapping big. */
    private static float bloom(AvatarAnimation anim, long now) {
        if (!anim.isWaking(now)) {
            return 0.0f;
        }
        return (float) Math.sin(Math.PI * anim.wakeProgress(now));
    }

    /**
     * §3.1's error shake: +/-1px horizontally, 3 cycles over 150ms, fired once on entry only. The
     * doc is emphatic that it must not repeat during the 4s hold, or it reads as an alarm rather
     * than a notice - which is why this is driven by the state-change timestamp and not a loop.
     */
    private static int shakeOffset(AvatarState state, AvatarAnimation anim, long now) {
        if (state != AvatarState.ERROR || !AnimClock.running(now, anim.stateChangedAt(), SHAKE_MILLIS)) {
            return 0;
        }
        float t = AnimClock.progress(now, anim.stateChangedAt(), SHAKE_MILLIS);
        return (int) Math.round(Math.sin(2 * Math.PI * SHAKE_CYCLES * t));
    }

    // ------------------------------------------------------------------------ drawing

    /** Fills one horizontal band per row, insetting each end by that row's indent. */
    private static void drawRows(GuiGraphics graphics, int left, int top, int width, int[] rows, int colour) {
        for (int row = 0; row < rows.length; row++) {
            int indent = rows[row];
            graphics.fill(left + indent, top + row, left + width - indent, top + row + 1, colour);
        }
    }

    /**
     * Three short strokes on a ring inside the glyph. THINKING advances them through 3 discrete
     * positions (§13.1: stepped, never interpolated - a smooth spin reads as a generic web
     * spinner), and tints them aqua while the outer glyph stays amber, per §3.1.
     */
    private static void drawRune(GuiGraphics graphics, int x, int y, AvatarState state,
                                 AvatarAnimation anim, long now, int body, boolean reducedMotion) {
        boolean thinking = state == AvatarState.THINKING;
        int step = (thinking && !reducedMotion)
                ? AnimClock.stepIndex(now, anim.epoch(), RUNE_CYCLE_MILLIS, RUNE_STEPS)
                : 0;

        int colour = thinking
                ? alphaMatched(COGNITION, body)
                : ColorLerp.blend(body, alphaMatched(OUTLINE, body), 0.55f);

        double centreX = x + TILE / 2.0;
        double centreY = y + TILE / 2.0;
        double stepRadians = Math.toRadians(RUNE_STEP_DEGREES * step);

        for (int i = 0; i < RUNE_STEPS; i++) {
            double angle = stepRadians + Math.toRadians(90 + i * (360.0 / RUNE_STEPS));
            int px = (int) Math.round(centreX + Math.cos(angle) * 3.0) - 1;
            int py = (int) Math.round(centreY + Math.sin(angle) * 3.0) - 1;
            graphics.fill(px, py, px + 2, py + 2, colour);
        }
    }

    /**
     * WORKING's orbiting dot - "a gear is turning", so linear easing, distinct from THINKING's
     * cognitive stepping. It rides just outside the glyph edge rather than the literal 2px radius
     * in §3.1, which would bury it inside the 12px body and make it invisible.
     *
     * <p>Under reduced motion §12.5 asks for a static dot that only changes brightness, so the
     * cycle drives the glow instead of the position.
     */
    private static void drawOrbitDot(GuiGraphics graphics, int x, int y, int body,
                                     AvatarAnimation anim, long now, boolean reducedMotion) {
        float phase = AnimClock.loopPhase(now, anim.epoch(), ORBIT_CYCLE_MILLIS);
        double angle = Math.toRadians(reducedMotion ? -90 : phase * 360.0 - 90);
        int colour = reducedMotion
                ? ColorLerp.brighten(body, (float) (0.5 + 0.5 * Math.sin(2 * Math.PI * phase)) * 0.5f)
                : body;

        int px = (int) Math.round(x + TILE / 2.0 + Math.cos(angle) * 7.0) - 1;
        int py = (int) Math.round(y + TILE / 2.0 + Math.sin(angle) * 7.0) - 1;
        graphics.fill(px, py, px + 2, py + 2, colour);
    }

    private AvatarSprite() {}
}
