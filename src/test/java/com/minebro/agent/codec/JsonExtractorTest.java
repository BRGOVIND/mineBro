package com.minebro.agent.codec;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The "crown jewel" corpus per architecture doc §22: malformed local-model output, never a clean API response. */
class JsonExtractorTest {

    @Test
    void parsesCleanToolCall() {
        String raw = "{\"tool\": \"get_inventory\", \"arguments\": {}}";
        Optional<JsonObject> result = JsonExtractor.extractToolCall(raw);
        assertTrue(result.isPresent());
        assertEquals("get_inventory", result.get().get("tool").getAsString());
    }

    @Test
    void stripsMarkdownFences() {
        String raw = "```json\n{\"tool\": \"get_recipe\", \"arguments\": {\"item\": \"minecraft:stick\"}}\n```";
        Optional<JsonObject> result = JsonExtractor.extractToolCall(raw);
        assertTrue(result.isPresent());
        assertEquals("get_recipe", result.get().get("tool").getAsString());
    }

    @Test
    void toleratesLeadingProse() {
        String raw = "Sure, let me check that for you.\n{\"tool\": \"check_can_craft\", \"arguments\": {\"item\": \"minecraft:iron_pickaxe\", \"quantity\": 1}}";
        Optional<JsonObject> result = JsonExtractor.extractToolCall(raw);
        assertTrue(result.isPresent());
        assertEquals("check_can_craft", result.get().get("tool").getAsString());
    }

    @Test
    void toleratesTrailingCommentary() {
        String raw = "{\"tool\": \"get_position\", \"arguments\": {}}\nLet me know what you find!";
        Optional<JsonObject> result = JsonExtractor.extractToolCall(raw);
        assertTrue(result.isPresent());
        assertEquals("get_position", result.get().get("tool").getAsString());
    }

    @Test
    void plainTextAnswerYieldsNoToolCall() {
        String raw = "A creeper is a hostile mob that explodes when close to the player.";
        assertTrue(JsonExtractor.extractToolCall(raw).isEmpty());
    }

    @Test
    void jsonWithoutArgumentsIsNotAToolCall() {
        String raw = "{\"tool\": \"get_inventory\"}";
        assertTrue(JsonExtractor.extractToolCall(raw).isEmpty());
    }

    @Test
    void jsonMissingToolFieldIsNotAToolCall() {
        String raw = "{\"item\": \"minecraft:iron_pickaxe\"}";
        assertTrue(JsonExtractor.extractToolCall(raw).isEmpty());
    }

    @Test
    void unbalancedBracesDoNotCrashTheParser() {
        String raw = "{\"tool\": \"craft_item\", \"arguments\": {\"item\": \"minecraft:stick\"";
        assertTrue(JsonExtractor.extractToolCall(raw).isEmpty());
    }

    @Test
    void nullOrBlankInputIsHandled() {
        assertTrue(JsonExtractor.extractToolCall(null).isEmpty());
        assertTrue(JsonExtractor.extractToolCall("   ").isEmpty());
    }

    @Test
    void findsToolCallAmongMultipleBraceGroups() {
        String raw = "Here's the game state I see: {\"hp\": 20}. Now calling a tool:\n{\"tool\": \"get_player_status\", \"arguments\": {}}";
        Optional<JsonObject> result = JsonExtractor.extractToolCall(raw);
        assertTrue(result.isPresent());
        assertEquals("get_player_status", result.get().get("tool").getAsString());
    }

    @Test
    void bracesInsideStringValuesDoNotConfuseBalancing() {
        String raw = "{\"tool\": \"get_recipe\", \"arguments\": {\"item\": \"minecraft:{weird}\"}}";
        Optional<JsonObject> result = JsonExtractor.extractToolCall(raw);
        assertTrue(result.isPresent());
        assertEquals("minecraft:{weird}", result.get().getAsJsonObject("arguments").get("item").getAsString());
    }

    // --- QA issue 5: unknown tool names, missing/wrong-type arguments, impossible quantities.
    // JsonExtractor's job is purely syntactic extraction - semantic rejection (is this tool
    // registered? are the argument types right?) happens downstream in ToolRegistry/ToolArgs.
    // These tests pin down that split: extraction must still succeed for a syntactically valid
    // call even when the tool name is bogus, and must safely decline (never throw) when the
    // shape is wrong.

    @Test
    void extractsAnUnknownToolNameSyntactically_rejectionHappensDownstreamInToolRegistry() {
        String raw = "{\"tool\": \"fly_to_the_moon\", \"arguments\": {}}";
        Optional<JsonObject> result = JsonExtractor.extractToolCall(raw);
        assertTrue(result.isPresent(), "extraction is purely syntactic; ToolRegistry/ToolExecutor rejects the unknown name, not this parser");
        assertEquals("fly_to_the_moon", result.get().get("tool").getAsString());
    }

    @Test
    void argumentsAsAStringInsteadOfAnObjectIsNotATreatedAsAToolCall() {
        String raw = "{\"tool\": \"get_recipe\", \"arguments\": \"minecraft:stick\"}";
        assertTrue(JsonExtractor.extractToolCall(raw).isEmpty(), "wrong-shaped arguments must be safely declined, never crash the parser");
    }

    @Test
    void argumentsAsANumberInsteadOfAnObjectIsSafelyDeclined() {
        String raw = "{\"tool\": \"craft_item\", \"arguments\": 42}";
        assertTrue(JsonExtractor.extractToolCall(raw).isEmpty());
    }

    @Test
    void toolNameAsANonStringValueIsSafelyDeclined() {
        String raw = "{\"tool\": 123, \"arguments\": {}}";
        assertTrue(JsonExtractor.extractToolCall(raw).isEmpty());
    }

    @Test
    void impossibleQuantityValueStillExtractsSyntactically_rejectionHappensInToolArgs() {
        // "quantity": 999999 is syntactically fine JSON; ToolArgs/each tool's own range check
        // (1-64) is what turns this into INVALID_ARGUMENTS, not JsonExtractor.
        String raw = "{\"tool\": \"craft_item\", \"arguments\": {\"item\": \"minecraft:stick\", \"quantity\": 999999}}";
        Optional<JsonObject> result = JsonExtractor.extractToolCall(raw);
        assertTrue(result.isPresent());
        assertEquals(999999, result.get().getAsJsonObject("arguments").get("quantity").getAsInt());
    }

    @Test
    void deeplyNestedJunkBeforeAndAfterTheRealCallIsIgnored() {
        String raw = "{}{{}malformed{\"tool\": \"get_inventory\", \"arguments\": {}}garbage}}}";
        Optional<JsonObject> result = JsonExtractor.extractToolCall(raw);
        assertTrue(result.isPresent());
        assertEquals("get_inventory", result.get().get("tool").getAsString());
    }
}
