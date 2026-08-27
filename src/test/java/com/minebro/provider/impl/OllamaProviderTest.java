package com.minebro.provider.impl;

import com.google.gson.JsonObject;
import com.minebro.core.CancellationToken;
import com.minebro.provider.model.ChatMessage;
import com.minebro.provider.model.ChatRequest;
import com.minebro.provider.model.ChatResponse;
import com.minebro.provider.model.HealthReport;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OllamaProviderTest {

    @Test
    void requestBodyIncludesModelAndDisablesStreaming() {
        ChatRequest request = new ChatRequest(
                List.of(new ChatMessage.UserMessage("hello")), List.of(), 0.2, 256, Duration.ofSeconds(30));
        JsonObject body = OllamaProvider.toRequestBody(request, "mistral");
        assertEquals("mistral", body.get("model").getAsString());
        assertFalse(body.get("stream").getAsBoolean());
        assertEquals(1, body.getAsJsonArray("messages").size());
        assertEquals("user", body.getAsJsonArray("messages").get(0).getAsJsonObject().get("role").getAsString());
    }

    @Test
    void requestBodyMapsSystemAndAssistantRoles() {
        ChatRequest request = new ChatRequest(
                List.of(
                        new ChatMessage.SystemMessage("You are MineBro."),
                        new ChatMessage.UserMessage("Can I craft a shield?"),
                        new ChatMessage.AssistantMessage("Let me check.", List.of())
                ), List.of(), 0.2, 256, Duration.ofSeconds(30));
        JsonObject body = OllamaProvider.toRequestBody(request, "mistral");
        var messages = body.getAsJsonArray("messages");
        assertEquals("system", messages.get(0).getAsJsonObject().get("role").getAsString());
        assertEquals("user", messages.get(1).getAsJsonObject().get("role").getAsString());
        assertEquals("assistant", messages.get(2).getAsJsonObject().get("role").getAsString());
    }

    @Test
    void parsesWellFormedResponse() {
        String raw = "{\"message\": {\"role\": \"assistant\", \"content\": \"Yes, you can.\"}, \"prompt_eval_count\": 42, \"eval_count\": 7}";
        ChatResponse response = OllamaProvider.parseResponse(raw, Duration.ofMillis(500));
        assertEquals("Yes, you can.", response.message().content());
        assertEquals(42, response.usage().promptTokens());
        assertEquals(7, response.usage().completionTokens());
    }

    @Test
    void malformedResponseBodyThrowsRatherThanReturningGarbage() {
        assertThrows(RuntimeException.class, () -> OllamaProvider.parseResponse("not json at all", Duration.ZERO));
    }

    @Test
    void responseWithMissingMessageFieldYieldsEmptyContentNotACrash() {
        ChatResponse response = OllamaProvider.parseResponse("{\"done\": true}", Duration.ZERO);
        assertEquals("", response.message().content());
    }

    // A malformed endpoint in config.json (a typo, a stray space, a missing scheme) makes
    // URI.create() throw IllegalArgumentException synchronously, before chat()/health() ever
    // return a future - this must become a clean failure, not an exception escaping the provider
    // layer entirely (which would surface as a raw, unexplained error to the player).

    @Test
    void chatWithAMalformedEndpointFailsCleanlyInsteadOfThrowing() {
        OllamaProvider provider = new OllamaProvider("http://bad url with spaces", "mistral");
        ChatRequest request = new ChatRequest(List.of(new ChatMessage.UserMessage("hi")), List.of(), 0.2, 100, Duration.ofSeconds(5));

        ExecutionException ex = assertThrows(ExecutionException.class,
                () -> provider.chat(request, new CancellationToken()).get());
        assertInstanceOf(ProviderException.class, ex.getCause());
    }

    @Test
    void healthWithAMalformedEndpointReportsUnreachableInsteadOfThrowing() throws Exception {
        OllamaProvider provider = new OllamaProvider("http://bad url with spaces", "mistral");
        HealthReport report = provider.health().get();
        assertFalse(report.reachable());
    }
}
