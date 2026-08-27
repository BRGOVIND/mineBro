package com.minebro.tool.impl;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.minebro.provider.model.ToolCall;
import com.minebro.provider.model.ToolSchema;
import com.minebro.tool.MineBroTool;
import com.minebro.tool.PermissionLevel;
import com.minebro.tool.ToolContext;
import com.minebro.tool.ToolKind;
import com.minebro.tool.ToolResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodData;

public final class GetPlayerStatusTool implements MineBroTool {

    @Override
    public String id() {
        return "get_player_status";
    }

    @Override
    public ToolSchema schema() {
        return ToolSchemas.noArgs("get_player_status",
                "Get the player's current health, food, XP, and active effects. Always call this instead of guessing.");
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
        Player player = ctx.player();
        FoodData food = player.getFoodData();

        JsonObject data = new JsonObject();
        data.addProperty("health", player.getHealth());
        data.addProperty("maxHealth", player.getMaxHealth());
        data.addProperty("food", food.getFoodLevel());
        data.addProperty("saturation", food.getSaturationLevel());
        data.addProperty("xpLevel", player.experienceLevel);

        JsonArray effects = new JsonArray();
        for (MobEffectInstance effect : player.getActiveEffects()) {
            effects.add(effect.getEffect().value().getDescriptionId().replace("effect.minecraft.", "") + " " + (effect.getAmplifier() + 1));
        }
        data.add("effects", effects);

        String reason = String.format(java.util.Locale.ROOT, "Health %.0f/%.0f, food %d/20.",
                player.getHealth(), player.getMaxHealth(), food.getFoodLevel());
        return ToolResult.ok(call.id(), id(), reason, data);
    }
}
