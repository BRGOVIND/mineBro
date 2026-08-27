package com.minebro.tool;

import com.minebro.core.MainThreadExecutor;
import com.minebro.provider.model.ToolCall;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;

/**
 * The one gate every tool call passes through: lookup, world/context availability, permission,
 * execute, catch. Two entry points share it - {@link #run} for callers not already on the
 * client thread (hops via {@link MainThreadExecutor}), {@link #runOnClientThread} for callers
 * that already are (a synchronous Brigadier command handler) and would otherwise have to
 * reimplement this same gate to get a synchronous {@link ToolResult} back.
 */
public final class ToolExecutor {

    private static final Logger LOGGER = LoggerFactory.getLogger(ToolExecutor.class);

    private final ToolRegistry registry;
    private final MainThreadExecutor mainThread;

    public ToolExecutor(ToolRegistry registry, MainThreadExecutor mainThread) {
        this.registry = registry;
        this.mainThread = mainThread;
    }

    /** For callers not already on the client thread (the agent loop, running off-thread). */
    public CompletableFuture<ToolResult> run(ToolCall call, ToolContextSupplier ctxSupplier) {
        var toolOpt = registry.find(call.tool());
        if (toolOpt.isEmpty()) {
            return CompletableFuture.completedFuture(unknownTool(call));
        }
        MineBroTool tool = toolOpt.get();
        return mainThread.submit(() -> executeGated(tool, call, ctxSupplier));
    }

    /**
     * For callers that are already on the client thread and need a synchronous result - no
     * thread hop, no future. It is the caller's responsibility to actually be on the client
     * thread; this class stays Minecraft-light and doesn't assert it itself (see the call site
     * in the client source set, which does).
     */
    public ToolResult runOnClientThread(ToolCall call, ToolContextSupplier ctxSupplier) {
        var toolOpt = registry.find(call.tool());
        if (toolOpt.isEmpty()) {
            return unknownTool(call);
        }
        return executeGated(toolOpt.get(), call, ctxSupplier);
    }

    private ToolResult executeGated(MineBroTool tool, ToolCall call, ToolContextSupplier ctxSupplier) {
        ToolContext ctx;
        try {
            ctx = ctxSupplier.get();
        } catch (IllegalStateException e) {
            return ToolResult.fail(call.id(), call.tool(), ToolResultCode.WORLD_NOT_LOADED,
                    "MineBro needs a loaded world for that.");
        }

        if (!ctx.config().permissionLevel.allows(tool.requiredPermission())) {
            return ToolResult.fail(call.id(), call.tool(), ToolResultCode.PERMISSION_DENIED,
                    "MineBro isn't allowed to do that yet - current permission level is "
                            + ctx.config().permissionLevel + ", this needs " + tool.requiredPermission() + ".");
        }
        try {
            return tool.execute(call, ctx);
        } catch (RuntimeException e) {
            LOGGER.error("Tool {} threw during execution", call.tool(), e);
            return ToolResult.fail(call.id(), call.tool(), ToolResultCode.INTERNAL_ERROR,
                    "Something went wrong running that: " + e.getMessage());
        }
    }

    private static ToolResult unknownTool(ToolCall call) {
        return ToolResult.fail(call.id(), call.tool(), ToolResultCode.UNKNOWN_TOOL,
                "There is no tool named \"" + call.tool() + "\".");
    }

    @FunctionalInterface
    public interface ToolContextSupplier {
        ToolContext get();
    }
}
