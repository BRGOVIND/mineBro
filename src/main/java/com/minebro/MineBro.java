package com.minebro;

import com.minebro.config.ConfigManager;
import com.minebro.tool.ToolRegistry;
import com.minebro.tool.impl.CheckCanCraftTool;
import com.minebro.tool.impl.CraftItemTool;
import com.minebro.tool.impl.GetInventoryTool;
import com.minebro.tool.impl.GetPlayerStatusTool;
import com.minebro.tool.impl.GetPositionTool;
import com.minebro.tool.impl.GetRecipeTool;
import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Common-side entrypoint: config and the tool registry only. No commands are registered here -
 * {@code /minebro} is a client-only command (see {@code MineBroClient}), because MineBro is a
 * client-side companion in v1 (architecture doc §19) and a server-registered command would
 * silently be absent on remote servers even in singleplayer's own integrated-server sense.
 */
public class MineBro implements ModInitializer {

    public static final Logger LOGGER = LoggerFactory.getLogger(MineBroConstants.MOD_ID);

    private static ConfigManager configManager;
    private static ToolRegistry toolRegistry;

    @Override
    public void onInitialize() {
        LOGGER.info("MineBro is starting");

        configManager = new ConfigManager(LOGGER);

        toolRegistry = new ToolRegistry();
        toolRegistry.register(new GetPlayerStatusTool());
        toolRegistry.register(new GetInventoryTool());
        toolRegistry.register(new GetPositionTool());
        toolRegistry.register(new GetRecipeTool());
        toolRegistry.register(new CheckCanCraftTool());
        toolRegistry.register(new CraftItemTool());
    }

    public static ConfigManager configManager() {
        return configManager;
    }

    public static ToolRegistry toolRegistry() {
        return toolRegistry;
    }
}
