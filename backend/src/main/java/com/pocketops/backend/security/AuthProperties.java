package com.pocketops.backend.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "pocketops.auth.jwt")
public record AuthProperties(
        String issuer,
        String secret,
        long accessTokenMinutes,
        long refreshTokenDays
) {
}
