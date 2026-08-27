package com.minebro.client.state;

import com.minebro.tool.ServerBridge;
import com.minebro.tool.ToolResult;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

/**
 * Mutating a client-side {@link LocalPlayer}'s inventory directly would just desync from the
 * server; every write goes through the integrated server's own {@link ServerPlayer} instead,
 * on the server thread, exactly like a real (if degenerate, same-JVM) client-server round trip.
 */
public final class IntegratedServerBridge implements ServerBridge {

    @Override
    public boolean isLocalSingleplayer() {
        Minecraft mc = Minecraft.getInstance();
        return mc.isLocalServer() && mc.getSingleplayerServer() != null;
    }

    @Override
    public Optional<CompletableFuture<ToolResult>> runOnIntegratedServer(Function<Player, ToolResult> action) {
        Minecraft mc = Minecraft.getInstance();
        IntegratedServer server = mc.getSingleplayerServer();
        LocalPlayer clientPlayer = mc.player;
        if (!mc.isLocalServer() || server == null || clientPlayer == null) {
            return Optional.empty();
        }
        UUID uuid = clientPlayer.getUUID();

        CompletableFuture<ToolResult> future = new CompletableFuture<>();
        server.execute(() -> {
            try {
                ServerPlayer serverPlayer = server.getPlayerList().getPlayer(uuid);
                if (serverPlayer == null) {
                    future.completeExceptionally(new IllegalStateException("Server-side player not found"));
                    return;
                }
                future.complete(action.apply(serverPlayer));
            } catch (RuntimeException e) {
                future.completeExceptionally(e);
            }
        });
        return Optional.of(future);
    }
}
