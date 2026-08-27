package com.minebro.core;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Background worker pool for anything that must never run on the client thread: HTTP calls,
 * JSON parsing of model output, the agent loop itself. Virtual threads because most of the
 * work here is I/O-bound waiting on a local/remote model, not CPU-bound.
 */
public final class MineBroExecutors {

    private static final ExecutorService WORKER = Executors.newVirtualThreadPerTaskExecutor();

    public static ExecutorService worker() {
        return WORKER;
    }

    private MineBroExecutors() {}
}
