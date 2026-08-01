package com.pocketops.backend.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @Email @NotBlank @Size(max = 320) String email,
        @NotBlank @Size(min = 8, max = 128) String password,
        @Size(max = 255) String deviceName,
        @Size(max = 64) String platform
) {
}
