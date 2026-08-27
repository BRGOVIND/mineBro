package com.minebro.agent.codec;

import com.minebro.provider.model.ToolCall;

public sealed interface ParseOutcome {

    record ToolCallFound(ToolCall call) implements ParseOutcome {}

    record TextAnswer(String text) implements ParseOutcome {}
}
