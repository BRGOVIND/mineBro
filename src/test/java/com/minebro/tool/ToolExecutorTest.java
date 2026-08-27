package com.minebro.tool;

import com.google.gson.JsonObject;
import com.minebro.config.MineBroConfig;
import com.minebro.core.MainThreadExecutor;
import com.minebro.provider.model.ToolCall;
import com.minebro.provider.model.ToolSchema;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Gate order (unknown tool -> world -> permission -> execute) and the never-crash safety net,
 * without touching any live Minecraft state. Both {@link ToolExecutor#run} (async, for callers
 * off the client thread) and {@link ToolExecutor#runOnClientThread} (sync, for callers already
 * on it) share the same gate - these tests exercise both entry points against the same fixtures
 * to prove that.
 */
class ToolExecutorTest {

    private static final MainThreadExecutor SYNCHRONOUS = new MainThreadExecutor() {
        @Override
        public <T> CompletableFuture<T> submit(Callable<T> task) {
            try {
                return CompletableFuture.completedFuture(task.call());
            } catch (Exception e) {
                return CompletableFuture.failedFuture(e);
            }
        }
    };

    private static ToolContext contextWith(PermissionLevel level) {
        MineBroConfig config = new MineBroConfig();
        config.permissionLevel = level;
        return new ToolContext(null, null, null, config, Instant.now(), null);
    }

    private static ToolResult run(ToolRegistry registry, ToolCall call, PermissionLevel level) throws ExecutionException, InterruptedException {
        ToolExecutor executor = new ToolExecutor(registry, SYNCHRONOUS);
        return executor.run(call, () -> contextWith(level)).get();
    }

    @Test
    void unknownToolIsRejectedBeforeTouchingTheClientThreadOrAnyContext() throws Exception {
        ToolRegistry registry = new ToolRegistry();
        ToolExecutor executor = new ToolExecutor(registry, SYNCHRONOUS);

        ToolExecutor.ToolContextSupplier explodingSupplier = () -> {
            throw new AssertionError("context must never be built for an unknown tool");
        };

        ToolResult result = executor.run(new ToolCall("id1", "nonexistent_tool", new JsonObject()), explodingSupplier).get();
        assertFalse(result.success());
        assertEquals(ToolResultCode.UNKNOWN_TOOL, result.code());
    }

    @Test
    void runOnClientThreadAlsoRejectsUnknownToolBeforeTouchingContext() {
        ToolRegistry registry = new ToolRegistry();
        ToolExecutor executor = new ToolExecutor(registry, SYNCHRONOUS);

        ToolExecutor.ToolContextSupplier explodingSupplier = () -> {
            throw new AssertionError("context must never be built for an unknown tool");
        };

        ToolResult result = executor.runOnClientThread(new ToolCall("id1", "nonexistent_tool", new JsonObject()), explodingSupplier);
        assertFalse(result.success());
        assertEquals(ToolResultCode.UNKNOWN_TOOL, result.code());
    }

    @Test
    void permissionDeniedNeverReachesToolExecute() throws Exception {
        FakeTool tool = new FakeTool(PermissionLevel.DESTRUCTIVE_ACTIONS);
        ToolRegistry registry = new ToolRegistry();
        registry.register(tool);

        ToolResult result = run(registry, new ToolCall("id1", tool.id(), new JsonObject()), PermissionLevel.READ_ONLY);

        assertFalse(result.success());
        assertEquals(ToolResultCode.PERMISSION_DENIED, result.code());
        assertFalse(tool.executed);
    }

    @Test
    void runOnClientThreadAlsoEnforcesPermission() {
        FakeTool tool = new FakeTool(PermissionLevel.DESTRUCTIVE_ACTIONS);
        ToolRegistry registry = new ToolRegistry();
        registry.register(tool);
        ToolExecutor executor = new ToolExecutor(registry, SYNCHRONOUS);

        ToolResult result = executor.runOnClientThread(new ToolCall("id1", tool.id(), new JsonObject()), () -> contextWith(PermissionLevel.READ_ONLY));

        assertFalse(result.success());
        assertEquals(ToolResultCode.PERMISSION_DENIED, result.code());
        assertFalse(tool.executed);
    }

    @Test
    void permissionAllowedRunsTheToolAndReturnsItsResult() throws Exception {
        FakeTool tool = new FakeTool(PermissionLevel.READ_ONLY);
        ToolRegistry registry = new ToolRegistry();
        registry.register(tool);

        ToolResult result = run(registry, new ToolCall("id1", tool.id(), new JsonObject()), PermissionLevel.READ_ONLY);

        assertTrue(result.success());
        assertTrue(tool.executed);
    }

