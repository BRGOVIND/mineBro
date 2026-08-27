package com.minebro.recipe;

import net.minecraft.resources.ResourceLocation;

import java.util.List;

public record CraftPlan(
        boolean craftable,
        ResourceLocation recipeId,
        boolean fitsInventoryGrid,
        List<ItemAmount> required,
        List<ItemAmount> have,
        List<ItemAmount> missing,
        int maxCraftable
) {
    public static CraftPlan noRecipe() {
        return new CraftPlan(false, null, false, List.of(), List.of(), List.of(), 0);
    }
}
