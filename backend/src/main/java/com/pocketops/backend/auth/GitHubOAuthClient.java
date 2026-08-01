package com.pocketops.backend.auth;

public interface GitHubOAuthClient {
    GitHubUserProfile exchangeAuthorizationCode(String code, String redirectUri);
}
