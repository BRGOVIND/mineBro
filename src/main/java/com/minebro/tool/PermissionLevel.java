package com.minebro.tool;

/** Ordered low-to-high. A configured level grants every tool at or below it. */
public enum PermissionLevel {
    READ_ONLY,
    SAFE_ACTIONS,
    GAMEPLAY_ACTIONS,
    DESTRUCTIVE_ACTIONS;

    public boolean allows(PermissionLevel required) {
        return this.ordinal() >= required.ordinal();
    }
}
