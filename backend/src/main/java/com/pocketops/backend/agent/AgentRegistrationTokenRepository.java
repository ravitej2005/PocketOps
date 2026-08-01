package com.pocketops.backend.agent;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AgentRegistrationTokenRepository extends JpaRepository<AgentRegistrationTokenEntity, String> {
    Optional<AgentRegistrationTokenEntity> findByTokenHash(String tokenHash);
}
