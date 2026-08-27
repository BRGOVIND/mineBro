package com.minebro.provider;

import com.minebro.core.CancellationToken;
import com.minebro.provider.model.ChatRequest;
import com.minebro.provider.model.ChatResponse;
import com.minebro.provider.model.HealthReport;
import com.minebro.provider.model.ModelInfo;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * One class per provider, zero Minecraft imports so this stays testable against recorded HTTP
 * fixtures. Adding a provider is implementing this interface and adding one line to
 * {@link ProviderRegistry} - nothing in the agent/tool layers changes.
 */
public interface AIProvider extends AutoCloseable {

    String id();

    String displayName();

    ProviderCapabilities capabilities();

    /** Non-blocking. Must never be invoked from the client thread. */
    CompletableFuture<ChatResponse> chat(ChatRequest request, CancellationToken cancel);

    /** Cheap liveness + model-availability probe. Drives the OFFLINE avatar state. */
    CompletableFuture<HealthReport> health();

    /** Models the endpoint reports, if it can. Empty list if unsupported. */
    default CompletableFuture<List<ModelInfo>> listModels() {
        return CompletableFuture.completedFuture(List.of());
    }

    @Override
    default void close() {}
}
