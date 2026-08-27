package com.minebro.tool;

import com.minebro.provider.model.ToolCall;
import com.minebro.provider.model.ToolSchema;

public interface MineBroTool {

    String id();

    ToolSchema schema();

    PermissionLevel requiredPermission();

    ToolKind kind();

    /**
     * Client-thread only, called with LIVE state. Side effects allowed for MUTATE/DESTRUCTIVE
     * tools - {@link ToolExecutor} is responsible for permission gating and marshalling this
     * call onto the client thread; the tool itself does not need to worry about threading.
     */
    ToolResult execute(ToolCall call, ToolContext ctx);
}
