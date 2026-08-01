package com.pocketops.backend.agent;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "pocketops.agent")
public record AgentProperties(
        Grpc grpc,
        String publicBaseUrl,
        long registrationTokenMinutes,
        long heartbeatTimeoutSeconds
) {
    public record Grpc(String host, int port, Tls tls) {
    }

    public record Tls(boolean enabled, String certChainPath, String privateKeyPath) {
    }
}
