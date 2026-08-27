package com.minebro.recipe;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.ShapedRecipe;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The model never does arithmetic or set-membership here - this returns the already-computed
 * boolean plus a required/have/missing delta (architecture doc §8.3-8.4). Greedy per-ingredient
 * matching: provably suboptimal for pathological overlapping-tag recipes, correct for
 * essentially every vanilla recipe. Not a constraint solver, intentionally.
 */
public final class CraftabilitySolver {

    public static CraftPlan solve(
            ResourceLocation itemId,
            int quantity,
            Map<ResourceLocation, Integer> inventoryCounts,
            RecipeIndex index,
            HolderLookup.Provider registries
    ) {
        List<RecipeHolder<CraftingRecipe>> candidates = index.byOutput(itemId);
        if (candidates.isEmpty()) {
            return CraftPlan.noRecipe();
        }

        CraftPlan best = null;
        for (RecipeHolder<CraftingRecipe> holder : candidates) {
            CraftPlan plan = solveFor(holder, inventoryCounts, registries);
            if (plan.craftable()) {
                return scaleForQuantity(plan, quantity);
            }
            if (best == null) {
                best = plan;
            }
        }
        return scaleForQuantity(best, quantity);
    }

    /** Shared with {@code CraftItemTool}, which needs the same grouping to actually remove items. */
    public static Map<Ingredient, Integer> requiredCounts(CraftingRecipe recipe) {
        Map<Ingredient, Integer> requiredCounts = new LinkedHashMap<>();
        for (Ingredient ingredient : recipe.getIngredients()) {
            if (ingredient.isEmpty()) {
                continue;
            }
            requiredCounts.merge(ingredient, 1, Integer::sum);
        }
        return requiredCounts;
    }

    private static CraftPlan solveFor(
            RecipeHolder<CraftingRecipe> holder,
            Map<ResourceLocation, Integer> inventoryCounts,
            HolderLookup.Provider registries
    ) {
        CraftingRecipe recipe = holder.value();
        Map<Ingredient, Integer> requiredCounts = requiredCounts(recipe);

        List<ItemAmount> required = new ArrayList<>();
        List<ItemAmount> have = new ArrayList<>();
        List<ItemAmount> missing = new ArrayList<>();
        boolean craftable = true;
        int maxCraftableForRecipe = Integer.MAX_VALUE;

        for (Map.Entry<Ingredient, Integer> entry : requiredCounts.entrySet()) {
            Ingredient ingredient = entry.getKey();
            int needed = entry.getValue();

            List<String> distinctIds = java.util.Arrays.stream(ingredient.getItems())
                    .map(ItemStack::getItem)
                    .distinct()
                    .map(item -> BuiltInRegistries.ITEM.getKey(item).toString())
                    .toList();
            String reportedId = distinctIds.isEmpty() ? "minecraft:air" : distinctIds.get(0);
            List<String> alternatives = distinctIds.size() > 1 ? distinctIds : List.of();

            int available = 0;
            for (Map.Entry<ResourceLocation, Integer> owned : inventoryCounts.entrySet()) {
                Item item = BuiltInRegistries.ITEM.get(owned.getKey());
                if (ingredient.test(new ItemStack(item))) {
                    available += owned.getValue();
                }
            }

            required.add(new ItemAmount(reportedId, needed, alternatives));
            have.add(new ItemAmount(reportedId, available, alternatives));
            if (available < needed) {
                craftable = false;
                missing.add(new ItemAmount(reportedId, needed - available, alternatives));
            }
            maxCraftableForRecipe = Math.min(maxCraftableForRecipe, available / Math.max(1, needed));
        }

        boolean fitsGrid = fitsInventoryGrid(recipe);
        int maxCraftable = craftable ? Math.max(1, maxCraftableForRecipe) : 0;

        return new CraftPlan(
                craftable,
                holder.id(),
                fitsGrid,
                required,
                have,
                missing,
                maxCraftable == Integer.MAX_VALUE ? 0 : maxCraftable
        );
    }

    public static boolean fitsInventoryGrid(CraftingRecipe recipe) {
        if (recipe instanceof ShapedRecipe shaped) {
            return shaped.getWidth() <= 2 && shaped.getHeight() <= 2;
        }
        long nonEmptyIngredients = recipe.getIngredients().stream().filter(i -> !i.isEmpty()).count();
        return nonEmptyIngredients <= 4;
    }

    /**
     * Zips {@code required}/{@code have} by index rather than looking {@code have} up by
     * {@code itemId} - two distinct ingredient groups can (rarely) share the same representative
     * id if the registry happens to order their first acceptable item the same way, and an
     * id-keyed lookup would then silently attribute one ingredient's stock to the other.
     */
    private static CraftPlan scaleForQuantity(CraftPlan plan, int quantity) {
        if (plan == null || quantity <= 1 || plan.recipeId() == null) {
            return plan;
        }
        if (plan.maxCraftable() >= quantity) {
            return plan;
        }
        List<ItemAmount> required = plan.required();
        List<ItemAmount> have = plan.have();
        List<ItemAmount> scaledRequired = new ArrayList<>();
        List<ItemAmount> scaledMissing = new ArrayList<>();
        for (int i = 0; i < required.size(); i++) {
            ItemAmount req = required.get(i);
            ItemAmount owned = have.get(i);
            int scaledCount = req.count() * quantity;
            scaledRequired.add(new ItemAmount(req.itemId(), scaledCount, req.alternatives()));
            if (owned.count() < scaledCount) {
                scaledMissing.add(new ItemAmount(req.itemId(), scaledCount - owned.count(), req.alternatives()));
            }
        }
        return new CraftPlan(false, plan.recipeId(), plan.fitsInventoryGrid(), scaledRequired, have, scaledMissing, plan.maxCraftable());
    }

    private CraftabilitySolver() {}
}
