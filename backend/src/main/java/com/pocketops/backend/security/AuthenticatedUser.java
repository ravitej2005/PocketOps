package com.pocketops.backend.security;

public record AuthenticatedUser(
        String userId,
        String sessionId,
        String email
) {
}
