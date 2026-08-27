package com.minebro.provider.impl;

import com.google.gson.JsonObject;
import com.minebro.core.CancellationToken;
import com.minebro.provider.model.ChatMessage;
import com.minebro.provider.model.ChatRequest;
import com.minebro.provider.model.ChatResponse;
import com.minebro.provider.model.HealthReport;
import com.minebro.provider.model.ModelInfo;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenAiCompatibleProviderTest {

    @Test
    void requestBodyMatchesOpenAiChatCompletionsShape() {
        ChatRequest request = new ChatRequest(
                List.of(new ChatMessage.SystemMessage("sys"), new ChatMessage.UserMessage("hi")),
                List.of(), 0.2, 128, Duration.ofSeconds(30));
        JsonObject body = OpenAiCompatibleProvider.toRequestBody(request, "gpt-4o-mini");
        assertEquals("gpt-4o-mini", body.get("model").getAsString());
        assertEquals(128, body.get("max_tokens").getAsInt());
        assertEquals(2, body.getAsJsonArray("messages").size());
    }

    @Test
    void parsesChoicesAndUsage() {
        String raw = "{\"choices\": [{\"message\": {\"role\": \"assistant\", \"content\": \"Hello!\"}}], "
                + "\"usage\": {\"prompt_tokens\": 10, \"completion_tokens\": 3}}";
        ChatResponse response = OpenAiCompatibleProvider.parseResponse(raw, Duration.ofMillis(200));
        assertEquals("Hello!", response.message().content());
        assertEquals(10, response.usage().promptTokens());
        assertEquals(3, response.usage().completionTokens());
    }

    @Test
    void missingUsageFieldDoesNotCrash() {
        String raw = "{\"choices\": [{\"message\": {\"content\": \"ok\"}}]}";
        ChatResponse response = OpenAiCompatibleProvider.parseResponse(raw, Duration.ZERO);
        assertEquals("ok", response.message().content());
        assertEquals(0, response.usage().promptTokens());
    }

    // The "genuinely universal OpenAI-compatible adapter" requirement: any server exposing the
    // standard GET /v1/models shape ({"data": [{"id": "..."}]}) must be discoverable through this
    // one parser - no per-vendor branching, and a malformed/unexpected body must never crash the
    // discovery attempt (model discovery is explicitly best-effort, never required).

    @Test
    void parseModelListReadsTheStandardOpenAiModelsShape() {
        String raw = "{\"data\": [{\"id\": \"gpt-4o-mini\"}, {\"id\": \"gpt-4o\"}]}";
        List<ModelInfo> models = OpenAiCompatibleProvider.parseModelList(raw);
        assertEquals(2, models.size());
        assertEquals("gpt-4o-mini", models.get(0).id());
        assertEquals("gpt-4o", models.get(1).id());
    }

    @Test
    void parseModelListOnMalformedBodyReturnsEmptyNotACrash() {
        assertTrue(OpenAiCompatibleProvider.parseModelList("not json at all").isEmpty());
    }

    @Test
    void parseModelListOnUnexpectedShapeReturnsEmptyNotACrash() {
        assertTrue(OpenAiCompatibleProvider.parseModelList("{\"error\": \"nope\"}").isEmpty());
    }

    // Same malformed-endpoint guard as OllamaProviderTest - a bad base URL in config.json must
    // fail cleanly, not escape as a raw IllegalArgumentException before any future exists.

    @Test
    void chatWithAMalformedBaseUrlFailsCleanlyInsteadOfThrowing() {
        OpenAiCompatibleProvider provider = new OpenAiCompatibleProvider(
                "openai-compatible", "Custom", "http://bad url with spaces", "some-model", "");
        ChatRequest request = new ChatRequest(List.of(new ChatMessage.UserMessage("hi")), List.of(), 0.2, 100, Duration.ofSeconds(5));

        ExecutionException ex = assertThrows(ExecutionException.class,
                () -> provider.chat(request, new CancellationToken()).get());
        assertInstanceOf(ProviderException.class, ex.getCause());
    }

    @Test
    void healthWithAMalformedBaseUrlReportsUnreachableInsteadOfThrowing() throws Exception {
        OpenAiCompatibleProvider provider = new OpenAiCompatibleProvider(
                "openai-compatible", "Custom", "http://bad url with spaces", "some-model", "");
        HealthReport report = provider.health().get();
        assertFalse(report.reachable());
    }
}
