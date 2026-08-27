package com.minebro.provider.model;

import java.time.Duration;

public record ChatResponse(
        ChatMessage.AssistantMessage message,
        FinishReason finishReason,
        TokenUsage usage,
        Duration latency
) {}
