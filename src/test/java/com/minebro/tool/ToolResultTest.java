package com.minebro.tool;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ToolResultTest {

    @Test
    void successResultAlwaysHasSuccessTrue() {
        ToolResult result = ToolResult.ok("tc_1", "get_inventory", "Inventory retrieved.", new JsonObject());
        JsonObject json = result.toJson();
        assertTrue(json.get("success").getAsBoolean());
        assertEquals("OK", json.get("code").getAsString());
    }

    @Test
    void failureResultUsesClosedEnumCode() {
        ToolResult result = ToolResult.fail("tc_2", "craft_item", ToolResultCode.MISSING_INGREDIENTS, "You need 2 more iron ingots.");
        JsonObject json = result.toJson();
        assertEquals(false, json.get("success").getAsBoolean());
        assertEquals("MISSING_INGREDIENTS", json.get("code").getAsString());
        assertEquals("You need 2 more iron ingots.", json.get("reason").getAsString());
    }

    @Test
    void everyResultCodeRoundTripsThroughTheEnum() {
        for (ToolResultCode code : ToolResultCode.values()) {
            ToolResult result = ToolResult.fail("tc", "some_tool", code, "reason");
            assertEquals(code, ToolResultCode.valueOf(result.toJson().get("code").getAsString()));
        }
    }

    @Test
    void dataFieldDefaultsToEmptyObjectNeverNull() {
        ToolResult result = ToolResult.fail("tc", "tool", ToolResultCode.INTERNAL_ERROR, "oops");
        assertTrue(result.toJson().get("data").isJsonObject());
    }
}
