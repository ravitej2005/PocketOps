package com.pocketops.backend.infrastructure;

import java.time.Instant;

public record InfrastructureResourceResponse(
        String id,
        String externalResourceId,
        String displayName,
        String resourceType,
        String status,
        String criticality,
        Instant lastSeenAt
) {
    static InfrastructureResourceResponse from(InfrastructureResourceEntity entity) {
        return new InfrastructureResourceResponse(
                entity.getId(),
                entity.getExternalResourceId(),
                entity.getDisplayName(),
                entity.getResourceType(),
                entity.getStatus(),
                entity.getCriticality(),
                entity.getLastSeenAt()
        );
    }
}
