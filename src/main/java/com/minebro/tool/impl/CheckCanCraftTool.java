package com.minebro.tool.impl;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.minebro.provider.model.ToolCall;
import com.minebro.provider.model.ToolSchema;
import com.minebro.recipe.CraftPlan;
import com.minebro.recipe.CraftabilitySolver;
import com.minebro.recipe.ItemAmount;
import com.minebro.recipe.ItemDisplay;
import com.minebro.tool.InventoryCounter;
import com.minebro.tool.ItemIdParser;
import com.minebro.tool.MineBroTool;
import com.minebro.tool.PermissionLevel;
import com.minebro.tool.ToolArgs;
import com.minebro.tool.ToolContext;
import com.minebro.tool.ToolKind;
import com.minebro.tool.ToolResult;
import com.minebro.tool.ToolResultCode;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Map;

/**
 * Deterministic solver, never the model - see architecture doc §8.4/§9 L1. The model's job is to
 * phrase the already-computed boolean and delta, not to compute either.
 */
public final class CheckCanCraftTool implements MineBroTool {

    @Override
    public String id() {
        return "check_can_craft";
    }

    @Override
    public ToolSchema schema() {
        return ToolSchemas.itemAndQuantity("check_can_craft",
                "Determine whether the player can craft an item RIGHT NOW using items currently in their inventory. Always call this instead of guessing.");
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
        int quantity = ToolArgs.getInt(call.arguments(), "quantity").orElse(1);
        if (quantity < 1 || quantity > 64) {
            return ToolResult.fail(call.id(), id(), ToolResultCode.INVALID_ARGUMENTS, "Quantity must be between 1 and 64.");
        }

        var parsed = ItemIdParser.parse(itemArg.get());
        if (parsed.isEmpty()) {
            List<String> suggestions = ItemIdParser.suggest(itemArg.get());
            String suggestionText = suggestions.isEmpty() ? "" : " Did you mean: " + String.join(", ", suggestions) + "?";
            return ToolResult.fail(call.id(), id(), ToolResultCode.UNKNOWN_ITEM,
                    "I don't recognize that item: \"" + itemArg.get() + "\"." + suggestionText);
        }
        ResourceLocation itemId = parsed.get().id();

        Map<ResourceLocation, Integer> inventory = InventoryCounter.aggregate(ctx.player().getInventory());
        CraftPlan plan = CraftabilitySolver.solve(itemId, quantity, inventory, ctx.recipes(), ctx.level().registryAccess());

        if (plan.recipeId() == null) {
            return ToolResult.fail(call.id(), id(), ToolResultCode.NO_RECIPE,
                    "There's no crafting-table recipe for " + itemId + ".");
        }

        JsonObject data = toJson(plan);
        String itemName = ItemDisplay.name(itemId.toString());

        if (!plan.craftable()) {
            return ToolResult.fail(call.id(), id(), ToolResultCode.MISSING_INGREDIENTS,
                    describeMissing(itemName, plan), data);
        }
        if (!plan.fitsInventoryGrid()) {
            data.addProperty("needsCraftingTable", true);
            return ToolResult.ok(call.id(), id(),
                    "I can make that, but " + itemName + " needs a crafting table (3x3 grid) - it won't fit your personal inventory grid.",
                    data);
        }
        return ToolResult.ok(call.id(), id(), "Yes - you have everything needed for " + itemName + ".", data);
    }

    private static String describeMissing(String itemName, CraftPlan plan) {
        StringBuilder sb = new StringBuilder("You can't craft " + itemName + " yet. You need: ");
        List<ItemAmount> missing = plan.missing();
        for (int i = 0; i < missing.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append(ItemDisplay.describe(missing.get(i)));
        }
        return sb.toString();
    }

    private static JsonObject toJson(CraftPlan plan) {
        JsonObject data = new JsonObject();
        data.addProperty("craftable", plan.craftable());
        data.addProperty("maxCraftable", plan.maxCraftable());
        data.addProperty("fitsInventoryGrid", plan.fitsInventoryGrid());
        data.add("required", amountsToJson(plan.required()));
        data.add("have", amountsToJson(plan.have()));
        data.add("missing", amountsToJson(plan.missing()));
        return data;
    }

    private static JsonArray amountsToJson(List<ItemAmount> amounts) {
        JsonArray arr = new JsonArray();
        for (ItemAmount a : amounts) {
            JsonObject o = new JsonObject();
            o.addProperty("item", a.itemId());
            o.addProperty("count", a.count());
            o.addProperty("displayName", ItemDisplay.name(a.itemId()));
            if (a.hasAlternatives()) {
                JsonArray alt = new JsonArray();
                a.alternatives().forEach(alt::add);
                o.add("anyOf", alt);
            }
            arr.add(o);
        }
        return arr;
    }
}
