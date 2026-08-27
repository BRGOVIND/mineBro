package com.minebro.client.hud;

/** Canonical seven-state enum (Phase 2 design doc §3.1). */
public enum AvatarState {
    OFFLINE,
    IDLE,
    THINKING,
    WORKING,
    RESPONDING,
    SUCCESS,
    ERROR
}
