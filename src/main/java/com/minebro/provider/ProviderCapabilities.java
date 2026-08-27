package com.minebro.provider;

public record ProviderCapabilities(
        boolean nativeToolCalling,
        boolean streaming,
        boolean jsonMode,
        boolean systemRole,
        int maxContextTokens,
        boolean requiresApiKey
) {}
