package com.minebro.provider.model;

import java.util.List;

/** Normalized message shape every provider adapter translates to and from. */
public sealed interface ChatMessage {

    record SystemMessage(String content) implements ChatMessage {}

    record UserMessage(String content) implements ChatMessage {}

    record AssistantMessage(String content, List<ToolCall> toolCalls) implements ChatMessage {
        public AssistantMessage {
            toolCalls = toolCalls == null ? List.of() : List.copyOf(toolCalls);
        }
    }

    record ToolResultMessage(String toolCallId, String toolName, String jsonResult) implements ChatMessage {}
}
