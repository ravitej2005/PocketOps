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

    public static Capability fromInfrastructureAction(String action) {
        return switch (action) {
            case "START_ALL" -> START;
            case "STOP_ALL" -> STOP;
            case "RESTART_ALL" -> RESTART;
            default -> throw new IllegalArgumentException("Unknown infrastructure action: " + action);
        };
    }

    public static String resourceCommand(String action) {
        return switch (action) {
            case "START_ALL" -> "START_CONTAINER";
            case "STOP_ALL" -> "STOP_CONTAINER";
            case "RESTART_ALL" -> "RESTART_CONTAINER";
            default -> throw new IllegalArgumentException("Unknown infrastructure action: " + action);
        };
    }
}
