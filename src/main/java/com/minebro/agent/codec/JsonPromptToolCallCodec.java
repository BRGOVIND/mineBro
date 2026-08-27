package com.minebro.agent.codec;

import com.google.gson.JsonObject;
import com.minebro.provider.model.ChatResponse;
import com.minebro.provider.model.ToolCall;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Fallback for models without native tool calling (every Ollama model, in practice - see
 * OllamaProvider's javadoc). Whether a model prefers native function calling or this prompt-
 * injected convention has to be verified against the actual model in use; this codec is always
 * correct to use, even for a model that also supports the native path.
 */
public final class JsonPromptToolCallCodec {

    private final AtomicInteger idCounter = new AtomicInteger();

    public ParseOutcome decode(ChatResponse response) {
        String content = response.message().content();
        Optional<JsonObject> toolJson = JsonExtractor.extractToolCall(content);
        if (toolJson.isEmpty()) {
            return new ParseOutcome.TextAnswer(content == null ? "" : content.trim());
        }
        JsonObject obj = toolJson.get();
        String toolName = obj.get("tool").getAsString();
        JsonObject arguments = obj.get("arguments").getAsJsonObject();
        String id = "tc_" + idCounter.incrementAndGet();
        return new ParseOutcome.ToolCallFound(new ToolCall(id, toolName, arguments));
    }
}
