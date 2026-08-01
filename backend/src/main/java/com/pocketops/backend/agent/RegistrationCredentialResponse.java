package com.pocketops.backend.agent;

import java.time.Instant;

public record RegistrationCredentialResponse(
        String registrationToken,
        Instant expiresAt,
        String installCommand
) {
}
