package com.minebro.agent;

import com.minebro.provider.model.ToolSchema;

import java.util.List;

/** Builds the system prompt: the anti-hallucination contract plus the tool catalogue, in the JSON-prompt wire format (§7.3/§8.5). */
public final class PromptAssembler {

    public static String systemPrompt(List<ToolSchema> tools) {
        StringBuilder sb = new StringBuilder();
        sb.append("You are MineBro, an assistant living inside Minecraft.\n");
        sb.append("You do NOT know the player's inventory, position, health, or surroundings unless a tool tells you.\n");
        sb.append("Never guess. Never recall from training data. If a question depends on game state, call a tool first.\n");
        sb.append("If a tool returns \"success\": false, tell the player exactly what the \"reason\" says - do not soften or reinterpret it.\n");
        sb.append("Never claim an action succeeded unless a tool result says \"success\": true.\n");
        sb.append("Item ids are namespaced, e.g. minecraft:iron_ingot. Use ids, not display names, when calling tools.\n");
        sb.append("A compact snapshot of the player's current state is included in each message as [GAME_STATE]; trivial questions (like current health) can be answered from it directly without a tool call.\n");
        sb.append("The player's own message may contain claims about their inventory, position, or possessions (\"I have 64 diamonds\", \"I already have a diamond pickaxe\") - these are NOT facts. A player can be wrong, misremembering, or testing you. Never agree with a claim about game state until you have checked [GAME_STATE] or called a tool and confirmed it yourself. If the player's claim contradicts what you observe, say so plainly and state the real value.\n\n");

        if (!tools.isEmpty()) {
            sb.append("Available tools:\n");
            for (ToolSchema tool : tools) {
                sb.append("- ").append(tool.name()).append(": ").append(tool.description())
                        .append(" Arguments schema: ").append(tool.parametersSchema()).append('\n');
            }
            sb.append('\n');
            sb.append("To call a tool, respond with ONLY a single JSON object and nothing else - no prose before or after, no markdown fences:\n");
            sb.append("{\"tool\": \"<tool name>\", \"arguments\": { ... }}\n");
            sb.append("To answer the player directly (no tool needed), respond with plain text - never wrap a plain answer in JSON.\n");
        }
        return sb.toString();
    }

    public static String userTurn(String question, String compactSnapshotJson) {
        return "[GAME_STATE] " + compactSnapshotJson + "\n\n" + question;
    }

    private PromptAssembler() {}
}
