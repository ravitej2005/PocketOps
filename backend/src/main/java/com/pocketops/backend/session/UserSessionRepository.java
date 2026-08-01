package com.pocketops.backend.session;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface UserSessionRepository extends JpaRepository<UserSessionEntity, String> {
    List<UserSessionEntity> findByUser_IdAndRevokedAtIsNullOrderByCreatedAtDesc(String userId);

    Optional<UserSessionEntity> findByIdAndUser_Id(String id, String userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select session from UserSessionEntity session where session.id = :id and session.user.id = :userId")
    Optional<UserSessionEntity> findByIdAndUser_IdForUpdate(@Param("id") String id, @Param("userId") String userId);

    List<UserSessionEntity> findByUser_IdAndIdNotAndRevokedAtIsNull(String userId, String id);

    long countByUser_IdAndRevokedAtIsNull(String userId);

    List<UserSessionEntity> findByUser_IdAndRevokedAtIsNull(String userId);

    long deleteByExpiresAtBefore(Instant cutoff);

    @Modifying
    @Query("update UserSessionEntity session set session.revokedAt = :revokedAt where session.id = :id and session.revokedAt is null")
    int revokeById(@Param("id") String id, @Param("revokedAt") Instant revokedAt);
}
