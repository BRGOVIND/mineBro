package com.minebro.provider.model;

import com.google.gson.JsonObject;

/** A model-requested tool invocation, already decoded into MineBro's normalized shape. */
public record ToolCall(String id, String tool, JsonObject arguments) {}
