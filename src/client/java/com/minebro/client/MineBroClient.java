package com.minebro.client;

import com.minebro.MineBro;
import com.minebro.agent.AgentLoop;
import com.minebro.agent.ConversationController;
import com.minebro.agent.codec.JsonPromptToolCallCodec;
import com.minebro.client.command.MineBroClientCommands;
import com.minebro.client.hud.HudAvatarController;
import com.minebro.client.hud.MineBroHud;
import com.minebro.client.input.MineBroKeybinds;
import com.minebro.client.state.ClientRuntime;
import com.minebro.client.thread.ClientThreadExecutor;
import com.minebro.provider.AIProvider;
import com.minebro.provider.ProviderRegistry;
import com.minebro.tool.ToolExecutor;
import net.fabricmc.api.ClientModInitializer;

public class MineBroClient implements ClientModInitializer {

    private static ClientRuntime runtime;
    private static AIProvider provider;
    private static AgentLoop agentLoop;
    private static ConversationController conversationController;
    private static HudAvatarController avatarController;
    private static ToolExecutor toolExecutor;

    @Override
    public void onInitializeClient() {
        runtime = new ClientRuntime();
        provider = ProviderRegistry.create(MineBro.configManager().get());

        toolExecutor = new ToolExecutor(MineBro.toolRegistry(), new ClientThreadExecutor());
        agentLoop = new AgentLoop(
                provider, toolExecutor, new JsonPromptToolCallCodec(), MineBro.configManager().get(),
                () -> runtime.buildToolContext().orElseThrow(() -> new IllegalStateException("No world loaded")));

        conversationController = new ConversationController(
                agentLoop, MineBro.toolRegistry(), MineBro.configManager().get().permissionLevel,
                () -> runtime.buildSnapshotJson());

        avatarController = new HudAvatarController();
        new MineBroHud(avatarController).register();

        MineBroKeybinds.register();
        MineBroClientCommands.register();

        MineBro.LOGGER.info("MineBro client ready (provider: {})", provider.displayName());
    }

    public static ClientRuntime runtime() {
        return runtime;
    }

    public static AIProvider provider() {
        return provider;
    }

    public static AgentLoop agentLoop() {
        return agentLoop;
    }

    /**
     * Swaps in a provider built from freshly-saved config (MineBroConfigScreen's Save). Both places
     * that hold a provider reference have to be updated together: this class's static field, which
     * {@code /minebro status} and {@code /minebro models} read, and the running {@link AgentLoop},
     * which is what actually issues chat requests. Updating only one of them would leave the
     * settings screen reporting a provider the agent isn't using.
     */
    public static void applyProvider(AIProvider newProvider) {
        provider = newProvider;
        if (agentLoop != null) {
            agentLoop.setProvider(newProvider);
        }
        MineBro.LOGGER.info("MineBro provider switched to {}", newProvider.displayName());
    }

    public static ConversationController conversationController() {
        return conversationController;
    }

    public static HudAvatarController avatarController() {
        return avatarController;
    }

    public static ToolExecutor toolExecutor() {
        return toolExecutor;
    }
}
