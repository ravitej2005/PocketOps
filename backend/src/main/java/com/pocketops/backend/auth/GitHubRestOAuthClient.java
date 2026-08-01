package com.pocketops.backend.auth;

import com.pocketops.backend.common.error.ApiException;
import com.pocketops.backend.common.error.ErrorCode;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;

@Component
public class GitHubRestOAuthClient implements GitHubOAuthClient {
    private final GitHubOAuthProperties properties;
    private final RestClient restClient;

    public GitHubRestOAuthClient(GitHubOAuthProperties properties, RestClient.Builder restClientBuilder) {
        this.properties = properties;
        this.restClient = restClientBuilder.build();
    }

    @Override
    public GitHubUserProfile exchangeAuthorizationCode(String code, String redirectUri) {
        if (!properties.isConfigured()) {
            throw new ApiException(
                    ErrorCode.AUTHENTICATION_REQUIRED,
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "GitHub OAuth is not configured."
            );
        }

        Map<?, ?> tokenResponse = restClient.post()
                .uri("https://github.com/login/oauth/access_token")
                .header("Accept", "application/json")
                .body(Map.of(
                        "client_id", properties.clientId(),
                        "client_secret", properties.clientSecret(),
                        "code", code,
                        "redirect_uri", redirectUri == null ? "" : redirectUri
                ))
                .retrieve()
                .body(Map.class);

        String accessToken = tokenResponse == null ? null : (String) tokenResponse.get("access_token");
        if (accessToken == null || accessToken.isBlank()) {
            throw new ApiException(ErrorCode.AUTHENTICATION_REQUIRED, HttpStatus.UNAUTHORIZED, "GitHub OAuth failed.");
        }

        Map<?, ?> user = restClient.get()
                .uri("https://api.github.com/user")
                .header("Authorization", "Bearer " + accessToken)
                .header("Accept", "application/vnd.github+json")
                .retrieve()
                .body(Map.class);

        if (user == null || user.get("id") == null) {
            throw new ApiException(ErrorCode.AUTHENTICATION_REQUIRED, HttpStatus.UNAUTHORIZED, "GitHub OAuth failed.");
        }

        String email = (String) user.get("email");
        if (email == null || email.isBlank()) {
            Object emails = restClient.get()
                    .uri("https://api.github.com/user/emails")
                    .header("Authorization", "Bearer " + accessToken)
                    .header("Accept", "application/vnd.github+json")
                    .retrieve()
                    .body(Object.class);
            email = extractPrimaryEmail(emails);
        }

        if (email == null || email.isBlank()) {
            throw new ApiException(ErrorCode.AUTHENTICATION_REQUIRED, HttpStatus.UNAUTHORIZED, "GitHub email is unavailable.");
        }

        return new GitHubUserProfile(String.valueOf(user.get("id")), email);
    }

    @SuppressWarnings("unchecked")
    private String extractPrimaryEmail(Object emails) {
        if (!(emails instanceof Iterable<?> iterable)) {
            return null;
        }
        for (Object item : iterable) {
            if (item instanceof Map<?, ?> map
                    && Boolean.TRUE.equals(map.get("primary"))
                    && Boolean.TRUE.equals(map.get("verified"))
                    && map.get("email") instanceof String email) {
                return email;
            }
        }
        return null;
    }
}
