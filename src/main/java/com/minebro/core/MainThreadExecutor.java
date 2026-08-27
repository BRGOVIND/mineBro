package com.minebro.core;

import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;

/**
 * Seam for "run this on the client thread and give me back the result", implemented in the
 * client source set (wraps {@code Minecraft.getInstance().execute(...)}). Kept as an interface
 * here so tool/context code in {@code main} never imports client-only Minecraft classes.
 */
public interface MainThreadExecutor {

    <T> CompletableFuture<T> submit(Callable<T> task);
}
