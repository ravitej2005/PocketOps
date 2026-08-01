package com.pocketops.backend.agent;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AgentRegistrationRequest(
        @NotBlank String registrationToken,
        @NotBlank @Size(max = 64) String agentVersion
) {
}
