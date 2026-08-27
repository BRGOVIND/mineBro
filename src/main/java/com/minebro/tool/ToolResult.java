package com.minebro.tool;

import com.google.gson.JsonObject;

/**
 * {@code success} is always present so the model never has to infer it. {@code reason} is a
 * short player-facing sentence - it doubles as the message shown in chat if the model fails to
 * produce a coherent final answer. {@code data} is machine-checkable structure, never prose.
 */
public record ToolResult(
        String id,
        String tool,
        boolean success,
        ToolResultCode code,
        String reason,
        JsonObject data
) {
    public static ToolResult ok(String id, String tool, String reason, JsonObject data) {
        return new ToolResult(id, tool, true, ToolResultCode.OK, reason, data == null ? new JsonObject() : data);
    }

    public static ToolResult fail(String id, String tool, ToolResultCode code, String reason) {
        return new ToolResult(id, tool, false, code, reason, new JsonObject());
    }

    public static ToolResult fail(String id, String tool, ToolResultCode code, String reason, JsonObject data) {
        return new ToolResult(id, tool, false, code, reason, data);
    }

    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("id", id);
        json.addProperty("tool", tool);
        json.addProperty("success", success);
        json.addProperty("code", code.name());
        json.addProperty("reason", reason);
        json.add("data", data);
        return json;
    }
}
