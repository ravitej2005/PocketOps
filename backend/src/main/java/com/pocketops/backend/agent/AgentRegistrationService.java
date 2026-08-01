package com.pocketops.backend.agent;

import com.pocketops.backend.common.error.ApiException;
import com.pocketops.backend.common.error.ErrorCode;
import com.pocketops.backend.infrastructure.HealthStatus;
import com.pocketops.backend.infrastructure.InfrastructureEntity;
import com.pocketops.backend.infrastructure.InfrastructureService;
import com.pocketops.backend.infrastructure.InfrastructureType;
import com.pocketops.backend.security.JwtService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;

@Service
public class AgentRegistrationService {
    private final AgentRegistrationTokenRepository tokenRepository;
    private final AgentRepository agentRepository;
    private final InfrastructureService infrastructureService;
    private final JwtService jwtService;
    private final AgentProperties agentProperties;
    private final SecureRandom secureRandom = new SecureRandom();

    public AgentRegistrationService(
            AgentRegistrationTokenRepository tokenRepository,
            AgentRepository agentRepository,
            InfrastructureService infrastructureService,
            JwtService jwtService,
            AgentProperties agentProperties
    ) {
        this.tokenRepository = tokenRepository;
        this.agentRepository = agentRepository;
        this.infrastructureService = infrastructureService;
        this.jwtService = jwtService;
        this.agentProperties = agentProperties;
    }

    @Transactional
    public RegistrationCredentialResponse createRegistrationCredential(String userId, String infrastructureId) {
        InfrastructureEntity infrastructure = infrastructureService.resolveOwned(userId, infrastructureId);
        if (infrastructure.getType() != InfrastructureType.SELF_HOSTED) {
            throw new ApiException(ErrorCode.OPERATION_REJECTED, HttpStatus.BAD_REQUEST, "Only self-hosted infrastructure uses an agent.");
        }

        agentRepository.findByInfrastructure_Id(infrastructure.getId()).orElseGet(() -> {
            AgentEntity pending = new AgentEntity();
            pending.setInfrastructure(infrastructure);
            pending.setIdentityTokenHash("pending-" + infrastructure.getId());
            pending.setStatus(AgentStatus.PENDING);
            return agentRepository.save(pending);
        });

        String token = randomToken();
        Instant expiresAt = Instant.now().plusSeconds(agentProperties.registrationTokenMinutes() * 60);

        AgentRegistrationTokenEntity entity = new AgentRegistrationTokenEntity();
        entity.setInfrastructure(infrastructure);
        entity.setTokenHash(jwtService.hashToken(token));
        entity.setExpiresAt(expiresAt);
        tokenRepository.save(entity);

        String installCommand = "pocketops-agent --backend "
                + agentProperties.publicBaseUrl()
                + " --grpc "
                + agentProperties.grpc().host()
                + ":"
                + agentProperties.grpc().port()
                + insecureDevFlag()
                + " --token "
                + token;
        return new RegistrationCredentialResponse(token, expiresAt, installCommand);
    }

    @Transactional
    public AgentRegistrationResponse register(AgentRegistrationRequest request) {
        AgentRegistrationTokenEntity token = tokenRepository.findByTokenHash(jwtService.hashToken(request.registrationToken()))
                .orElseThrow(() -> invalidRegistrationToken());
        if (token.getUsedAt() != null || token.getExpiresAt().isBefore(Instant.now())) {
            throw invalidRegistrationToken();
        }

        InfrastructureEntity infrastructure = token.getInfrastructure();
        AgentEntity agent = agentRepository.findByInfrastructure_Id(infrastructure.getId()).orElseGet(() -> {
            AgentEntity created = new AgentEntity();
            created.setInfrastructure(infrastructure);
            return created;
        });

        String identityToken = randomToken();
        Instant now = Instant.now();
        agent.setIdentityTokenHash(jwtService.hashToken(identityToken));
        agent.setVersion(request.agentVersion());
        agent.setStatus(AgentStatus.ONLINE);
        agent.setRegisteredAt(now);
        agent.setLastSeenAt(now);
        agent.setRevokedAt(null);
        agent = agentRepository.save(agent);

        token.setUsedAt(now);
        infrastructure.setHealthStatus(HealthStatus.HEALTHY);

        return new AgentRegistrationResponse(
                agent.getId(),
                infrastructure.getId(),
                identityToken,
                agentProperties.grpc().host(),
                agentProperties.grpc().port()
        );
    }

    @Transactional(readOnly = true)
    public AgentIdentity authenticate(String agentId, String infrastructureId, String identityToken) {
        AgentEntity agent = agentRepository.findByIdAndInfrastructure_Id(agentId, infrastructureId)
                .orElseThrow(() -> new ApiException(ErrorCode.AUTHENTICATION_REQUIRED, HttpStatus.UNAUTHORIZED, "Agent authentication failed."));
        if (agent.getStatus() == AgentStatus.REVOKED || agent.getRevokedAt() != null) {
            throw new ApiException(ErrorCode.AUTHENTICATION_REQUIRED, HttpStatus.UNAUTHORIZED, "Agent is revoked.");
        }
        if (!agent.getIdentityTokenHash().equals(jwtService.hashToken(identityToken))) {
            throw new ApiException(ErrorCode.AUTHENTICATION_REQUIRED, HttpStatus.UNAUTHORIZED, "Agent authentication failed.");
        }
        return new AgentIdentity(agent, infrastructureId);
    }

    private ApiException invalidRegistrationToken() {
        return new ApiException(
                ErrorCode.REGISTRATION_TOKEN_INVALID,
                HttpStatus.UNAUTHORIZED,
                "Registration token is invalid."
        );
    }

    private String randomToken() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String insecureDevFlag() {
        AgentProperties.Tls tls = agentProperties.grpc().tls();
        if (tls == null || !tls.enabled()) {
            return " --insecure-dev";
        }
        return "";
    }
}
