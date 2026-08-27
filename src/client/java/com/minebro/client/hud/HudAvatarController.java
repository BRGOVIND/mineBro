package com.minebro.client.hud;

import com.minebro.agent.AgentEventSink;
import com.minebro.tool.ToolResult;

/**
 * Every non-IDLE/OFFLINE state decays back to IDLE on its own (Phase 2 §2.3) - the avatar never
 * demands a click to dismiss itself. Reads/writes are volatile-only: events arrive from the
 * background agent-loop thread, state is read from the render thread each frame.
 */
public final class HudAvatarController implements AgentEventSink {

    private static final long SUCCESS_HOLD_MS = 2000;
    private static final long ERROR_HOLD_MS = 4000;
    private static final long RESPONDING_HOLD_MS = 600;

    private volatile AvatarState rawState = AvatarState.IDLE;
    private volatile long stateSetAtMillis = System.currentTimeMillis();
    private volatile String subtitle = "";
    private volatile boolean offline = false;

    private void set(AvatarState state, String subtitle) {
        this.rawState = state;
        this.subtitle = subtitle;
        this.stateSetAtMillis = System.currentTimeMillis();
    }

    public void setOffline(String reason) {
        this.offline = true;
        set(AvatarState.OFFLINE, reason);
    }

    public void clearOffline() {
        this.offline = false;
    }

    @Override
    public void onThinking() {
        offline = false;
        set(AvatarState.THINKING, "thinking...");
    }

    @Override
    public void onToolCall(String toolName) {
        set(AvatarState.WORKING, "using " + toolName + "...");
    }

    @Override
    public void onToolResult(ToolResult result) {
        set(AvatarState.WORKING, result.success() ? "ok: " + result.tool() : "issue: " + result.reason());
    }

    @Override
    public void onFinalAnswer(String text) {
        set(AvatarState.RESPONDING, "");
    }

    @Override
    public void onError(String message) {
        set(AvatarState.ERROR, message);
    }

    public AvatarState currentState() {
        if (offline) {
            return AvatarState.OFFLINE;
        }
        long elapsed = System.currentTimeMillis() - stateSetAtMillis;
        return switch (rawState) {
            case RESPONDING -> elapsed > RESPONDING_HOLD_MS ? AvatarState.SUCCESS : AvatarState.RESPONDING;
            case SUCCESS -> elapsed > SUCCESS_HOLD_MS + RESPONDING_HOLD_MS ? AvatarState.IDLE : AvatarState.SUCCESS;
            case ERROR -> elapsed > ERROR_HOLD_MS ? AvatarState.IDLE : AvatarState.ERROR;
            default -> rawState;
        };
    }

    public String subtitle() {
        return subtitle;
    }
}
