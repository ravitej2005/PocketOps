package com.pocketops.backend.auth;

public record GitHubUserProfile(
        String githubId,
        String email
) {
}
