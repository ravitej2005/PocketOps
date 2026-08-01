package com.pocketops.backend.agent;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface AgentRepository extends JpaRepository<AgentEntity, String> {
    Optional<AgentEntity> findByInfrastructure_Id(String infrastructureId);

    Optional<AgentEntity> findByIdAndInfrastructure_Id(String id, String infrastructureId);

    List<AgentEntity> findByStatusAndLastSeenAtBefore(AgentStatus status, Instant cutoff);
}
