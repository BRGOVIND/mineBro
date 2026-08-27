package com.minebro.provider.model;

import com.google.gson.JsonObject;

/**
 * A tool advertised to the model. {@code parametersSchema} is a flat JSON-Schema object
 * (type/properties/required only - no $ref, no nesting) so small local models can follow it.
 */
public record ToolSchema(String name, String description, JsonObject parametersSchema) {}
