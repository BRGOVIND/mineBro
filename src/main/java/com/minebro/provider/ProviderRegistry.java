package com.minebro.provider;

import com.minebro.config.MineBroConfig;
import com.minebro.provider.impl.OllamaProvider;
import com.minebro.provider.impl.OpenAiCompatibleProvider;

/**
 * Builds the currently-configured {@link AIProvider} from {@link MineBroConfig}. Adding a new
 * provider id is one case here plus one adapter class - the agent/tool layers never change.
 */
public final class ProviderRegistry {

    public static AIProvider create(MineBroConfig config) {
        return switch (config.providerId) {
            case "openai-compatible" -> new OpenAiCompatibleProvider(
                    "openai-compatible", "OpenAI-compatible", config.openAiCompatEndpoint,
                    config.openAiCompatModel, config.resolveApiKey());
            case "ollama" -> new OllamaProvider(config.ollamaEndpoint, config.ollamaModel);
            default -> new OllamaProvider(config.ollamaEndpoint, config.ollamaModel);
        };
    }

    private ProviderRegistry() {}
}
