package com.minebro.provider.model;

public record HealthReport(boolean reachable, boolean modelAvailable, String detail) {

    public static HealthReport ok(String detail) {
        return new HealthReport(true, true, detail);
    }

    public static HealthReport unreachable(String detail) {
        return new HealthReport(false, false, detail);
    }

    public static HealthReport modelMissing(String detail) {
        return new HealthReport(true, false, detail);
    }
}
