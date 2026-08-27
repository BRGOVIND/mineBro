package com.minebro.agent.codec;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;

import java.util.Optional;

/**
 * The paranoid parser. Small local models wrap tool calls in markdown fences, add a sentence of
 * preamble, or trail off with commentary after the JSON. This extracts the first syntactically
 * valid {@code {"tool": ..., "arguments": {...}}} object it can find, or gives up cleanly -
 * never throws, never guesses at a repair.
 */
public final class JsonExtractor {

    public static Optional<JsonObject> extractToolCall(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        String stripped = stripFences(raw).trim();

        Optional<JsonObject> whole = tryParseObject(stripped);
        if (whole.isPresent() && looksLikeToolCall(whole.get())) {
            return whole;
        }

        int start = stripped.indexOf('{');
        while (start >= 0) {
            Optional<String> candidate = extractBalanced(stripped, start);
            if (candidate.isPresent()) {
                Optional<JsonObject> parsed = tryParseObject(candidate.get());
                if (parsed.isPresent() && looksLikeToolCall(parsed.get())) {
                    return parsed;
                }
            }
            start = stripped.indexOf('{', start + 1);
        }
        return Optional.empty();
    }

    private static boolean looksLikeToolCall(JsonObject obj) {
        return obj.has("tool") && isJsonString(obj.get("tool")) && obj.has("arguments") && obj.get("arguments").isJsonObject();
    }

    private static boolean isJsonString(JsonElement el) {
        return el.isJsonPrimitive() && el.getAsJsonPrimitive().isString();
    }

    private static Optional<JsonObject> tryParseObject(String text) {
        try {
            JsonElement el = JsonParser.parseString(text);
            return el.isJsonObject() ? Optional.of(el.getAsJsonObject()) : Optional.empty();
        } catch (JsonSyntaxException | IllegalStateException e) {
            return Optional.empty();
        }
    }

    private static Optional<String> extractBalanced(String text, int start) {
        int depth = 0;
        boolean inString = false;
        boolean escape = false;
        for (int i = start; i < text.length(); i++) {
            char c = text.charAt(i);
            if (inString) {
                if (escape) {
                    escape = false;
                } else if (c == '\\') {
                    escape = true;
                } else if (c == '"') {
                    inString = false;
                }
                continue;
            }
            if (c == '"') {
                inString = true;
            } else if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0) {
                    return Optional.of(text.substring(start, i + 1));
                }
            }
        }
        return Optional.empty();
    }

    private static String stripFences(String text) {
        String result = text.trim();
        if (result.startsWith("```")) {
            int firstNewline = result.indexOf('\n');
            if (firstNewline >= 0) {
                result = result.substring(firstNewline + 1);
            }
            int lastFence = result.lastIndexOf("```");
            if (lastFence >= 0) {
                result = result.substring(0, lastFence);
            }
        }
        return result;
    }

    private JsonExtractor() {}
}
