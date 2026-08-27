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
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.phys.Vec3;

public final class GetPositionTool implements MineBroTool {

    @Override
    public String id() {
        return "get_position";
    }

    @Override
    public ToolSchema schema() {
        return ToolSchemas.noArgs("get_position",
                "Get the player's current position, facing direction, dimension, and biome.");
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
        Level level = ctx.level();
        Vec3 pos = player.position();
        BlockPos blockPos = player.blockPosition();
        Direction facing = player.getDirection();

        Holder<Biome> biome = level.getBiomeManager().getBiome(blockPos);
        String biomeId = biome.unwrapKey().map(k -> k.location().toString()).orElse("unknown");

        JsonArray posArr = new JsonArray();
        posArr.add(Math.round(pos.x * 10) / 10.0);
        posArr.add(Math.round(pos.y * 10) / 10.0);
        posArr.add(Math.round(pos.z * 10) / 10.0);

        JsonObject data = new JsonObject();
        data.add("position", posArr);
        data.addProperty("facing", facing.getName());
        data.addProperty("dimension", level.dimension().location().toString());
        data.addProperty("biome", biomeId);

        String reason = "At (%d, %d, %d), facing %s, in %s.".formatted(
                blockPos.getX(), blockPos.getY(), blockPos.getZ(), facing.getName(), biomeId);
        return ToolResult.ok(call.id(), id(), reason, data);
    }
}
