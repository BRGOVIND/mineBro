package com.minebro.client.command;

import com.google.gson.JsonObject;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.minebro.MineBro;
import com.minebro.client.MineBroClient;
import com.minebro.client.screen.MineBroChatScreen;
import com.minebro.client.screen.MineBroConfigScreen;
import com.minebro.client.state.ClientRuntime;
import com.minebro.provider.http.SecretRedactor;
import com.minebro.provider.model.ToolCall;
import com.minebro.tool.ToolResult;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/**
 * Client-only by design (architecture doc §19, and the fix for the bug Phase 1 flagged: the
 * template registered {@code /minebro} server-side via {@code CommandRegistrationCallback},
 * which is silently absent on remote servers even in the integrated-server sense).
 */
public final class MineBroClientCommands {

    public static void register() {
        ClientCommandRegistrationCallback.EVENT.register(MineBroClientCommands::build);
    }

    private static void build(CommandDispatcher<FabricClientCommandSource> dispatcher, Object registryAccess) {
        dispatcher.register(ClientCommandManager.literal("minebro")
                .executes(MineBroClientCommands::hello)
                .then(ClientCommandManager.literal("inventory").executes(MineBroClientCommands::inventory))
                .then(ClientCommandManager.literal("status").executes(MineBroClientCommands::status))
                .then(ClientCommandManager.literal("settings").executes(MineBroClientCommands::settings))
                .then(ClientCommandManager.literal("models").executes(MineBroClientCommands::models))
                .then(ClientCommandManager.literal("stop").executes(MineBroClientCommands::stop))
                .then(ClientCommandManager.literal("recipe")
                        .then(ClientCommandManager.argument("item", StringArgumentType.greedyString())
                                .executes(MineBroClientCommands::recipe)))
                .then(ClientCommandManager.literal("craft")
                        .then(ClientCommandManager.argument("item", StringArgumentType.greedyString())
                                .executes(MineBroClientCommands::craft)))
                .then(ClientCommandManager.argument("question", StringArgumentType.greedyString())
                        .executes(MineBroClientCommands::ask)));
    }

    /**
     * The secondary entry point to the chat panel (design doc §5.1 - the {@code B} keybind is
     * primary). Deferred a tick with {@link Minecraft#execute}: Brigadier runs this while the
     * vanilla chat screen is still open, and closing that screen right afterwards would set the
     * screen to {@code null} on top of ours.
     */
    private static int hello(CommandContext<FabricClientCommandSource> ctx) {
        feedback(ctx, Component.literal("MineBro is alive. Try: /minebro <question>, /minebro inventory, /minebro recipe <item>, /minebro craft <item>, /minebro status, /minebro settings, /minebro models, /minebro stop"));
        Minecraft.getInstance().execute(() -> {
            // Stamped here, inside the deferred block, so the tween starts when the panel actually
            // appears rather than a tick earlier - and so this entry point animates exactly like
            // the B keybind instead of snapping open.
            MineBroClient.avatarAnimation().onPanelOpen();
            Minecraft.getInstance().setScreen(new MineBroChatScreen());
        });
        return 1;
    }

    private static int ask(CommandContext<FabricClientCommandSource> ctx) {
        String question = StringArgumentType.getString(ctx, "question");
        FabricClientCommandSource source = ctx.getSource();

        // An empty result means this request was cancelled or superseded by a newer one - printing
        // it would drop a stale answer into chat under whatever the player asked most recently.
        MineBroClient.conversationController().submit(question, MineBroClient.avatarController())
                .thenAccept(maybeAnswer -> maybeAnswer.ifPresent(answer -> Minecraft.getInstance().execute(() ->
                        source.sendFeedback(gold(answer)))));
        return 1;
    }

    private static int inventory(CommandContext<FabricClientCommandSource> ctx) {
        return runDirectTool(ctx, "get_inventory", new JsonObject());
    }

    private static int recipe(CommandContext<FabricClientCommandSource> ctx) {
        String raw = StringArgumentType.getString(ctx, "item").trim();
        JsonObject args = new JsonObject();
        args.addProperty("item", raw);
        return runDirectTool(ctx, "get_recipe", args);
    }

    private static int craft(CommandContext<FabricClientCommandSource> ctx) {
        String raw = StringArgumentType.getString(ctx, "item");
        ItemAndQuantity parsed = parseItemAndQuantity(raw);
        JsonObject args = new JsonObject();
        args.addProperty("item", parsed.item());
        args.addProperty("quantity", parsed.quantity());
        return runDirectTool(ctx, "craft_item", args);
    }

    /**
     * The {@code item} argument is a single greedy string (see {@link #build}) rather than
     * item+quantity as two Brigadier nodes, specifically so a malformed or multi-word attempt at
     * an item id (e.g. "wooden pickaxe" instead of "minecraft:wooden_pickaxe") can never produce
     * a raw Brigadier "expected whitespace to end one argument" parser error - it always reaches
     * this method, which fails cleanly through the tool layer instead.
     */
    static ItemAndQuantity parseItemAndQuantity(String raw) {
        String trimmed = raw.trim();
        int lastSpace = trimmed.lastIndexOf(' ');
        if (lastSpace > 0) {
            String possibleQuantity = trimmed.substring(lastSpace + 1);
            String rest = trimmed.substring(0, lastSpace).trim();
            try {
                int quantity = Integer.parseInt(possibleQuantity);
                if (quantity >= 1 && quantity <= 64 && !rest.isEmpty()) {
                    return new ItemAndQuantity(rest, quantity);
                }
            } catch (NumberFormatException ignored) {
                // not a trailing quantity - the whole trimmed string is the item text
            }
        }
        return new ItemAndQuantity(trimmed, 1);
    }

