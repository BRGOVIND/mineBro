package com.minebro.recipe;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Fixes the "1 diamond, 1 diamond, 1 diamond, 2 sticks" bug: a recipe with 3 identical diamond
 * ingredient slots must collapse to one group of count 3, not three groups of count 1. Two slots
 * are "the same ingredient" iff they accept exactly the same set of items - order-independent, so
 * a slot accepting {oak_planks, spruce_planks} never merges with one accepting only {oak_planks},
 * even though the sets overlap. Deliberately Minecraft-free: takes and returns plain strings so
 * this can be unit tested without any game state.
 */
public final class IngredientAggregator {

    public record Group(List<String> acceptableItemIds, int count) {
        public boolean isSingleItem() {
            return acceptableItemIds.size() == 1;
        }
    }

    /** One entry per non-empty ingredient slot; each entry is the (possibly length-1) set of item ids that satisfy it. */
    public static List<Group> aggregate(List<List<String>> slots) {
        Map<List<String>, Integer> counts = new LinkedHashMap<>();
        Map<List<String>, List<String>> firstSeenOrder = new LinkedHashMap<>();

        for (List<String> slot : slots) {
            List<String> dedupedInOrder = slot.stream().distinct().toList();
            if (dedupedInOrder.isEmpty()) {
                continue;
            }
            List<String> canonicalKey = dedupedInOrder.stream().sorted().toList();
            counts.merge(canonicalKey, 1, Integer::sum);
            firstSeenOrder.putIfAbsent(canonicalKey, dedupedInOrder);
        }

        List<Group> result = new ArrayList<>();
        for (Map.Entry<List<String>, Integer> entry : counts.entrySet()) {
            result.add(new Group(firstSeenOrder.get(entry.getKey()), entry.getValue()));
        }
        return result;
    }

    private IngredientAggregator() {}
}
