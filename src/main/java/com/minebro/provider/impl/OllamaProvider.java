package com.minebro.provider.impl;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.minebro.core.CancellationToken;
import com.minebro.provider.AIProvider;
import com.minebro.provider.ProviderCapabilities;
import com.minebro.provider.model.ChatMessage;
import com.minebro.provider.model.ChatRequest;
import com.minebro.provider.model.ChatResponse;
import com.minebro.provider.model.FinishReason;
import com.minebro.provider.model.HealthReport;
import com.minebro.provider.model.ModelInfo;
import com.minebro.provider.model.TokenUsage;

import java.io.IOException;
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
 * Talks to a local Ollama server's {@code POST /api/chat} and {@code GET /api/tags}.
 * Ollama's chat template determines whether the configured model honours the "tools" field;
 * MineBro does not rely on it here (see agent.codec.JsonPromptToolCallCodec) and instead treats
 * every Ollama model as non-native-tool-calling, which is always correct even when a given
 * model would also support the native path.
 */
public final class OllamaProvider implements AIProvider {

    private final String endpoint;
    private final String model;
    private final HttpClient http;

    public OllamaProvider(String endpoint, String model) {
        this.endpoint = endpoint.endsWith("/") ? endpoint.substring(0, endpoint.length() - 1) : endpoint;
        this.model = model;
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    @Override
    public String id() {
        return "ollama";
    }

    @Override
    public String displayName() {
        return "Ollama (local)";
    }

    @Override
    public ProviderCapabilities capabilities() {
        return new ProviderCapabilities(false, false, false, true, 0, false);
    }

    static JsonObject toRequestBody(ChatRequest request, String model) {
        JsonObject body = new JsonObject();
        body.addProperty("model", model);
        body.addProperty("stream", false);

        JsonArray messages = new JsonArray();
        for (ChatMessage message : request.messages()) {
            messages.add(toOllamaMessage(message));
        }
        body.add("messages", messages);

        JsonObject options = new JsonObject();
        options.addProperty("temperature", request.temperature());
        if (request.maxTokens() > 0) {
            options.addProperty("num_predict", request.maxTokens());
        }
        body.add("options", options);
        return body;
    }

    private static JsonObject toOllamaMessage(ChatMessage message) {
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
        if (root.has("message") && root.get("message").isJsonObject()) {
            JsonObject message = root.getAsJsonObject("message");
            if (message.has("content") && !message.get("content").isJsonNull()) {
                content = message.get("content").getAsString();
            }
        }
        int promptTokens = intOrZero(root, "prompt_eval_count");
        int completionTokens = intOrZero(root, "eval_count");
        ChatMessage.AssistantMessage assistant = new ChatMessage.AssistantMessage(content, List.of());
        return new ChatResponse(assistant, FinishReason.STOP, new TokenUsage(promptTokens, completionTokens), latency);
    }

    private static int intOrZero(JsonObject obj, String key) {
        return obj.has(key) && obj.get(key).isJsonPrimitive() ? obj.get(key).getAsInt() : 0;
    }

    @Override
    public CompletableFuture<ChatResponse> chat(ChatRequest request, CancellationToken cancel) {
        JsonObject body = toRequestBody(request, model);
        HttpRequest httpRequest;
        try {
            httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint + "/api/chat"))
                    .timeout(request.timeout())
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                    .build();
        } catch (IllegalArgumentException e) {
            // URI.create() throws synchronously, before any future exists to carry the failure -
            // a bad ollamaEndpoint value in the config file must not escape as a raw exception.
            return CompletableFuture.failedFuture(new ProviderException(
                    "Invalid Ollama endpoint \"" + endpoint + "\" - check ollamaEndpoint in your MineBro config.", false));
        }

        Instant start = Instant.now();
        CompletableFuture<HttpResponse<String>> httpFuture = http.sendAsync(httpRequest, HttpResponse.BodyHandlers.ofString());
        cancel.onCancel(() -> httpFuture.cancel(true));

