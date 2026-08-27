package com.minebro.tool;

import net.minecraft.world.entity.player.Player;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

/**
 * The one seam {@code craft_item} needs into client-only integrated-server access, kept as an
 * interface here so {@code main} never imports {@code Minecraft}/{@code IntegratedServer}
 * directly. Implemented in {@code client} by bridging to the singleplayer integrated server -
 * mutating the client's own mirror inventory directly would desync, so every write goes through
 * the authoritative server-side player instead.
 */
public interface ServerBridge {

    boolean isLocalSingleplayer();

    /**
     * Runs {@code action} on the integrated server thread against the server-side mirror of the
     * calling player, and completes the returned future with its result. Empty if this isn't a
     * local singleplayer world (multiplayer/dedicated-server crafting execution is out of scope
     * for v1 - see architecture doc §19).
     */
    Optional<CompletableFuture<ToolResult>> runOnIntegratedServer(Function<Player, ToolResult> action);
}
