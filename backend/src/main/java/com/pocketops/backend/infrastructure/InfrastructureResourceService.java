package com.pocketops.backend.infrastructure;

import com.pocketops.backend.agent.AgentEntity;
import com.pocketops.backend.agent.AgentGrpcCommandDispatcher;
import com.pocketops.backend.agent.AgentRepository;
import com.pocketops.backend.agent.AgentStatus;
import com.pocketops.backend.common.error.ApiException;
import com.pocketops.backend.common.error.ErrorCode;
import com.pocketops.backend.monitoring.InfrastructureStateUpdate;
import com.pocketops.backend.monitoring.ResourceStateUpdate;
import com.pocketops.backend.proto.ResourceSnapshot;
import com.pocketops.backend.websocket.InfrastructureUpdatesWebSocketHandler;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class InfrastructureResourceService {
    private final AgentRepository agentRepository;
    private final InfrastructureResourceRepository resourceRepository;
    private final InfrastructureService infrastructureService;
    private final InfrastructureUpdatesWebSocketHandler webSocketHandler;
    private final AgentGrpcCommandDispatcher commandDispatcher;

    public InfrastructureResourceService(
            AgentRepository agentRepository,
            InfrastructureResourceRepository resourceRepository,
            InfrastructureService infrastructureService,
            InfrastructureUpdatesWebSocketHandler webSocketHandler,
            AgentGrpcCommandDispatcher commandDispatcher
    ) {
        this.agentRepository = agentRepository;
        this.resourceRepository = resourceRepository;
        this.infrastructureService = infrastructureService;
        this.webSocketHandler = webSocketHandler;
        this.commandDispatcher = commandDispatcher;
    }

    @Transactional(readOnly = true)
    public List<InfrastructureResourceResponse> listOwned(String userId, String infrastructureId) {
        infrastructureService.resolveOwned(userId, infrastructureId);
        return resourceRepository.findByInfrastructure_IdOrderByDisplayNameAsc(infrastructureId)
                .stream()
                .map(InfrastructureResourceResponse::from)
                .toList();
    }

    @Transactional
    public void reconcile(String agentId, List<ResourceSnapshot> resources) {
        InfrastructureEntity infrastructure = agentRepository.findById(agentId)
                .orElseThrow()
                .getInfrastructure();
        Instant now = Instant.now();

        for (ResourceSnapshot snapshot : resources) {
            InfrastructureResourceEntity entity = resourceRepository
                    .findByInfrastructure_IdAndExternalResourceId(
                            infrastructure.getId(),
                            snapshot.getExternalResourceId()
                    )
                    .orElseGet(() -> {
                        InfrastructureResourceEntity created = new InfrastructureResourceEntity();
                        created.setInfrastructure(infrastructure);
                        created.setExternalResourceId(snapshot.getExternalResourceId());
                        return created;
                    });
            entity.setDisplayName(nonBlank(snapshot.getDisplayName(), snapshot.getExternalResourceId()));
            entity.setResourceType(nonBlank(snapshot.getResourceType(), "CONTAINER"));
            entity.setStatus(normalizeStatus(snapshot.getStatus()));
            entity.setCriticality(nonBlank(snapshot.getCriticality(), "NORMAL"));
            entity.setLastSeenAt(now);
            resourceRepository.save(entity);
            webSocketHandler.broadcast(infrastructure.getId(), new ResourceStateUpdate(
                    "ResourceStateChanged",
                    infrastructure.getId(),
                    entity.getExternalResourceId(),
                    entity.getDisplayName(),
                    entity.getResourceType(),
                    entity.getStatus(),
                    entity.getCriticality(),
                    now.toEpochMilli(),
                    snapshot.getStartedAtUnixMs()
            ));
        }
        HealthStatus healthStatus = evaluateHealth(infrastructure.getId());
        infrastructure.setHealthStatus(healthStatus);
        webSocketHandler.broadcast(infrastructure.getId(), new InfrastructureStateUpdate(
                "InfrastructureStateChanged",
                infrastructure.getId(),
                healthStatus,
                now.toEpochMilli()
        ));
    }

    // Dispatches only an in-memory gRPC command after validation; it does not mutate persistent state.
    @Transactional(readOnly = true)
    public InfrastructureController.ResourceActionResponse executeAction(
            String userId,
            String infrastructureId,
            String resourceId,
            InfrastructureController.ResourceActionRequest request
    ) {
        InfrastructureEntity infrastructure = infrastructureService.resolveOwned(userId, infrastructureId);
        // Ownership-scoped resource lookup
        resourceRepository
                .findByInfrastructure_IdAndExternalResourceId(infrastructureId, resourceId)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, HttpStatus.NOT_FOUND, "Resource not found."));

        // Validate action is on the allow-list and infrastructure supports it
        Capability required;
        try {
            required = Capability.fromAction(request.action());
        } catch (IllegalArgumentException e) {
            throw new ApiException(ErrorCode.CAPABILITY_UNSUPPORTED, HttpStatus.BAD_REQUEST, "Action not allowed.");
        }
        if (!infrastructure.getCapabilities().contains(required)) {
            throw new ApiException(ErrorCode.CAPABILITY_UNSUPPORTED, HttpStatus.BAD_REQUEST, "Action not supported by this infrastructure.");
        }

        // Validate agent availability (status check)
        AgentEntity agent = agentRepository.findByInfrastructure_Id(infrastructureId)
                .orElseThrow(() -> new ApiException(ErrorCode.AGENT_NOT_FOUND, HttpStatus.NOT_FOUND, "Agent not found."));
        if (agent.getStatus() != AgentStatus.ONLINE) {
            throw new ApiException(ErrorCode.AGENT_OFFLINE, HttpStatus.SERVICE_UNAVAILABLE, "The agent for this infrastructure is currently offline.");
        }

        // Dispatch command via gRPC — if no active stream (race with disconnect), fail immediately as AGENT_OFFLINE
        String correlationId = UUID.randomUUID().toString();
        try {
            commandDispatcher.dispatch(agent.getId(), infrastructureId, resourceId, request.action(), correlationId);
        } catch (IllegalStateException e) {
            throw new ApiException(ErrorCode.AGENT_OFFLINE, HttpStatus.SERVICE_UNAVAILABLE, "The agent stream is not active. Command was not queued.");
        }

        return new InfrastructureController.ResourceActionResponse(correlationId, "DISPATCHED");
    }

    private HealthStatus evaluateHealth(String infrastructureId) {
        List<InfrastructureResourceEntity> resources =
                resourceRepository.findByInfrastructure_IdOrderByDisplayNameAsc(infrastructureId);
        if (resources.isEmpty()) {
            return HealthStatus.UNKNOWN;
        }
        boolean degraded = false;
        for (InfrastructureResourceEntity resource : resources) {
            if ("FAILED".equals(resource.getStatus()) && "CRITICAL".equals(resource.getCriticality())) {
                return HealthStatus.CRITICAL;
            }
            if (!"RUNNING".equals(resource.getStatus())) {
                degraded = true;
            }
        }
        return degraded ? HealthStatus.DEGRADED : HealthStatus.HEALTHY;
    }

    private String normalizeStatus(String status) {
        String normalized = nonBlank(status, "UNKNOWN").toUpperCase();
        return switch (normalized) {
            case "RUNNING" -> "RUNNING";
            case "EXITED", "CREATED", "REMOVING", "STOPPED" -> "STOPPED";
            case "DEAD", "FAILED" -> "FAILED";
            default -> "UNKNOWN";
        };
    }

    private String nonBlank(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value.trim();
    }
}
