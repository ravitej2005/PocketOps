package com.pocketops.backend.agent;

public record AgentRegistrationResponse(
        String agentId,
        String infrastructureId,
        String identityToken,
        String grpcHost,
        int grpcPort
) {
}
