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

    public InfrastructureResourceService(
            AgentRepository agentRepository,
            InfrastructureResourceRepository resourceRepository
    ) {
        this.agentRepository = agentRepository;
        this.resourceRepository = resourceRepository;
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
            entity.setDisplayName(snapshot.getDisplayName());
            entity.setResourceType(snapshot.getResourceType());
            entity.setStatus(snapshot.getStatus());
            entity.setCriticality(snapshot.getCriticality());
            entity.setLastSeenAt(now);
            resourceRepository.save(entity);
        }
    }
}
