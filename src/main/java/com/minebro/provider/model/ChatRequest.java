package com.minebro.provider.model;

import java.time.Duration;
import java.util.List;

/**
 * The model to use is the provider's own concern (each adapter is constructed with one, e.g.
 * {@code new OllamaProvider(endpoint, model)}) - it is not repeated here. An earlier version of
 * this record carried a {@code model} field that {@code AgentLoop} had to resolve via a
 * provider-id switch statement, but neither adapter's {@code toRequestBody} ever read it; the
 * switch was a pure leak of provider-specific knowledge into a caller that should only depend on
 * {@link com.minebro.provider.AIProvider}, with no behavioral effect. Removed rather than fixed
 * in place, since there was nothing to fix - the field was dead.
 */
public record ChatRequest(
        List<ChatMessage> messages,
        List<ToolSchema> tools,
        double temperature,
        int maxTokens,
        Duration timeout
) {
    public ChatRequest {
        messages = List.copyOf(messages);
        tools = tools == null ? List.of() : List.copyOf(tools);
    }
}
