package com.pocketops.backend.infrastructure;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateInfrastructureRequest(
        @NotBlank @Size(max = 255) String name,
        @NotNull InfrastructureType type,
        @Size(max = 64) String providerType
) {
}
