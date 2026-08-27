package com.minebro.tool.impl;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.minebro.provider.model.ToolSchema;

/** Flat JSON-Schema builders (object/string/integer, no nesting) - small models handle these far better than $ref-heavy schemas. */
final class ToolSchemas {

    static ToolSchema noArgs(String name, String description) {
        JsonObject params = new JsonObject();
        params.addProperty("type", "object");
        params.add("properties", new JsonObject());
        params.add("required", new JsonArray());
        return new ToolSchema(name, description, params);
    }

    static ToolSchema oneStringArg(String name, String description, String argName, String argDescription) {
        JsonObject prop = new JsonObject();
        prop.addProperty("type", "string");
        prop.addProperty("description", argDescription);

        JsonObject properties = new JsonObject();
        properties.add(argName, prop);

        JsonArray required = new JsonArray();
        required.add(argName);

        JsonObject params = new JsonObject();
        params.addProperty("type", "object");
        params.add("properties", properties);
        params.add("required", required);
        return new ToolSchema(name, description, params);
    }

    static ToolSchema itemAndQuantity(String name, String description) {
        JsonObject item = new JsonObject();
        item.addProperty("type", "string");
        item.addProperty("description", "Namespaced item id, e.g. minecraft:iron_pickaxe");

        JsonObject quantity = new JsonObject();
        quantity.addProperty("type", "integer");
        quantity.addProperty("description", "How many to craft, default 1");

        JsonObject properties = new JsonObject();
        properties.add("item", item);
        properties.add("quantity", quantity);

        JsonArray required = new JsonArray();
        required.add("item");

        JsonObject params = new JsonObject();
        params.addProperty("type", "object");
        params.add("properties", properties);
        params.add("required", required);
        return new ToolSchema(name, description, params);
    }

    private ToolSchemas() {}
}
