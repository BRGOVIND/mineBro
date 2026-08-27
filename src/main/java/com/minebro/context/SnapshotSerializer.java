package com.minebro.context;

import com.google.gson.JsonObject;

import java.util.Map;

/** Hand-rolled rather than a generic Gson dump: field names are shortened to save tokens (§10.2). */
public final class SnapshotSerializer {

    public static String toCompactJson(GameSnapshot snapshot) {
        JsonObject root = new JsonObject();

        JsonObject world = new JsonObject();
        world.addProperty("dim", snapshot.world().dimension());
        world.addProperty("time", snapshot.world().timeOfDay());
        world.addProperty("weather", snapshot.world().raining() ? "rain" : "clear");
        world.addProperty("sp", snapshot.world().singleplayer());
        root.add("world", world);

        JsonObject player = new JsonObject();
        JsonObject pos = new JsonObject();
        pos.addProperty("x", snapshot.player().x());
        pos.addProperty("y", snapshot.player().y());
        pos.addProperty("z", snapshot.player().z());
        player.add("pos", pos);
        player.addProperty("facing", snapshot.player().facing());
        player.addProperty("biome", snapshot.player().biome());
        player.addProperty("hp", (int) snapshot.player().health() + "/" + (int) snapshot.player().maxHealth());
        player.addProperty("food", snapshot.player().food());
        player.addProperty("xp", snapshot.player().xpLevel());
        root.add("player", player);

        JsonObject inv = new JsonObject();
        inv.addProperty("free", snapshot.inventory().freeSlots());
        JsonObject items = new JsonObject();
        for (Map.Entry<String, Integer> e : snapshot.inventory().items().entrySet()) {
            items.addProperty(e.getKey(), e.getValue());
        }
        inv.add("items", items);
        inv.addProperty("mainHand", snapshot.inventory().mainHand());
        root.add("inv", inv);

        com.google.gson.JsonArray stations = new com.google.gson.JsonArray();
        snapshot.stationsInReach().forEach(stations::add);
        JsonObject near = new JsonObject();
        near.add("stations", stations);
        root.add("near", near);

        return root.toString();
    }

    private SnapshotSerializer() {}
}
