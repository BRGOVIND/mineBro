package com.minebro.recipe;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Built fresh from {@link RecipeManager} whenever needed (cheap enough - the recipe list rarely
 * exceeds a few thousand entries and this only iterates {@code RecipeType.CRAFTING}). Indexes by
 * output item id since "what can make me an X" is the only lookup direction MineBro needs.
 */
public final class RecipeIndex {

    private final Map<ResourceLocation, List<RecipeHolder<CraftingRecipe>>> byOutput;

    private RecipeIndex(Map<ResourceLocation, List<RecipeHolder<CraftingRecipe>>> byOutput) {
        this.byOutput = byOutput;
    }

    public static RecipeIndex build(Level level) {
        RecipeManager manager = level.getRecipeManager();
        HolderLookup.Provider registries = level.registryAccess();
        Map<ResourceLocation, List<RecipeHolder<CraftingRecipe>>> index = new LinkedHashMap<>();

        for (RecipeHolder<CraftingRecipe> holder : manager.getAllRecipesFor(RecipeType.CRAFTING)) {
            CraftingRecipe recipe = holder.value();
            ItemStack out = recipe.getResultItem(registries);
            if (out.isEmpty()) {
                continue;
            }
            ResourceLocation outId = BuiltInRegistries.ITEM.getKey(out.getItem());
            index.computeIfAbsent(outId, k -> new ArrayList<>()).add(holder);
        }
        return new RecipeIndex(index);
    }

    public List<RecipeHolder<CraftingRecipe>> byOutput(ResourceLocation itemId) {
        return byOutput.getOrDefault(itemId, List.of());
    }
}