        return httpFuture
                .thenApply(response -> {
                    if (cancel.isCancelled()) {
                        throw new ProviderException("cancelled", true);
                    }
                    Duration latency = Duration.between(start, Instant.now());
                    if (response.statusCode() == 404) {
                        throw new ProviderException(
                                "Ollama returned 404 - is model \"" + model + "\" pulled? Try: ollama pull " + model,
                                false);
                    }
                    if (response.statusCode() != 200) {
                        throw new ProviderException(
                                "Ollama returned HTTP " + response.statusCode() + ": " + truncate(response.body()),
                                false);
                    }
                    try {
                        return parseResponse(response.body(), latency);
                    } catch (RuntimeException e) {
                        throw new ProviderException("Malformed Ollama response: " + e.getMessage(), false);
                    }
                })
                .exceptionallyCompose(this::translateConnectionFailure);
    }

    private CompletableFuture<ChatResponse> translateConnectionFailure(Throwable t) {
        Throwable cause = t.getCause() != null ? t.getCause() : t;
        if (cause instanceof ProviderException pe) {
            return CompletableFuture.failedFuture(pe);
        }
        if (cause instanceof java.util.concurrent.CancellationException) {
            return CompletableFuture.failedFuture(new ProviderException("cancelled", true));
        }
        if (cause instanceof java.net.ConnectException || cause instanceof java.net.http.HttpConnectTimeoutException) {
            return CompletableFuture.failedFuture(
                    new ProviderException("Can't reach Ollama at " + endpoint + " - is it running?", false));
        }
        if (cause instanceof java.net.http.HttpTimeoutException) {
            return CompletableFuture.failedFuture(new ProviderException("Ollama request timed out", false));
        }
        return CompletableFuture.failedFuture(new ProviderException("Ollama request failed: " + cause.getMessage(), false));
    }

    private static String truncate(String s) {
        return s.length() > 200 ? s.substring(0, 200) + "..." : s;
    }

    @Override
    public CompletableFuture<HealthReport> health() {
        HttpRequest request;
        try {
            request = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint + "/api/tags"))
                    .timeout(Duration.ofSeconds(3))
                    .GET()
                    .build();
        } catch (IllegalArgumentException e) {
            return CompletableFuture.completedFuture(
                    HealthReport.unreachable("Invalid endpoint \"" + endpoint + "\" - check ollamaEndpoint in your config."));
        }
        return http.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (response.statusCode() != 200) {
                        return HealthReport.unreachable("HTTP " + response.statusCode());
                    }
                    List<String> tags = parseModelTags(response.body());
                    boolean hasModel = tags.stream().anyMatch(tag -> tag.equals(model) || tag.startsWith(model + ":"));
                    return hasModel
                            ? HealthReport.ok("Connected - " + model + " available")
                            : HealthReport.modelMissing("Connected, but \"" + model + "\" is not pulled (try: ollama pull " + model + ")");
                })
                .exceptionally(t -> HealthReport.unreachable("Can't reach Ollama at " + endpoint));
    }

    @Override
    public CompletableFuture<List<ModelInfo>> listModels() {
        HttpRequest request;
        try {
            request = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint + "/api/tags"))
                    .timeout(Duration.ofSeconds(3))
                    .GET()
                    .build();
        } catch (IllegalArgumentException e) {
            return CompletableFuture.completedFuture(List.of());
        }
        return http.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    List<ModelInfo> models = new ArrayList<>();
                    JsonObject root = JsonParser.parseString(response.body()).getAsJsonObject();
                    if (root.has("models")) {
                        for (JsonElement el : root.getAsJsonArray("models")) {
                            JsonObject m = el.getAsJsonObject();
                            String name = m.has("name") ? m.get("name").getAsString() : "unknown";
                            long size = m.has("size") ? m.get("size").getAsLong() : 0;
                            models.add(new ModelInfo(name, size));
                        }
                    }
                    return models;
                })
                .exceptionally(t -> List.of());
    }

    private static List<String> parseModelTags(String body) {
        List<String> tags = new ArrayList<>();
        try {
            JsonObject root = JsonParser.parseString(body).getAsJsonObject();
            if (root.has("models")) {
                for (JsonElement el : root.getAsJsonArray("models")) {
                    JsonObject m = el.getAsJsonObject();
                    if (m.has("name")) {
                        tags.add(m.get("name").getAsString());
                    }
                }
            }
        } catch (RuntimeException ignored) {
            // malformed /api/tags body - treat as "no models known", never crash the health probe
        }
        return tags;
    }
}