    record ItemAndQuantity(String item, int quantity) {}

    private static int status(CommandContext<FabricClientCommandSource> ctx) {
        var config = MineBro.configManager().get();
        FabricClientCommandSource source = ctx.getSource();
        boolean isOllama = config.providerId.equals("ollama");
        source.sendFeedback(gold("Provider: " + config.providerId + " (" + (isOllama ? "local" : "cloud/custom") + ")"
                + " - " + (isOllama ? config.ollamaModel + " @ " + config.ollamaEndpoint
                                    : config.openAiCompatModel + " @ " + config.openAiCompatEndpoint)));
        if (!isOllama) {
            boolean hasKey = !MineBroClient.provider().capabilities().requiresApiKey()
                    || !config.resolveApiKey().isBlank();
            source.sendFeedback(Component.literal(hasKey ? "API key: configured" : "API key: not set - this provider will likely reject requests")
                    .withStyle(hasKey ? ChatFormatting.GRAY : ChatFormatting.YELLOW));
        }
        source.sendFeedback(Component.literal("Checking connection...").withStyle(ChatFormatting.GRAY));
        MineBroClient.provider().health().thenAccept(report -> Minecraft.getInstance().execute(() ->
                source.sendFeedback(report.reachable() && report.modelAvailable()
                        ? Component.literal("Online - " + report.detail()).withStyle(ChatFormatting.GREEN)
                        : Component.literal("Offline - " + report.detail()).withStyle(ChatFormatting.RED))));
        return 1;
    }

    private static int settings(CommandContext<FabricClientCommandSource> ctx) {
        var config = MineBro.configManager().get();
        FabricClientCommandSource source = ctx.getSource();
        boolean isOllama = config.providerId.equals("ollama");
        source.sendFeedback(gold("MineBro config file: " + MineBro.configManager().path()));
        source.sendFeedback(Component.literal("provider=" + config.providerId + " (" + (isOllama ? "local" : "cloud/custom") + ")"
                + "  model=" + (isOllama ? config.ollamaModel : config.openAiCompatModel)));
        if (!isOllama) {
            source.sendFeedback(Component.literal("endpoint=" + config.openAiCompatEndpoint
                    + "  apiKey=" + (config.resolveApiKey().isBlank() ? "(not set)" : SecretRedactor.mask(config.resolveApiKey()))));
        }
        source.sendFeedback(Component.literal("permission=" + config.permissionLevel + "  maxToolIterations=" + config.maxToolIterations));
        source.sendFeedback(Component.literal("Opening the settings screen - provider, endpoint, model, API key and permission level are editable there and apply without a restart. Everything else stays hand-editable in the file above.").withStyle(ChatFormatting.GRAY));
        // Deferred for the same reason as hello(): Brigadier runs this while the vanilla chat
        // screen is still open, and closing that screen afterwards would null out ours.
        Minecraft.getInstance().execute(() -> Minecraft.getInstance().setScreen(new MineBroConfigScreen()));
        return 1;
    }

    private static int models(CommandContext<FabricClientCommandSource> ctx) {
        FabricClientCommandSource source = ctx.getSource();
        source.sendFeedback(Component.literal("Checking available models...").withStyle(ChatFormatting.GRAY));
        MineBroClient.provider().listModels().thenAccept(models -> Minecraft.getInstance().execute(() -> {
            if (models.isEmpty()) {
                source.sendFeedback(Component.literal(
                        "No models reported - this provider may not support model discovery, or is unreachable. "
                                + "You can still set any model name directly in the config file.").withStyle(ChatFormatting.GRAY));
                return;
            }
            source.sendFeedback(gold("Available models:"));
            for (var model : models) {
                source.sendFeedback(Component.literal("  " + model.id()));
            }
        }));
        return 1;
    }

    private static int stop(CommandContext<FabricClientCommandSource> ctx) {
        MineBroClient.conversationController().cancel();
        feedback(ctx, Component.literal("Stopped.").withStyle(ChatFormatting.GRAY));
        return 1;
    }

    /**
     * Routes through {@link com.minebro.tool.ToolExecutor#runOnClientThread} - the same gate
     * {@code AgentLoop} uses via {@code ToolExecutor.run} - instead of reimplementing permission
     * checks and exception handling here. This command handler is itself always invoked on the
     * client thread by Brigadier, so the synchronous entry point applies directly; the assertion
     * below exists so a future caller that violates that assumption fails loudly here rather
     * than corrupting state silently.
     */
    private static int runDirectTool(CommandContext<FabricClientCommandSource> ctx, String toolId, JsonObject args) {
        if (!Minecraft.getInstance().isSameThread()) {
            throw new IllegalStateException("runDirectTool must be called from the client thread");
        }
        FabricClientCommandSource source = ctx.getSource();
        ClientRuntime runtime = MineBroClient.runtime();

        ToolResult result = MineBroClient.toolExecutor().runOnClientThread(
                new ToolCall("cmd", toolId, args),
                () -> runtime.buildToolContext().orElseThrow(() -> new IllegalStateException("No world loaded")));

        source.sendFeedback(result.success()
                ? gold(result.reason())
                : Component.literal(result.reason()).withStyle(ChatFormatting.RED));
        return result.success() ? 1 : 0;
    }

    private static void feedback(CommandContext<FabricClientCommandSource> ctx, Component component) {
        ctx.getSource().sendFeedback(component);
    }

    private static Component gold(String text) {
        return Component.literal("[MineBro] ").withStyle(ChatFormatting.GOLD).append(Component.literal(text).withStyle(ChatFormatting.RESET));
    }

    private MineBroClientCommands() {}
}
