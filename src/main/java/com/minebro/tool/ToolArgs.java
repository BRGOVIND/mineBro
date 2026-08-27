package com.minebro.tool;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.Optional;
import java.util.OptionalInt;

/**
 * Defensive argument reading for tool calls. A model can send a missing key, the wrong JSON
 * type, or a non-numeric string where a number belongs - none of that should ever throw out of a
 * {@code MineBroTool#execute}; it should become a clean {@link ToolResultCode#INVALID_ARGUMENTS}.
 */
public final class ToolArgs {

    public static Optional<String> getString(JsonObject args, String key) {
        if (args == null || !args.has(key)) {
            return Optional.empty();
        }
        JsonElement el = args.get(key);
        if (el.isJsonPrimitive() && el.getAsJsonPrimitive().isString()) {
            return Optional.of(el.getAsString());
        }
        return Optional.empty();
    }

    public static OptionalInt getInt(JsonObject args, String key) {
        if (args == null || !args.has(key)) {
            return OptionalInt.empty();
        }
        JsonElement el = args.get(key);
        if (!el.isJsonPrimitive() || !el.getAsJsonPrimitive().isNumber()) {
            return OptionalInt.empty();
        }
        try {
            double asDouble = el.getAsDouble();
            if (asDouble != Math.floor(asDouble) || Double.isInfinite(asDouble)) {
                return OptionalInt.empty();
            }
            return OptionalInt.of(el.getAsInt());
        } catch (NumberFormatException e) {
            return OptionalInt.empty();
        }
    }

    private ToolArgs() {}
}
