package com.minebro.tool.impl;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.minebro.provider.model.ToolCall;
import com.minebro.provider.model.ToolSchema;
import com.minebro.recipe.IngredientAggregator;
import com.minebro.recipe.ItemDisplay;
import com.minebro.tool.ItemIdParser;
import com.minebro.tool.MineBroTool;
import com.minebro.tool.PermissionLevel;
import com.minebro.tool.ToolArgs;
import com.minebro.tool.ToolContext;
import com.minebro.tool.ToolKind;
import com.minebro.tool.ToolResult;
import com.minebro.tool.ToolResultCode;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;

import java.util.ArrayList;
import java.util.List;

public final class GetRecipeTool implements MineBroTool {

    @Override
    public String id() {
        return "get_recipe";
    }

    @Override
    public ToolSchema schema() {
        return ToolSchemas.oneStringArg("get_recipe",
                "Look up the crafting recipe(s) that produce a given item. Never invent a recipe yourself - call this instead.",
                "item", "Namespaced item id, e.g. minecraft:iron_pickaxe");
    }

    @Override
    public PermissionLevel requiredPermission() {
        return PermissionLevel.READ_ONLY;
    }

    @Override
    public ToolKind kind() {
        return ToolKind.READ;
    }

    @Override
    public ToolResult execute(ToolCall call, ToolContext ctx) {
        var itemArg = ToolArgs.getString(call.arguments(), "item");
        if (itemArg.isEmpty()) {
            return ToolResult.fail(call.id(), id(), ToolResultCode.INVALID_ARGUMENTS, "Missing or invalid \"item\" argument.");
        }

        var parsed = ItemIdParser.parse(itemArg.get());
        if (parsed.isEmpty()) {
            return unknownItem(call, itemArg.get());
        }
        ResourceLocation itemId = parsed.get().id();

        List<RecipeHolder<CraftingRecipe>> recipes = ctx.recipes().byOutput(itemId);
        if (recipes.isEmpty()) {
            return ToolResult.fail(call.id(), id(), ToolResultCode.NO_RECIPE,
                    "There's no crafting-table recipe for " + itemId + " (it may come from smelting, a loot table, or trading instead).");
        }

        JsonArray recipeArr = new JsonArray();
        List<String> primaryRecipeLines = null;

        for (RecipeHolder<CraftingRecipe> holder : recipes) {
            CraftingRecipe recipe = holder.value();

            List<List<String>> slots = new ArrayList<>();
            for (Ingredient ingredient : recipe.getIngredients()) {
                if (ingredient.isEmpty()) {
                    continue;
                }
                List<String> acceptable = java.util.Arrays.stream(ingredient.getItems())
                        .map(ItemStack::getItem)
                        .distinct()
                        .map(item -> BuiltInRegistries.ITEM.getKey(item).toString())
                        .toList();
                if (!acceptable.isEmpty()) {
                    slots.add(acceptable);
                }
            }
            List<IngredientAggregator.Group> groups = IngredientAggregator.aggregate(slots);

            JsonObject r = new JsonObject();
            r.addProperty("recipeId", holder.id().toString());
            JsonArray ingredientsJson = new JsonArray();
            List<String> lines = new ArrayList<>();
            for (IngredientAggregator.Group group : groups) {
                String primaryId = group.acceptableItemIds().get(0);
                JsonObject ingJson = new JsonObject();
                ingJson.addProperty("count", group.count());
                ingJson.addProperty("displayName", ItemDisplay.name(primaryId));
                if (group.isSingleItem()) {
                    ingJson.addProperty("item", primaryId);
                    lines.add(group.count() + "x " + ItemDisplay.name(primaryId));
                } else {
                    JsonArray anyOf = new JsonArray();
                    group.acceptableItemIds().forEach(anyOf::add);
                    ingJson.add("anyOf", anyOf);
                    lines.add(group.count() + "x any of " + ItemDisplay.summarize(group.acceptableItemIds()));
                }
                ingredientsJson.add(ingJson);
            }
            r.add("ingredients", ingredientsJson);
            recipeArr.add(r);

            if (primaryRecipeLines == null) {
                primaryRecipeLines = lines;
            }
        }

        JsonObject data = new JsonObject();
        data.add("recipes", recipeArr);

        String ingredientSummary = primaryRecipeLines == null || primaryRecipeLines.isEmpty()
                ? "no ingredients"
                : String.join(" + ", primaryRecipeLines);
        String reason = recipes.size() == 1
                ? ItemDisplay.name(itemId.toString()) + " needs: " + ingredientSummary
                : "Found " + recipes.size() + " recipes for " + ItemDisplay.name(itemId.toString()) + "; the first needs: " + ingredientSummary;

        return ToolResult.ok(call.id(), id(), reason, data);
    }

    private ToolResult unknownItem(ToolCall call, String raw) {
        List<String> suggestions = ItemIdParser.suggest(raw);
        String suggestionText = suggestions.isEmpty() ? "" : " Did you mean: " + String.join(", ", suggestions) + "?";
        return ToolResult.fail(call.id(), id(), ToolResultCode.UNKNOWN_ITEM,
                "I don't recognize that item: \"" + raw + "\"." + suggestionText);
    }
}
