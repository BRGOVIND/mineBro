package com.minebro.client.hud;

import com.minebro.core.anim.AnimClock;
import com.minebro.core.anim.Easing;

/**
 * One-shot animation timelines for the avatar and the chat panel: when the player last woke
 * MineBro with the keybind, and when the panel started opening or closing.
 *
 * <p>Deliberately <em>not</em> volatile, unlike {@link HudAvatarController}. That class needs
 * volatile fields because agent events arrive on the background agent-loop thread and are read by
 * the render thread; this one is only ever touched from the client thread - the keybind tick, the
 * chat screen, and the HUD renderer all run there - so plain fields are correct and cheaper.
 *
 * <p>It does track which {@link AvatarState} is showing, but only to spot the moment one changes
 * so a crossfade has something to fade from. It never decides what the state is - that stays
 * entirely in {@link HudAvatarController} (DESIGN.md §15.2: "who owns avatar state" must be
 * unambiguous). Everything here is presentation timing layered on top of that.
 */
public final class AvatarAnimation {

    /** §13.2: chat panel open/close, 120ms. */
    public static final long PANEL_MILLIS = 120;
    /** The badge's "wake" bloom when the panel is summoned - a one-shot ease-out, §2.3's brighten-don't-bounce. */
    public static final long WAKE_MILLIS = 200;
    /** §13.1's floor for a state colour change: anything shorter reads as a glitch, not a change. */
    public static final long CROSSFADE_MILLIS = 200;

    /** Epoch for every ambient loop (idle flicker, rune rotation, orbit) - set once at startup. */
    private final long epochMillis = now();

    private long wokeAtMillis;
    private long panelOpenedAtMillis;
    private long panelClosedAtMillis;

    private AvatarState currentState = AvatarState.IDLE;
    private AvatarState previousState = AvatarState.IDLE;
    private long stateChangedAtMillis;

    public static long now() {
        return System.currentTimeMillis();
    }

    public long epoch() {
        return epochMillis;
    }

    /** The panel is opening: stamp both the bloom and the panel tween from the same instant. */
    public void onPanelOpen() {
        long at = now();
        wokeAtMillis = at;
        panelOpenedAtMillis = at;
        panelClosedAtMillis = 0;
    }

    /**
     * The panel is closing. The screen is torn down by {@code setScreen(null)} the moment this is
     * called, so it cannot draw its own collapse - {@link MineBroHud} renders the shrinking ghost
     * from this timestamp instead.
     */
    public void onPanelClose() {
        panelClosedAtMillis = now();
        panelOpenedAtMillis = 0;
    }

    /** 0 -> 1 over {@link #WAKE_MILLIS}, ease-out. 1 means "settled", i.e. no bloom to draw. */
    public float wakeProgress(long nowMillis) {
        return Easing.easeOut(AnimClock.progress(nowMillis, wokeAtMillis, WAKE_MILLIS));
    }

    public boolean isWaking(long nowMillis) {
        return AnimClock.running(nowMillis, wokeAtMillis, WAKE_MILLIS);
    }

    /** How far the panel has grown out of the avatar: 0 = collapsed to the badge, 1 = full size. */
    public float openProgress(long nowMillis) {
        if (panelOpenedAtMillis == 0) {
            return 1.0f;
        }
        return Easing.easeOut(AnimClock.progress(nowMillis, panelOpenedAtMillis, PANEL_MILLIS));
    }

    /** True only while the closing ghost still has frames left to draw. */
    public boolean isClosing(long nowMillis) {
        return AnimClock.running(nowMillis, panelClosedAtMillis, PANEL_MILLIS);
    }

    /** 1 -> 0 as the ghost collapses back into the badge, ease-in (§13.2's close curve). */
    public float closeProgress(long nowMillis) {
        return 1.0f - Easing.easeIn(AnimClock.progress(nowMillis, panelClosedAtMillis, PANEL_MILLIS));
    }

    // ------------------------------------------------------------ state-transition tracking

    /**
     * Call once per frame with whatever {@link HudAvatarController} currently reports. That class
     * exposes only "what state am I in", not "what was I before and when did it change", which is
     * what a crossfade needs - so the transition edge is detected here, on the render thread,
     * rather than by widening the off-thread state machine's contract.
     */
    public void observe(AvatarState state, long nowMillis) {
        if (state != currentState) {
            previousState = currentState;
            currentState = state;
            stateChangedAtMillis = nowMillis;
        }
    }

    public AvatarState previousState() {
        return previousState;
    }

    /** When the avatar last changed state - the origin for entry one-shots (flash, shake, fade). */
    public long stateChangedAt() {
        return stateChangedAtMillis;
    }

    /** 0 -> 1 crossfade from {@link #previousState()} to the current one. */
    public float stateFade(long nowMillis) {
        return Easing.sineInOut(AnimClock.progress(nowMillis, stateChangedAtMillis, CROSSFADE_MILLIS));
    }
}
