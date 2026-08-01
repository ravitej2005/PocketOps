package com.pocketops.backend.auth;

public record AuthResponse(
        String accessToken,
        String refreshToken,
        UserResponse user
) {
}
