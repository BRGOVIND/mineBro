package com.minebro.tool.impl;

import com.google.gson.JsonObject;
import com.minebro.provider.model.ToolCall;
import com.minebro.provider.model.ToolSchema;
import com.minebro.recipe.CraftPlan;
import com.minebro.recipe.CraftabilitySolver;
import com.minebro.recipe.ItemDisplay;
import com.minebro.tool.InventoryCounter;
import com.minebro.tool.ItemIdParser;
import com.minebro.tool.MineBroTool;
import com.minebro.tool.PermissionLevel;
import com.minebro.tool.ToolArgs;
import com.minebro.tool.ToolContext;
import com.minebro.tool.ToolKind;
import com.minebro.tool.ToolResult;
import com.minebro.tool.ToolResultCode;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Executes recipes of either grid size: a 2x2 recipe (see
 * {@link CraftabilitySolver#fitsInventoryGrid}) needs nothing, while a 3x3 recipe requires a real
 * crafting table within {@code STATION_SEARCH_RADIUS} - checked cheaply client-side, then
 * re-verified server-side at the moment of mutation, so a table broken in between fails with
 * {@link ToolResultCode#NO_STATION_IN_REACH}. The table is a real, enforced precondition, not a UI
 * to open: MineBro isn't a human clicking through a screen, so it deliberately skips
 * {@code CraftingMenu}/recipe-packet simulation and performs the same direct, verified inventory
 * mutation the 2x2 path already uses. That mutation runs on the integrated server's mirror of the
 * player (never the client-side mirror, which would desync), and only ever reports success after
 * re-reading the server-side inventory and confirming the result count actually increased -
 * "success" means observed, not attempted.
 */
public final class CraftItemTool implements MineBroTool {

    private static final int STATION_SEARCH_RADIUS = 4;
    private static final long CLIENT_THREAD_WAIT_MS = 500;

    @Override
    public String id() {
        return "craft_item";
    }

    @Override
    public ToolSchema schema() {
        return ToolSchemas.itemAndQuantity("craft_item",
                "Craft an item using the player's real inventory and Minecraft's real recipes. Works for recipes that fit a 2x2 grid, and for 3x3 recipes when a crafting table is within a few blocks. Call check_can_craft first if unsure.");
    }

    @Override
    public PermissionLevel requiredPermission() {
        return PermissionLevel.SAFE_ACTIONS;
    }

    @Override
    public ToolKind kind() {
        return ToolKind.MUTATE;
    }

    @Override
    public ToolResult execute(ToolCall call, ToolContext ctx) {
        var itemArg = ToolArgs.getString(call.arguments(), "item");
        if (itemArg.isEmpty()) {
            return ToolResult.fail(call.id(), id(), ToolResultCode.INVALID_ARGUMENTS, "Missing or invalid \"item\" argument.");
        }
        int quantity = ToolArgs.getInt(call.arguments(), "quantity").orElse(1);
        if (quantity < 1 || quantity > 64) {
            return ToolResult.fail(call.id(), id(), ToolResultCode.INVALID_ARGUMENTS, "Quantity must be between 1 and 64.");
        }

        var parsed = ItemIdParser.parse(itemArg.get());
        if (parsed.isEmpty()) {
            List<String> suggestions = ItemIdParser.suggest(itemArg.get());
            String suggestionText = suggestions.isEmpty() ? "" : " Did you mean: " + String.join(", ", suggestions) + "?";
            return ToolResult.fail(call.id(), id(), ToolResultCode.UNKNOWN_ITEM,
                    "I don't recognize that item: \"" + itemArg.get() + "\"." + suggestionText);
        }
        ResourceLocation itemId = parsed.get().id();
        String itemName = ItemDisplay.name(itemId.toString());

        Map<ResourceLocation, Integer> inventory = InventoryCounter.aggregate(ctx.player().getInventory());
        CraftPlan plan = CraftabilitySolver.solve(itemId, quantity, inventory, ctx.recipes(), ctx.level().registryAccess());

        if (plan.recipeId() == null) {
            return ToolResult.fail(call.id(), id(), ToolResultCode.NO_RECIPE, "There's no crafting-table recipe for " + itemName + ".");
        }
        if (!plan.craftable()) {
            return ToolResult.fail(call.id(), id(), ToolResultCode.MISSING_INGREDIENTS,
                    describeMissing(itemName, plan), toDataJson(plan));
        }
        // A 3x3 recipe needs a real crafting table in reach. Cheap client-side fail-fast here; the
        // authoritative re-check happens server-side, right before the mutation.
        boolean requiresStation = !plan.fitsInventoryGrid();
        if (requiresStation && findNearbyCraftingTable(ctx.player(), ctx.level()).isEmpty()) {
            return ToolResult.fail(call.id(), id(), ToolResultCode.NO_STATION_IN_REACH,
                    "I can make that, but " + itemName + " needs a crafting table, and I don't see one nearby.",
                    toDataJson(plan));
        }

        if (!ctx.serverBridge().isLocalSingleplayer()) {
            return ToolResult.fail(call.id(), id(), ToolResultCode.NOT_AVAILABLE_CLIENT_SIDE,
                    "MineBro can only craft items in singleplayer worlds right now, not while connected to another server.");
        }

        RecipeHolder<CraftingRecipe> recipeHolder = findHolder(ctx, plan.recipeId());
        if (recipeHolder == null || recipeHolder.value().isSpecial()) {
            return ToolResult.fail(call.id(), id(), ToolResultCode.INTERNAL_ERROR,
                    "This recipe type isn't supported for automatic crafting yet.");
        }

        int finalQuantity = quantity;
        Optional<CompletableFuture<ToolResult>> future = ctx.serverBridge().runOnIntegratedServer(
                serverPlayer -> craftOnServerThread(call, itemId, recipeHolder, finalQuantity, serverPlayer, ctx.level().registryAccess(), requiresStation));

        if (future.isEmpty()) {
            return ToolResult.fail(call.id(), id(), ToolResultCode.NOT_AVAILABLE_CLIENT_SIDE,
                    "MineBro can only craft items in singleplayer worlds right now.");
        }
        try {
            // 500ms, not the 2s this used to be: this is a same-JVM cross-thread rendezvous with
            // the integrated server's own tick-task queue, which normally drains in well under
            // one tick (50ms). 500ms is generous headroom for that case while capping the client
            // freeze if the server thread is ever genuinely stalled - waiting the full 2s on the
            // render thread for a craft that likely won't finish anyway isn't worth the freeze.
            return future.get().get(CLIENT_THREAD_WAIT_MS, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return ToolResult.fail(call.id(), id(), ToolResultCode.INTERNAL_ERROR, "Crafting was interrupted.");
        } catch (ExecutionException | TimeoutException e) {
            return ToolResult.fail(call.id(), id(), ToolResultCode.INTERNAL_ERROR, "Crafting didn't complete: " + e.getMessage());
        }
    }

    /** Runs on the integrated server thread, against the server-side mirror of the player. */
    private ToolResult craftOnServerThread(
            ToolCall call, ResourceLocation itemId, RecipeHolder<CraftingRecipe> holder,
            int quantity, Player serverPlayer, net.minecraft.core.HolderLookup.Provider registries,
            boolean requiresStation
    ) {
        // The client-side check was only a fail-fast; the server is the side that decides whether
        // this mutation is allowed, and the table may have been broken since then.
        if (requiresStation && findNearbyCraftingTable(serverPlayer, serverPlayer.level()).isEmpty()) {
            return ToolResult.fail(call.id(), id(), ToolResultCode.NO_STATION_IN_REACH,
                    "The crafting table isn't in reach anymore - try again.");
        }

        CraftingRecipe recipe = holder.value();
        Inventory inv = serverPlayer.getInventory();
        Map<Ingredient, Integer> requiredCounts = CraftabilitySolver.requiredCounts(recipe);
        String itemName = ItemDisplay.name(itemId.toString());

        for (Map.Entry<Ingredient, Integer> entry : requiredCounts.entrySet()) {
            int needed = entry.getValue() * quantity;
            int available = countMatching(inv, entry.getKey());
            if (available < needed) {
                return ToolResult.fail(call.id(), id(), ToolResultCode.MISSING_INGREDIENTS,
                        "Ran out of ingredients while crafting " + itemName + " (inventory changed since I checked).");
            }
        }

        int countBefore = countMatchingItem(inv, itemId);

        for (Map.Entry<Ingredient, Integer> entry : requiredCounts.entrySet()) {
            removeMatching(inv, entry.getKey(), entry.getValue() * quantity);
        }
        for (int i = 0; i < quantity; i++) {
            ItemStack result = recipe.getResultItem(registries).copy();
            if (!inv.add(result)) {
                serverPlayer.drop(result, false);
            }
        }
        inv.setChanged();

        int countAfter = countMatchingItem(inv, itemId);
        int gained = countAfter - countBefore;
        if (gained < quantity) {
            return ToolResult.fail(call.id(), id(), ToolResultCode.INTERNAL_ERROR,
                    "Something went wrong crafting " + itemName + " - the item count didn't increase as expected. Check your inventory.");
        }

        JsonObject data = new JsonObject();
        data.addProperty("crafted", itemId.toString());
        data.addProperty("quantity", quantity);
        return ToolResult.ok(call.id(), id(), "Crafted " + quantity + "x " + itemName + ".", data);
    }

    private static int countMatching(Inventory inv, Ingredient ingredient) {
        int total = 0;
        for (ItemStack stack : inv.items) {
            if (!stack.isEmpty() && ingredient.test(stack)) {
                total += stack.getCount();
            }
        }
        for (ItemStack stack : inv.offhand) {
            if (!stack.isEmpty() && ingredient.test(stack)) {
                total += stack.getCount();
            }
        }
        return total;
    }

    private static int countMatchingItem(Inventory inv, ResourceLocation itemId) {
        int total = 0;
        for (ItemStack stack : inv.items) {
            if (!stack.isEmpty() && BuiltInRegistries.ITEM.getKey(stack.getItem()).equals(itemId)) {
                total += stack.getCount();
            }
        }
        return total;
    }

    private static void removeMatching(Inventory inv, Ingredient ingredient, int amount) {
        int remaining = amount;
        for (int i = 0; i < inv.items.size() && remaining > 0; i++) {
            ItemStack stack = inv.items.get(i);
            if (stack.isEmpty() || !ingredient.test(stack)) {
                continue;
            }
            int take = Math.min(remaining, stack.getCount());
            stack.shrink(take);
            remaining -= take;
        }
        for (int i = 0; i < inv.offhand.size() && remaining > 0; i++) {
            ItemStack stack = inv.offhand.get(i);
            if (stack.isEmpty() || !ingredient.test(stack)) {
                continue;
            }
            int take = Math.min(remaining, stack.getCount());
            stack.shrink(take);
            remaining -= take;
        }
    }

    /** RecipeIndex maps by output item only; the exact-id lookup goes straight to RecipeManager. */
    private static RecipeHolder<CraftingRecipe> findHolder(ToolContext ctx, ResourceLocation recipeId) {
        for (RecipeHolder<CraftingRecipe> holder :
                ctx.level().getRecipeManager().getAllRecipesFor(net.minecraft.world.item.crafting.RecipeType.CRAFTING)) {
            if (holder.id().equals(recipeId)) {
                return holder;
            }
        }
        return null;
    }

    private static Optional<BlockPos> findNearbyCraftingTable(Player player, Level level) {
        BlockPos center = player.blockPosition();
        for (BlockPos pos : BlockPos.betweenClosed(
                center.offset(-STATION_SEARCH_RADIUS, -2, -STATION_SEARCH_RADIUS),
                center.offset(STATION_SEARCH_RADIUS, 2, STATION_SEARCH_RADIUS))) {
            BlockState state = level.getBlockState(pos);
            if (state.is(Blocks.CRAFTING_TABLE)) {
                return Optional.of(pos.immutable());
            }
        }
        return Optional.empty();
    }

    private static String describeMissing(String itemName, CraftPlan plan) {
        StringBuilder sb = new StringBuilder("Can't craft " + itemName + " - you need: ");
        List<com.minebro.recipe.ItemAmount> missing = plan.missing();
        for (int i = 0; i < missing.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append(ItemDisplay.describe(missing.get(i)));
        }
        return sb.toString();
    }

    private static JsonObject toDataJson(CraftPlan plan) {
        JsonObject data = new JsonObject();
        data.addProperty("craftable", plan.craftable());
        data.addProperty("fitsInventoryGrid", plan.fitsInventoryGrid());
        return data;
    }
}