    @Test
    void runOnClientThreadAlsoRunsTheToolOnSuccess() {
        FakeTool tool = new FakeTool(PermissionLevel.READ_ONLY);
        ToolRegistry registry = new ToolRegistry();
        registry.register(tool);
        ToolExecutor executor = new ToolExecutor(registry, SYNCHRONOUS);

        ToolResult result = executor.runOnClientThread(new ToolCall("id1", tool.id(), new JsonObject()), () -> contextWith(PermissionLevel.READ_ONLY));

        assertTrue(result.success());
        assertTrue(tool.executed);
    }

    @Test
    void aToolThatThrowsNeverCrashesTheExecutorItBecomesAnInternalErrorResult() throws Exception {
        ToolRegistry registry = new ToolRegistry();
        registry.register(throwingTool());

        ToolResult result = run(registry, new ToolCall("id1", "throwing_tool", new JsonObject()), PermissionLevel.READ_ONLY);

        assertFalse(result.success());
        assertEquals(ToolResultCode.INTERNAL_ERROR, result.code());
    }

    @Test
    void runOnClientThreadAlsoConvertsAThrownExceptionToInternalError() {
        ToolRegistry registry = new ToolRegistry();
        registry.register(throwingTool());
        ToolExecutor executor = new ToolExecutor(registry, SYNCHRONOUS);

        ToolResult result = executor.runOnClientThread(new ToolCall("id1", "throwing_tool", new JsonObject()), () -> contextWith(PermissionLevel.READ_ONLY));

        assertFalse(result.success());
        assertEquals(ToolResultCode.INTERNAL_ERROR, result.code());
    }

    @Test
    void aFailedWorldLookupBecomesWorldNotLoadedNotACrash() throws Exception {
        FakeTool tool = new FakeTool(PermissionLevel.READ_ONLY);
        ToolRegistry registry = new ToolRegistry();
        registry.register(tool);
        ToolExecutor executor = new ToolExecutor(registry, SYNCHRONOUS);

        ToolExecutor.ToolContextSupplier noWorld = () -> { throw new IllegalStateException("No world loaded"); };

        ToolResult asyncResult = executor.run(new ToolCall("id1", tool.id(), new JsonObject()), noWorld).get();
        assertFalse(asyncResult.success());
        assertEquals(ToolResultCode.WORLD_NOT_LOADED, asyncResult.code());
        assertFalse(tool.executed);
    }

    @Test
    void runOnClientThreadAlsoReportsWorldNotLoaded() {
        FakeTool tool = new FakeTool(PermissionLevel.READ_ONLY);
        ToolRegistry registry = new ToolRegistry();
        registry.register(tool);
        ToolExecutor executor = new ToolExecutor(registry, SYNCHRONOUS);

        ToolExecutor.ToolContextSupplier noWorld = () -> { throw new IllegalStateException("No world loaded"); };

        ToolResult result = executor.runOnClientThread(new ToolCall("id1", tool.id(), new JsonObject()), noWorld);
        assertFalse(result.success());
        assertEquals(ToolResultCode.WORLD_NOT_LOADED, result.code());
        assertFalse(tool.executed);
    }

    private static MineBroTool throwingTool() {
        return new MineBroTool() {
            @Override public String id() { return "throwing_tool"; }
            @Override public ToolSchema schema() { return new ToolSchema("throwing_tool", "", new JsonObject()); }
            @Override public PermissionLevel requiredPermission() { return PermissionLevel.READ_ONLY; }
            @Override public ToolKind kind() { return ToolKind.READ; }
            @Override public ToolResult execute(ToolCall call, ToolContext ctx) { throw new IllegalStateException("boom"); }
        };
    }

    private static final class FakeTool implements MineBroTool {
        private final PermissionLevel required;
        boolean executed = false;

        FakeTool(PermissionLevel required) {
            this.required = required;
        }

        @Override public String id() { return "fake_tool"; }
        @Override public ToolSchema schema() { return new ToolSchema("fake_tool", "", new JsonObject()); }
        @Override public PermissionLevel requiredPermission() { return required; }
        @Override public ToolKind kind() { return ToolKind.READ; }

        @Override
        public ToolResult execute(ToolCall call, ToolContext ctx) {
            executed = true;
            return ToolResult.ok(call.id(), id(), "fake ok", new JsonObject());
        }
    }
}
