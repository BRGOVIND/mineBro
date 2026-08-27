package com.minebro.tool.impl;

import com.google.gson.JsonObject;
import com.minebro.provider.model.ToolCall;
import com.minebro.provider.model.ToolSchema;
import com.minebro.tool.InventoryCounter;
import com.minebro.tool.MineBroTool;
import com.minebro.tool.PermissionLevel;
import com.minebro.tool.ToolContext;
import com.minebro.tool.ToolKind;
import com.minebro.tool.ToolResult;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.Map;

public final class GetInventoryTool implements MineBroTool {

    @Override
    public String id() {
        return "get_inventory";
    }

    @Override
    public ToolSchema schema() {
        return ToolSchemas.noArgs("get_inventory",
                "Get the player's current inventory as aggregated item counts. Always call this instead of guessing what the player has.");
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
        Map<ResourceLocation, Integer> counts = InventoryCounter.aggregate(ctx.player().getInventory());

        JsonObject items = new JsonObject();
        for (Map.Entry<ResourceLocation, Integer> entry : counts.entrySet()) {
            items.addProperty(entry.getKey().toString(), entry.getValue());
        }

        ItemStack mainHand = ctx.player().getMainHandItem();
        ItemStack offHand = ctx.player().getOffhandItem();

        JsonObject data = new JsonObject();
        data.add("items", items);
        data.addProperty("mainHand", mainHand.isEmpty() ? "empty" : itemId(mainHand));
        data.addProperty("offHand", offHand.isEmpty() ? "empty" : itemId(offHand));
        long freeSlots = ctx.player().getInventory().items.stream().filter(ItemStack::isEmpty).count();
        data.addProperty("freeSlots", freeSlots);

        return ToolResult.ok(call.id(), id(), counts.isEmpty() ? "Inventory is empty." : "Inventory retrieved.", data);
    }

    private static String itemId(ItemStack stack) {
        return net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
    }
}
