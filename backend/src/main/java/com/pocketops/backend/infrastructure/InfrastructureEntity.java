package com.pocketops.backend.infrastructure;

import com.pocketops.backend.user.UserEntity;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "infrastructures")
public class InfrastructureEntity {
    @Id
    @Column(length = 36, nullable = false, updatable = false, columnDefinition = "CHAR(36)")
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, columnDefinition = "CHAR(36)")
    private UserEntity user;

    @Column(length = 255, nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(length = 32, nullable = false)
    private InfrastructureType type;

    @Column(name = "provider_type", length = 64)
    private String providerType;

    @Enumerated(EnumType.STRING)
    @Column(name = "health_status", length = 32, nullable = false)
    private HealthStatus healthStatus;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
            name = "infrastructure_capabilities",
            joinColumns = @JoinColumn(name = "infrastructure_id", columnDefinition = "CHAR(36)")
    )
    @Enumerated(EnumType.STRING)
    @Column(name = "capability", length = 64, nullable = false)
    private Set<Capability> capabilities = new LinkedHashSet<>();

    @Column(name = "last_updated_at", nullable = false)
    private Instant lastUpdatedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void prePersist() {
        if (id == null) {
            id = UUID.randomUUID().toString();
        }
        Instant now = Instant.now();
        createdAt = now;
        lastUpdatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        lastUpdatedAt = Instant.now();
    }

    public String getId() {
        return id;
    }

    public UserEntity getUser() {
        return user;
    }

    public void setUser(UserEntity user) {
        this.user = user;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public InfrastructureType getType() {
        return type;
    }

    public void setType(InfrastructureType type) {
        this.type = type;
    }

    public String getProviderType() {
        return providerType;
    }

    public void setProviderType(String providerType) {
        this.providerType = providerType;
    }

    public HealthStatus getHealthStatus() {
        return healthStatus;
    }

    public void setHealthStatus(HealthStatus healthStatus) {
        this.healthStatus = healthStatus;
    }

    public Set<Capability> getCapabilities() {
        return capabilities;
    }

    public void setCapabilities(Set<Capability> capabilities) {
        this.capabilities = capabilities;
    }

    public Instant getLastUpdatedAt() {
        return lastUpdatedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
