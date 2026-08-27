package com.minebro.tool;

import com.minebro.config.MineBroConfig;
import com.minebro.recipe.RecipeIndex;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

import java.time.Instant;

/**
 * Carries {@link Player}/{@link Level}, not {@code LocalPlayer}/{@code ClientLevel} - the tool
 * layer stays in {@code main} and stays reusable by a future server-side companion.
 */
public record ToolContext(
        Player player,
        Level level,
        RecipeIndex recipes,
        MineBroConfig config,
        Instant now,
        ServerBridge serverBridge
) {}
