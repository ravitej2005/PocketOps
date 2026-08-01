package com.pocketops.backend.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "pocketops.auth.github")
public record GitHubOAuthProperties(
        String clientId,
        String clientSecret
) {
    boolean isConfigured() {
        return clientId != null && !clientId.isBlank()
                && clientSecret != null && !clientSecret.isBlank();
    }
}
