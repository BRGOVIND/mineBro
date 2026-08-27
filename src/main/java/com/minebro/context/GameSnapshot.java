package com.minebro.context;

import java.util.List;
import java.util.Map;

/** Immutable, cheap to build, serialized to compact JSON for the prompt (target < 500 tokens). */
public record GameSnapshot(
        WorldInfo world,
        PlayerInfo player,
        InventoryInfo inventory,
        List<String> stationsInReach
) {
    public record WorldInfo(String dimension, String timeOfDay, boolean raining, boolean singleplayer) {}

    public record PlayerInfo(
            int x, int y, int z, String facing, String biome,
            float health, float maxHealth, int food, int xpLevel
    ) {}

    public record InventoryInfo(Map<String, Integer> items, String mainHand, String offHand, int freeSlots) {}
}
