package com.minebro.context;

import com.minebro.tool.InventoryCounter;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Client-thread only. Target build cost < 1ms - this is injected into every turn's prompt. */
public final class ContextBuilder {

    private static final int STATION_RADIUS = 5;

    public static GameSnapshot build(Player player, Level level, boolean singleplayer) {
        BlockPos pos = player.blockPosition();

        long dayTime = level.getDayTime() % 24000;
        String timeOfDay = dayTime < 12000 ? "day" : dayTime < 13800 ? "dusk" : dayTime < 22200 ? "night" : "dawn";

        Holder<Biome> biome = level.getBiomeManager().getBiome(pos);
        String biomeId = biome.unwrapKey().map(k -> k.location().toString()).orElse("unknown");

        GameSnapshot.WorldInfo world = new GameSnapshot.WorldInfo(
                level.dimension().location().toString(), timeOfDay, level.isRaining(), singleplayer);

        GameSnapshot.PlayerInfo playerInfo = new GameSnapshot.PlayerInfo(
                pos.getX(), pos.getY(), pos.getZ(), player.getDirection().getName(), biomeId,
                player.getHealth(), player.getMaxHealth(), player.getFoodData().getFoodLevel(), player.experienceLevel);

        Map<ResourceLocation, Integer> counts = InventoryCounter.aggregate(player.getInventory());
        Map<String, Integer> items = new LinkedHashMap<>();
        counts.entrySet().stream()
                .sorted((a, b) -> b.getValue() - a.getValue())
                .limit(24)
                .forEach(e -> items.put(e.getKey().toString(), e.getValue()));

        ItemStack mainHand = player.getMainHandItem();
        ItemStack offHand = player.getOffhandItem();
        long freeSlots = player.getInventory().items.stream().filter(ItemStack::isEmpty).count();

        GameSnapshot.InventoryInfo inventoryInfo = new GameSnapshot.InventoryInfo(
                items,
                mainHand.isEmpty() ? "empty" : BuiltInRegistries.ITEM.getKey(mainHand.getItem()).toString(),
                offHand.isEmpty() ? "empty" : BuiltInRegistries.ITEM.getKey(offHand.getItem()).toString(),
                (int) freeSlots);

        List<String> stations = findStationsInReach(player, level);

        return new GameSnapshot(world, playerInfo, inventoryInfo, stations);
    }

    private static List<String> findStationsInReach(Player player, Level level) {
        List<String> found = new ArrayList<>();
        BlockPos center = player.blockPosition();
        for (BlockPos p : BlockPos.betweenClosed(
                center.offset(-STATION_RADIUS, -2, -STATION_RADIUS),
                center.offset(STATION_RADIUS, 2, STATION_RADIUS))) {
            BlockState state = level.getBlockState(p);
            if (state.is(Blocks.CRAFTING_TABLE) && !found.contains("minecraft:crafting_table")) {
                found.add("minecraft:crafting_table");
            } else if (state.is(Blocks.FURNACE) && !found.contains("minecraft:furnace")) {
                found.add("minecraft:furnace");
            } else if (state.is(Blocks.SMITHING_TABLE) && !found.contains("minecraft:smithing_table")) {
                found.add("minecraft:smithing_table");
            }
            if (found.size() >= 3) {
                break;
            }
        }
        return found;
    }

    private ContextBuilder() {}
}
