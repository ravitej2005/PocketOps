package com.pocketops.backend.monitoring;

import com.pocketops.backend.infrastructure.HealthStatus;

public record InfrastructureStateUpdate(
        String type,
        String infrastructureId,
        HealthStatus healthStatus,
        long timestampUnixMs
) {
}
