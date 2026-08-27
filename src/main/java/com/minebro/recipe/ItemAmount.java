package com.minebro.recipe;

import java.util.List;

/**
 * {@code alternatives} is empty for a plain single-item requirement, and holds every acceptable
 * item id (including {@code itemId} itself) when the ingredient is tag-based / accepts more than
 * one item - so callers can render "any of X, Y, Z" instead of falsely naming just one option.
 */
public record ItemAmount(String itemId, int count, List<String> alternatives) {

    public ItemAmount {
        alternatives = alternatives == null ? List.of() : List.copyOf(alternatives);
    }

    public ItemAmount(String itemId, int count) {
        this(itemId, count, List.of());
    }

    public boolean hasAlternatives() {
        return !alternatives.isEmpty();
    }
}
