package com.pocketops.backend.common.health;

public record HealthResponse(
        String status,
        String database
) {
}
