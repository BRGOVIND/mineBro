package com.minebro.tool;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * QA issue 2: only the pure string-distance logic is unit-testable here without a full game
 * bootstrap (constructing/looking up real {@code Item}s requires {@code Bootstrap.bootStrap()},
 * which is exactly the "fake Minecraft integration" this test suite avoids - confirmed the hard
 * way: an earlier version of this test touched {@code BuiltInRegistries.ITEM} directly and threw
 * {@code ExceptionInInitializerError}). {@link ItemIdParser#parse}/{@code suggest} against real
 * item ids are covered by the manual verification checklist (recipe/craft commands with garbage
 * input) instead.
 */
class ItemIdParserTest {

    @Test
    void levenshteinDistanceIsZeroForIdenticalStrings() {
        assertEquals(0, ItemIdParser.levenshtein("diamond", "diamond"));
    }

    @Test
    void levenshteinDistanceMatchesKnownEditDistance() {
        assertEquals(3, ItemIdParser.levenshtein("kitten", "sitting"));
    }

    @Test
    void levenshteinDistanceHandlesEmptyStrings() {
        assertEquals(5, ItemIdParser.levenshtein("", "stick"));
        assertEquals(5, ItemIdParser.levenshtein("stick", ""));
        assertEquals(0, ItemIdParser.levenshtein("", ""));
    }

    @Test
    void levenshteinDistanceIsSymmetric() {
        assertEquals(ItemIdParser.levenshtein("wooden", "wood"), ItemIdParser.levenshtein("wood", "wooden"));
    }
}
