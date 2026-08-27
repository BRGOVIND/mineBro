package com.minebro.provider.impl;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.minebro.core.CancellationToken;
import com.minebro.provider.AIProvider;
import com.minebro.provider.ProviderCapabilities;
import com.minebro.provider.http.SecretRedactor;
import com.minebro.provider.model.ChatMessage;
import com.minebro.provider.model.ChatRequest;
import com.minebro.provider.model.ChatResponse;
import com.minebro.provider.model.FinishReason;
import com.minebro.provider.model.HealthReport;
import com.minebro.provider.model.ModelInfo;
import com.minebro.provider.model.TokenUsage;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * One adapter for every server that speaks the OpenAI {@code /v1/chat/completions} shape:
 * LM Studio, llama.cpp's {@code server}, vLLM, Groq, OpenRouter, and OpenAI itself. They differ
 * only in base URL, whether an Authorization header is required, and the model id - never in
 * the wire format, so a second class per vendor would be duplication, not abstraction.
 */
public final class OpenAiCompatibleProvider implements AIProvider {

    private final String id;
    private final String displayName;
    private final String baseUrl;
    private final String model;
    private final String apiKey;
    private final HttpClient http;

    public OpenAiCompatibleProvider(String id, String displayName, String baseUrl, String model, String apiKey) {
        this.id = id;
        this.displayName = displayName;
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.model = model;
        this.apiKey = apiKey;
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public String displayName() {
        return displayName;
    }

    @Override
    public ProviderCapabilities capabilities() {
        return new ProviderCapabilities(true, false, true, true, 0, apiKey != null && !apiKey.isBlank());
    }

    static JsonObject toRequestBody(ChatRequest request, String model) {
        JsonObject body = new JsonObject();
        body.addProperty("model", model);
        body.addProperty("temperature", request.temperature());
        if (request.maxTokens() > 0) {
            body.addProperty("max_tokens", request.maxTokens());
        }
        JsonArray messages = new JsonArray();
        for (ChatMessage message : request.messages()) {
            messages.add(toOpenAiMessage(message));
        }
        body.add("messages", messages);
        return body;
    }

    private static JsonObject toOpenAiMessage(ChatMessage message) {
        JsonObject json = new JsonObject();
        switch (message) {
            case ChatMessage.SystemMessage m -> {
                json.addProperty("role", "system");
                json.addProperty("content", m.content());
            }
            case ChatMessage.UserMessage m -> {
                json.addProperty("role", "user");
                json.addProperty("content", m.content());
            }
            case ChatMessage.AssistantMessage m -> {
                json.addProperty("role", "assistant");
                json.addProperty("content", m.content() == null ? "" : m.content());
            }
            case ChatMessage.ToolResultMessage m -> {
                json.addProperty("role", "user");
                json.addProperty("content", "Tool result for " + m.toolName() + ": " + m.jsonResult());
            }
        }
        return json;
    }

    static ChatResponse parseResponse(String rawBody, Duration latency) {
        JsonObject root = JsonParser.parseString(rawBody).getAsJsonObject();
        String content = "";
        if (root.has("choices") && root.getAsJsonArray("choices").size() > 0) {
            JsonObject choice = root.getAsJsonArray("choices").get(0).getAsJsonObject();
            JsonObject messageObj = choice.getAsJsonObject("message");
            if (messageObj != null && messageObj.has("content") && !messageObj.get("content").isJsonNull()) {
                content = messageObj.get("content").getAsString();
            }
        }
        int promptTokens = 0;
        int completionTokens = 0;
        if (root.has("usage")) {
            JsonObject usage = root.getAsJsonObject("usage");
            promptTokens = usage.has("prompt_tokens") ? usage.get("prompt_tokens").getAsInt() : 0;
            completionTokens = usage.has("completion_tokens") ? usage.get("completion_tokens").getAsInt() : 0;
        }
        ChatMessage.AssistantMessage assistant = new ChatMessage.AssistantMessage(content, List.of());
        return new ChatResponse(assistant, FinishReason.STOP, new TokenUsage(promptTokens, completionTokens), latency);
    }

    @Override
    public CompletableFuture<ChatResponse> chat(ChatRequest request, CancellationToken cancel) {
        JsonObject body = toRequestBody(request, model);
        HttpRequest httpRequest;
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/chat/completions"))
                    .timeout(request.timeout())
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body.toString()));
            if (apiKey != null && !apiKey.isBlank()) {
                builder.header("Authorization", "Bearer " + apiKey);
            }
            httpRequest = builder.build();
        } catch (IllegalArgumentException e) {
            // URI.create() throws synchronously, before any future exists to carry the failure -
            // a bad base-URL value in the config file must not escape as a raw exception.
            return CompletableFuture.failedFuture(new ProviderException(
                    "Invalid " + displayName + " base URL \"" + baseUrl + "\" - check your MineBro config.", false));
        }

        Instant start = Instant.now();
        CompletableFuture<HttpResponse<String>> httpFuture = http.sendAsync(httpRequest, HttpResponse.BodyHandlers.ofString());
        cancel.onCancel(() -> httpFuture.cancel(true));

        return httpFuture
                .thenApply(response -> {
                    if (cancel.isCancelled()) {
                        throw new RuntimeException(new ProviderException("cancelled", true));
                    }
                    Duration latency = Duration.between(start, Instant.now());
                    int status = response.statusCode();
                    if (status == 401 || status == 403) {
                        throw new RuntimeException(new ProviderException(
                                "Authentication failed (key: " + SecretRedactor.mask(apiKey) + ") - check your API key", false));
                    }
                    if (status == 404) {
                        throw new RuntimeException(new ProviderException(
                                displayName + " returned 404 - check the base URL and that model \"" + model + "\" exists", false));
                    }
                    if (status == 429) {
                        throw new RuntimeException(new ProviderException(
                                displayName + " is rate-limiting requests - try again in a moment", false));
                    }
                    if (status >= 500) {
                        throw new RuntimeException(new ProviderException(
                                displayName + " is having trouble right now (HTTP " + status + ") - try again shortly", false));
                    }
                    if (status != 200) {
                        throw new RuntimeException(new ProviderException(
                                displayName + " returned HTTP " + status, false));
                    }
                    try {
                        return parseResponse(response.body(), latency);
                    } catch (RuntimeException e) {
                        throw new RuntimeException(new ProviderException("Malformed response: " + e.getMessage(), false));
                    }
                })
                .handle((result, throwable) -> {
                    if (throwable == null) {
                        return CompletableFuture.completedFuture(result);
                    }
                    Throwable cause = throwable.getCause() != null ? throwable.getCause() : throwable;
                    if (cause instanceof ProviderException pe) {
                        return CompletableFuture.<ChatResponse>failedFuture(pe);
                    }
                    if (cause instanceof java.util.concurrent.CancellationException) {
                        return CompletableFuture.<ChatResponse>failedFuture(new ProviderException("cancelled", true));
                    }
                    if (cause instanceof java.net.ConnectException) {
                        return CompletableFuture.<ChatResponse>failedFuture(
                                new ProviderException("Can't reach " + displayName + " at " + baseUrl, false));
                    }
                    return CompletableFuture.<ChatResponse>failedFuture(
                            new ProviderException(displayName + " request failed: " + cause.getMessage(), false));
                })
                .thenCompose(f -> f);
    }

    @Override
    public CompletableFuture<HealthReport> health() {
        HttpRequest request;
        try {
            request = modelsRequest();
        } catch (IllegalArgumentException e) {
            return CompletableFuture.completedFuture(
                    HealthReport.unreachable("Invalid base URL \"" + baseUrl + "\" - check your config."));
        }
        return http.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> switch (response.statusCode()) {
                    case 200 -> HealthReport.ok("Connected");
                    case 401, 403 -> HealthReport.unreachable("Authentication failed - check API key");
                    case 404 -> HealthReport.unreachable("Not found (HTTP 404) - check the base URL");
                    case 429 -> HealthReport.unreachable("Rate-limited (HTTP 429) - try again in a moment");
                    default -> response.statusCode() >= 500
                            ? HealthReport.unreachable("Server error (HTTP " + response.statusCode() + ") - try again shortly")
                            : HealthReport.unreachable("HTTP " + response.statusCode());
                })
                .exceptionally(t -> HealthReport.unreachable("Can't reach " + displayName + " at " + baseUrl));
    }

    @Override
    public CompletableFuture<List<ModelInfo>> listModels() {
        HttpRequest request;
        try {
            request = modelsRequest();
        } catch (IllegalArgumentException e) {
            return CompletableFuture.completedFuture(List.of());
        }
        return http.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> response.statusCode() == 200 ? parseModelList(response.body()) : List.<ModelInfo>of())
                .exceptionally(t -> List.of());
    }

    private HttpRequest modelsRequest() {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/models"))
                .timeout(Duration.ofSeconds(3))
                .GET();
        if (apiKey != null && !apiKey.isBlank()) {
            builder.header("Authorization", "Bearer " + apiKey);
        }
        return builder.build();
    }

    /** OpenAI's standard {@code GET /v1/models} shape: {@code {"data": [{"id": "..."}]}} - the same shape every OpenAI-compatible server exposes, so this needs no per-vendor handling. */
    static List<ModelInfo> parseModelList(String rawBody) {
        List<ModelInfo> models = new ArrayList<>();
        try {
            JsonObject root = JsonParser.parseString(rawBody).getAsJsonObject();
            if (root.has("data")) {
                for (JsonElement el : root.getAsJsonArray("data")) {
                    JsonObject m = el.getAsJsonObject();
                    if (m.has("id")) {
                        models.add(new ModelInfo(m.get("id").getAsString(), 0));
                    }
                }
            }
        } catch (RuntimeException ignored) {
            // malformed/unexpected /models body - model discovery is best-effort, never crash on it
        }
        return models;
    }
}
