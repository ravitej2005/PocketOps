package com.pocketops.backend.infrastructure;

public enum Capability {
    METRICS,
    LOGS,
    LIVE_LOGS,
    START,
    STOP,
    RESTART,
    NETWORK_STATS,
    CONTAINER_DISCOVERY;

    public static Capability fromAction(String action) {
        return switch (action) {
            case "START_CONTAINER" -> START;
            case "STOP_CONTAINER" -> STOP;
            case "RESTART_CONTAINER" -> RESTART;
            default -> throw new IllegalArgumentException("Unknown action: " + action);
        };
    }
}
