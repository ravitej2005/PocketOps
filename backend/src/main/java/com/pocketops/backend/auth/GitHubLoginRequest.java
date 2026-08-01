package com.pocketops.backend.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record GitHubLoginRequest(
        @NotBlank @Size(max = 4096) String code,
        @Size(max = 512) String redirectUri,
        @Size(max = 255) String deviceName,
        @Size(max = 64) String platform
) {
}
