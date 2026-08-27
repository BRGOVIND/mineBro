package com.minebro.client.hud;

import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

/**
 * A small badge at the vertical middle of the left edge - the one region unclaimed by vanilla
 * HUD elements at every GUI scale (Phase 2 design doc §4). Deliberately a flat colored square,
 * not a texture: no avatar art asset exists yet, and "clean HUD rendering, not an animation
 * engine" (Phase 3 brief) rules out building a sprite pipeline just to ship a placeholder.
 */
public final class MineBroHud {

    private static final int OFFSET_X = 6;
    private static final int SIZE = 16;

    private final HudAvatarController controller;

    public MineBroHud(HudAvatarController controller) {
        this.controller = controller;
    }

    public void register() {
        HudRenderCallback.EVENT.register(this::render);
    }

    private void render(GuiGraphics graphics, net.minecraft.client.DeltaTracker deltaTracker) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui || mc.screen != null || mc.player == null) {
            return;
        }

        int y = graphics.guiHeight() / 2 - SIZE / 2;
        int x = OFFSET_X;

        AvatarState state = controller.currentState();
        int color = colorFor(state);

        graphics.fill(x, y, x + SIZE, y + SIZE, 0xFF101010);
        graphics.fill(x + 1, y + 1, x + SIZE - 1, y + SIZE - 1, color);

        String subtitle = controller.subtitle();
        if (!subtitle.isEmpty() && state != AvatarState.IDLE) {
            graphics.drawString(mc.font, trim(subtitle), x + SIZE + 4, y + 4, 0xFFE7E1D6, true);
        }
    }

    private static String trim(String s) {
        return s.length() > 40 ? s.substring(0, 40) + "..." : s;
    }

    private static int colorFor(AvatarState state) {
        return switch (state) {
            case OFFLINE -> 0xFF6B6B6B;
            case IDLE -> 0xFFE8A93C;
            case THINKING -> 0xFF4FD8D0;
            case WORKING -> 0xFFE8A93C;
            case RESPONDING -> 0xFFFFC96B;
            case SUCCESS -> 0xFF6FCB6A;
            case ERROR -> 0xFFE05555;
        };
    }
}
