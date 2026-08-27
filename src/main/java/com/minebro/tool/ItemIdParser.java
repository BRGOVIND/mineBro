package com.minebro.tool;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Deterministic item-id validation shared by every tool that takes an item argument - never the
 * model's job to decide whether an id is real (architecture doc §9 L2). Accepts a bare path
 * ("oak_planks") as a convenience by defaulting to the minecraft namespace; never silently
 * substitutes a *different* item; suggestions are Levenshtein-nearest real registry ids, purely
 * for the error message, never auto-applied.
 */
public final class ItemIdParser {

    private static final int DEFAULT_SUGGESTION_COUNT = 3;

    public record Result(Item item, ResourceLocation id) {}

    public static Optional<Result> parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        String trimmed = raw.trim();

        ResourceLocation direct = ResourceLocation.tryParse(trimmed);
        if (direct != null && BuiltInRegistries.ITEM.containsKey(direct)) {
            return Optional.of(new Result(BuiltInRegistries.ITEM.get(direct), direct));
        }

        if (!trimmed.contains(":")) {
            ResourceLocation withNamespace = ResourceLocation.tryParse("minecraft:" + trimmed);
            if (withNamespace != null && BuiltInRegistries.ITEM.containsKey(withNamespace)) {
                return Optional.of(new Result(BuiltInRegistries.ITEM.get(withNamespace), withNamespace));
            }
        }
        return Optional.empty();
    }

    public static List<String> suggest(String raw, int max) {
        String needle = (raw == null ? "" : raw)
                .toLowerCase(Locale.ROOT)
                .replace("minecraft:", "");
        return BuiltInRegistries.ITEM.keySet().stream()
                .sorted(Comparator.comparingInt(id -> levenshtein(needle, id.getPath())))
                .limit(max)
                .map(ResourceLocation::toString)
                .toList();
    }

    public static List<String> suggest(String raw) {
        return suggest(raw, DEFAULT_SUGGESTION_COUNT);
    }

    static int levenshtein(String a, String b) {
        int[] prev = new int[b.length() + 1];
        int[] curr = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) {
            prev[j] = j;
        }
        for (int i = 1; i <= a.length(); i++) {
            curr[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                curr[j] = Math.min(Math.min(curr[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost);
            }
            System.arraycopy(curr, 0, prev, 0, curr.length);
        }
        return prev[b.length()];
    }

    private ItemIdParser() {}
}
