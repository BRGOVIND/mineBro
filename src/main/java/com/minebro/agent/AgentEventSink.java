package com.minebro.agent;

import com.minebro.tool.ToolResult;

/** How the agent loop reports progress to the client HUD/chat without importing anything client-only. */
public interface AgentEventSink {

    void onThinking();

    void onToolCall(String toolName);

    void onToolResult(ToolResult result);

    void onFinalAnswer(String text);

    void onError(String message);

    AgentEventSink NOOP = new AgentEventSink() {
        @Override public void onThinking() {}
        @Override public void onToolCall(String toolName) {}
        @Override public void onToolResult(ToolResult result) {}
        @Override public void onFinalAnswer(String text) {}
        @Override public void onError(String message) {}
    };
}
