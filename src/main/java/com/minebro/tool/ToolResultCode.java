package com.minebro.tool;

/** Closed enum by design (architecture doc §7.2) - testable, unlike free-text reasons. */
public enum ToolResultCode {
    OK,
    UNKNOWN_ITEM,
    UNKNOWN_TOOL,
    NO_RECIPE,
    MISSING_INGREDIENTS,
    NO_STATION_IN_REACH,
    OUT_OF_RANGE,
    INVENTORY_FULL,
    PERMISSION_DENIED,
    USER_DENIED,
    NOT_AVAILABLE_CLIENT_SIDE,
    WORLD_NOT_LOADED,
    INVALID_ARGUMENTS,
    INTERNAL_ERROR
}
