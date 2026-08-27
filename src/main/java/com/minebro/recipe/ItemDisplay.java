package com.minebro.recipe;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/** Human-facing text for chat (`reason` fields) - ids stay in `data` for the model/machine, per architecture doc §10.1. */
public final class ItemDisplay {

    private static final int MAX_ALTERNATIVES_SHOWN = 3;

    public static String name(String itemId) {
        ResourceLocation id = ResourceLocation.tryParse(itemId);
        if (id == null || !BuiltInRegistries.ITEM.containsKey(id)) {
            return itemId;
        }
        return new ItemStack(BuiltInRegistries.ITEM.get(id)).getHoverName().getString();
    }

    public static String describe(ItemAmount amount) {
        if (amount.hasAlternatives()) {
            return amount.count() + "x any of " + summarize(amount.alternatives());
        }
        return amount.count() + "x " + name(amount.itemId());
    }

    public static String summarize(List<String> itemIds) {
        List<String> names = itemIds.stream().map(ItemDisplay::name).toList();
        if (names.size() <= MAX_ALTERNATIVES_SHOWN) {
            return String.join(", ", names);
        }
        return String.join(", ", names.subList(0, MAX_ALTERNATIVES_SHOWN))
                + " (+" + (names.size() - MAX_ALTERNATIVES_SHOWN) + " more)";
    }

    private ItemDisplay() {}
}
