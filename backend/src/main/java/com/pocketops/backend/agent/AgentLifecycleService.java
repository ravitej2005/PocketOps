package com.pocketops.backend.agent;

import com.pocketops.backend.common.error.ApiException;
import com.pocketops.backend.common.error.ErrorCode;
import com.pocketops.backend.infrastructure.HealthStatus;
import com.pocketops.backend.infrastructure.InfrastructureService;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class AgentLifecycleService {
    private final AgentRepository agentRepository;
    private final InfrastructureService infrastructureService;
    private final AgentProperties agentProperties;

    public AgentLifecycleService(
            AgentRepository agentRepository,
            InfrastructureService infrastructureService,
            AgentProperties agentProperties
    ) {
        this.agentRepository = agentRepository;
        this.infrastructureService = infrastructureService;
        this.agentProperties = agentProperties;
    }

    @Transactional
    public void recordHeartbeat(AgentIdentity identity, String agentVersion) {
        AgentEntity agent = agentRepository.findById(identity.agent().getId())
                .orElseThrow(() -> new ApiException(ErrorCode.AGENT_NOT_FOUND, HttpStatus.NOT_FOUND, "Agent not found."));
        if (agent.getRevokedAt() != null || agent.getStatus() == AgentStatus.REVOKED) {
            throw new ApiException(ErrorCode.AUTHENTICATION_REQUIRED, HttpStatus.UNAUTHORIZED, "Agent is revoked.");
        }
        agent.setVersion(agentVersion);
        agent.setLastSeenAt(Instant.now());
        agent.setStatus(AgentStatus.ONLINE);
        agent.getInfrastructure().setHealthStatus(HealthStatus.HEALTHY);
    }

    @Transactional
    public void revokeOwnedAgent(String userId, String infrastructureId) {
        var infrastructure = infrastructureService.resolveOwned(userId, infrastructureId);
        AgentEntity agent = agentRepository.findByInfrastructure_Id(infrastructure.getId())
                .orElseThrow(() -> new ApiException(ErrorCode.AGENT_NOT_FOUND, HttpStatus.NOT_FOUND, "Agent not found."));
        agent.setStatus(AgentStatus.REVOKED);
        agent.setRevokedAt(Instant.now());
        infrastructure.setHealthStatus(HealthStatus.UNKNOWN);
    }

    @Scheduled(fixedDelayString = "${pocketops.agent.heartbeat-timeout-seconds:45}000")
    @Transactional
    public void markTimedOutAgentsOffline() {
        Instant cutoff = Instant.now().minusSeconds(agentProperties.heartbeatTimeoutSeconds());
        for (AgentEntity agent : agentRepository.findByStatusAndLastSeenAtBefore(AgentStatus.ONLINE, cutoff)) {
            agent.setStatus(AgentStatus.OFFLINE);
            agent.getInfrastructure().setHealthStatus(HealthStatus.UNKNOWN);
        }
    }
}
