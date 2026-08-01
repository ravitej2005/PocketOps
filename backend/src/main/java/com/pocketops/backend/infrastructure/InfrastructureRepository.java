package com.pocketops.backend.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface InfrastructureRepository extends JpaRepository<InfrastructureEntity, String> {
    List<InfrastructureEntity> findByUser_IdOrderByCreatedAtDesc(String userId);

    Optional<InfrastructureEntity> findByIdAndUser_Id(String id, String userId);
}
