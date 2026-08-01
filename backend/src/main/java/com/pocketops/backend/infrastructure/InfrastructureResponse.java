package com.pocketops.backend.infrastructure;

import java.time.Instant;
import java.util.Set;

public record InfrastructureResponse(
        String id,
        String name,
        InfrastructureType type,
        String providerType,
        HealthStatus healthStatus,
        Set<Capability> capabilities,
        Instant lastUpdatedAt,
        Instant createdAt
) {
    static InfrastructureResponse from(InfrastructureEntity entity) {
        return new InfrastructureResponse(
                entity.getId(),
                entity.getName(),
                entity.getType(),
                entity.getProviderType(),
                entity.getHealthStatus(),
                entity.getCapabilities(),
                entity.getLastUpdatedAt(),
                entity.getCreatedAt()
        );
    }
}
