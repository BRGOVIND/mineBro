package com.minebro.client.state;

import com.minebro.MineBro;
import com.minebro.context.ContextBuilder;
import com.minebro.context.SnapshotSerializer;
import com.minebro.recipe.RecipeIndex;
import com.minebro.tool.ServerBridge;
import com.minebro.tool.ToolContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.multiplayer.ClientLevel;

import java.time.Instant;
import java.util.Optional;

/** The thin client-thread seam every common-side piece (agent loop, tools) reaches Minecraft through. */
public final class ClientRuntime {

    private final ServerBridge serverBridge = new IntegratedServerBridge();

    public Optional<ToolContext> buildToolContext() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        ClientLevel level = mc.level;
        if (player == null || level == null) {
            return Optional.empty();
        }
        RecipeIndex recipes = RecipeIndex.build(level);
        return Optional.of(new ToolContext(player, level, recipes, MineBro.configManager().get(), Instant.now(), serverBridge));
    }

    public String buildSnapshotJson() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        ClientLevel level = mc.level;
        if (player == null || level == null) {
            return "{}";
        }
        var snapshot = ContextBuilder.build(player, level, mc.isLocalServer());
        return SnapshotSerializer.toCompactJson(snapshot);
    }

    public boolean inWorld() {
        Minecraft mc = Minecraft.getInstance();
        return mc.player != null && mc.level != null;
    }
}
