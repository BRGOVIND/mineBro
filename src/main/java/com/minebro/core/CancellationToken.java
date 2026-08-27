package com.minebro.core;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Cooperative cancellation signal shared between the agent loop, the provider HTTP call, and the
 * client UI. {@link #onCancel} exists so "/minebro stop" can actually abort an in-flight HTTP
 * request (e.g. {@code CompletableFuture#cancel(true)} on the pending response) rather than only
 * discarding the result once the (possibly very slow, local-model) request eventually finishes.
 */
public final class CancellationToken {

    private final AtomicBoolean cancelled = new AtomicBoolean(false);
    private final List<Runnable> listeners = new CopyOnWriteArrayList<>();

    public void cancel() {
        if (cancelled.compareAndSet(false, true)) {
            for (Runnable listener : listeners) {
                runSafely(listener);
            }
        }
    }

    public boolean isCancelled() {
        return cancelled.get();
    }

    public void throwIfCancelled() throws CancelledException {
        if (cancelled.get()) {
            throw new CancelledException();
        }
    }

    /** Fires immediately if already cancelled; otherwise fires exactly once, the first time {@link #cancel()} is called. */
    public void onCancel(Runnable listener) {
        listeners.add(listener);
        if (cancelled.get()) {
            runSafely(listener);
        }
    }

    private static void runSafely(Runnable listener) {
        try {
            listener.run();
        } catch (RuntimeException ignored) {
            // a listener misbehaving must never break cancellation for the others
        }
    }

    public static final class CancelledException extends Exception {
        public CancelledException() {
            super("Request was cancelled");
        }
    }
}
