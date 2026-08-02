package com.pocketops.backend.monitoring;

public record ResourceStateUpdate(
        String type,
        String infrastructureId,
        String resourceId,
        String displayName,
        String resourceType,
        String status,
        String criticality,
        long lastSeenAtUnixMs,
        long startedAtUnixMs
) {
}
