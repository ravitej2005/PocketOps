package com.pocketops.backend.session;

import java.time.Instant;

public record SessionResponse(
        String id,
        String deviceName,
        Instant createdAt,
        Instant lastUsedAt,
        Instant expiresAt,
        boolean current
) {
    static SessionResponse from(UserSessionEntity session, String currentSessionId) {
        return new SessionResponse(
                session.getId(),
                session.getDeviceName(),
                session.getCreatedAt(),
                session.getLastUsedAt(),
                session.getExpiresAt(),
                session.getId().equals(currentSessionId)
        );
    }
}
