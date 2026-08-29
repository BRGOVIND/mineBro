package com.minebro.client.hud;

import com.minebro.MineBro;
import com.minebro.client.screen.MineBroChatScreen;
import com.minebro.core.anim.AnimClock;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

/**
 * A small badge at the vertical middle of the left edge - the one region unclaimed by vanilla
 * HUD elements at every GUI scale (Phase 2 design doc §4). The glyph itself is drawn by
 * {@link AvatarSprite}; this class owns placement, visibility, and the subtitle.
 */
public final class MineBroHud {

    private static final int OFFSET_X = 6;
    private static final int SIZE = AvatarSprite.TILE;

    /** §3.1/§12.3: an ASCII ellipsis cycling at 400ms/step, as a redundant non-colour signal. */
    private static final long ELLIPSIS_STEP_MILLIS = 400;

    private final HudAvatarController controller;
    private final AvatarAnimation animation;

    public MineBroHud(HudAvatarController controller, AvatarAnimation animation) {
        this.controller = controller;
        this.animation = animation;
    }

    public void register() {
        HudRenderCallback.EVENT.register(this::render);
    }

    private void render(GuiGraphics graphics, net.minecraft.client.DeltaTracker deltaTracker) {
        Minecraft mc = Minecraft.getInstance();
        // The chat panel is the one screen the avatar stays visible behind (§13.2: the panel *is*
        // the avatar, expanded) - hiding it there would make pressing B read as the badge vanishing
        // and an unrelated window appearing. Every other screen, and hideGui, still hides it.
        boolean overChatPanel = mc.screen instanceof MineBroChatScreen;
        if (mc.options.hideGui || mc.player == null || (mc.screen != null && !overChatPanel)) {
            return;
        }

        long now = AvatarAnimation.now();
        boolean reducedMotion = MineBro.configManager().get().reducedMotion;

        int y = graphics.guiHeight() / 2 - SIZE / 2;
        int x = OFFSET_X;

        AvatarState state = controller.currentState();

        if (!overChatPanel && animation.isClosing(now) && !reducedMotion) {
            drawClosingGhost(graphics, now, y);
        }

        AvatarSprite.draw(graphics, x, y, 1.0f, state, animation, now, reducedMotion);

        // The subtitle belongs to the HUD badge only: while the chat panel is open the same
        // information is already on screen as step lines, and drawing it twice reads as a bug.
        if (!overChatPanel) {
            String subtitle = controller.subtitle();
            if (!subtitle.isEmpty() && state != AvatarState.IDLE) {
                String text = trim(subtitle);
                if (state == AvatarState.THINKING) {
                    text = withAnimatedEllipsis(text, now);
                }
                graphics.drawString(mc.font, text, x + SIZE + 4, y + 4, 0xFFE7E1D6, true);
            }
        }
    }

    /**
     * The collapsing panel outline, drawn for 120ms after the chat screen closes.
     *
     * <p>It lives here rather than in {@link MineBroChatScreen} because {@code setScreen(null)}
     * tears the screen down immediately - by the time the collapse should be drawn, there is no
     * screen left to draw it. So the HUD, which renders every frame regardless, draws the ghost:
     * background and border only, never text or widgets, since those belong to a screen that no
     * longer exists.
     */
    private void drawClosingGhost(GuiGraphics graphics, long now, int avatarY) {
        float progress = animation.closeProgress(now);
        MineBroChatScreen.drawCollapsingGhost(graphics, avatarY + SIZE / 2, progress);
    }

    private static String withAnimatedEllipsis(String text, long now) {
        String stem = text.endsWith("...") ? text.substring(0, text.length() - 3) : text;
        int dots = 1 + AnimClock.stepIndex(now, 0, ELLIPSIS_STEP_MILLIS * 3, 3);
        return stem + ".".repeat(dots);
    }

    private static String trim(String s) {
        return s.length() > 40 ? s.substring(0, 40) + "..." : s;
    }
}
