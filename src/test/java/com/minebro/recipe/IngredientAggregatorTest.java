package com.minebro.recipe;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Regression coverage for the "1 diamond, 1 diamond, 1 diamond, 2 sticks" bug (QA issue 1). */
class IngredientAggregatorTest {

    @Test
    void threeIdenticalSlotsCollapseToOneGroupOfThree() {
        List<List<String>> slots = List.of(
                List.of("minecraft:diamond"), List.of("minecraft:diamond"), List.of("minecraft:diamond"));
        List<IngredientAggregator.Group> groups = IngredientAggregator.aggregate(slots);
        assertEquals(1, groups.size());
        assertEquals(3, groups.get(0).count());
        assertEquals("minecraft:diamond", groups.get(0).acceptableItemIds().get(0));
        assertTrue(groups.get(0).isSingleItem());
    }

    @Test
    void diamondPickaxeShapeAggregatesToTwoGroups() {
        List<List<String>> slots = List.of(
                List.of("minecraft:diamond"), List.of("minecraft:diamond"), List.of("minecraft:diamond"),
                List.of("minecraft:stick"), List.of("minecraft:stick"));
        List<IngredientAggregator.Group> groups = IngredientAggregator.aggregate(slots);
        assertEquals(2, groups.size());
        assertEquals(3, groups.get(0).count());
        assertEquals("minecraft:diamond", groups.get(0).acceptableItemIds().get(0));
        assertEquals(2, groups.get(1).count());
        assertEquals("minecraft:stick", groups.get(1).acceptableItemIds().get(0));
    }

    @Test
    void distinctSingleItemSlotsNeverMerge() {
        List<List<String>> slots = List.of(List.of("minecraft:diamond"), List.of("minecraft:stick"));
        List<IngredientAggregator.Group> groups = IngredientAggregator.aggregate(slots);
        assertEquals(2, groups.size());
    }

    @Test
    void alternativeIngredientSlotsGroupTogetherWhenSetsMatchExactly() {
        List<String> anyPlank = List.of("minecraft:oak_planks", "minecraft:spruce_planks", "minecraft:birch_planks");
        List<List<String>> slots = List.of(anyPlank, anyPlank, anyPlank);
        List<IngredientAggregator.Group> groups = IngredientAggregator.aggregate(slots);
        assertEquals(1, groups.size());
        assertEquals(3, groups.get(0).count());
        assertTrue(groups.get(0).acceptableItemIds().containsAll(anyPlank));
    }

    @Test
    void alternativeSetOrderDoesNotAffectGrouping() {
        List<String> order1 = List.of("minecraft:oak_planks", "minecraft:spruce_planks");
        List<String> order2 = List.of("minecraft:spruce_planks", "minecraft:oak_planks");
        List<IngredientAggregator.Group> groups = IngredientAggregator.aggregate(List.of(order1, order2));
        assertEquals(1, groups.size());
        assertEquals(2, groups.get(0).count());
    }

    @Test
    void overlappingButDifferentAlternativeSetsNeverIncorrectlyMerge() {
        List<String> anyPlank = List.of("minecraft:oak_planks", "minecraft:spruce_planks");
        List<String> justOak = List.of("minecraft:oak_planks");
        List<IngredientAggregator.Group> groups = IngredientAggregator.aggregate(List.of(anyPlank, justOak));
        assertEquals(2, groups.size(), "a tag-based slot must never merge with a single-item slot even though the sets overlap");
    }

    @Test
    void emptySlotsAreIgnored() {
        List<List<String>> slots = List.of(List.of(), List.of("minecraft:stick"), List.of());
        List<IngredientAggregator.Group> groups = IngredientAggregator.aggregate(slots);
        assertEquals(1, groups.size());
        assertEquals(1, groups.get(0).count());
    }

    @Test
    void groupOrderFollowsFirstOccurrence() {
        List<List<String>> slots = List.of(
                List.of("minecraft:stick"), List.of("minecraft:diamond"), List.of("minecraft:stick"));
        List<IngredientAggregator.Group> groups = IngredientAggregator.aggregate(slots);
        assertEquals("minecraft:stick", groups.get(0).acceptableItemIds().get(0));
        assertEquals("minecraft:diamond", groups.get(1).acceptableItemIds().get(0));
    }
}
