package com.minebro.provider.impl;

/**
 * Thrown for any provider-shaped failure (bad status, malformed body, cancellation). Unchecked
 * so it can cross {@code CompletableFuture} lambda boundaries. Lives at the top level, shared by
 * every HTTP-based adapter, so no adapter has to reach into a sibling's namespace to construct
 * or catch it - a third adapter costs nothing here.
 */
public final class ProviderException extends RuntimeException {

    public final boolean cancelled;

    public ProviderException(String message, boolean cancelled) {
        super(message);
        this.cancelled = cancelled;
    }
}
