package com.minebro.tool;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.util.LinkedHashMap;
import java.util.Map;

/** The model must never be asked to add stack counts itself - this is free to do in Java. */
public final class InventoryCounter {

    /**
     * Counts {@code items} (main + hotbar, 36 slots) and {@code offhand} - the same set vanilla's
     * own crafting-grid auto-fill draws from. Deliberately excludes {@code armor}: worn armor
     * isn't spendable crafting material.
     */
    public static Map<ResourceLocation, Integer> aggregate(Inventory inventory) {
        Map<ResourceLocation, Integer> counts = new LinkedHashMap<>();
        addAll(counts, inventory.items);
        addAll(counts, inventory.offhand);
        return counts;
    }

    private static void addAll(Map<ResourceLocation, Integer> counts, Iterable<ItemStack> stacks) {
        for (ItemStack stack : stacks) {
            if (stack.isEmpty()) {
                continue;
            }
            counts.merge(BuiltInRegistries.ITEM.getKey(stack.getItem()), stack.getCount(), Integer::sum);
        }
    }

    private InventoryCounter() {}
}
