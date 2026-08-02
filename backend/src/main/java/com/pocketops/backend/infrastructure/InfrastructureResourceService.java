package com.pocketops.backend.infrastructure;

import com.pocketops.backend.agent.AgentRepository;
import com.pocketops.backend.proto.ResourceSnapshot;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
public class InfrastructureResourceService {
    private final AgentRepository agentRepository;
    private final InfrastructureResourceRepository resourceRepository;
    private final InfrastructureService infrastructureService;

    public InfrastructureResourceService(
            AgentRepository agentRepository,
            InfrastructureResourceRepository resourceRepository,
            InfrastructureService infrastructureService
    ) {
        this.agentRepository = agentRepository;
        this.resourceRepository = resourceRepository;
        this.infrastructureService = infrastructureService;
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
        }
        infrastructure.setHealthStatus(evaluateHealth(infrastructure.getId()));
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
