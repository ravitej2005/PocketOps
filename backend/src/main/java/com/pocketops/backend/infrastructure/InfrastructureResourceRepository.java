package com.pocketops.backend.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface InfrastructureResourceRepository extends JpaRepository<InfrastructureResourceEntity, String> {
    Optional<InfrastructureResourceEntity> findByInfrastructure_IdAndExternalResourceId(
            String infrastructureId,
            String externalResourceId
    );
}
