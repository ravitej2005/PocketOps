package com.pocketops.backend.session;

import com.pocketops.backend.user.UserEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "devices")
public class DeviceEntity {
    @Id
    @Column(length = 36, nullable = false, updatable = false, columnDefinition = "CHAR(36)")
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, columnDefinition = "CHAR(36)")
    private UserEntity user;

    @Column(name = "fcm_token", length = 512)
    private String fcmToken;

    @Column(name = "platform", length = 64, nullable = false)
    private String platform;

    @Column(name = "last_seen_at", nullable = false)
    private Instant lastSeenAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void prePersist() {
        if (id == null) {
            id = UUID.randomUUID().toString();
        }
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
        lastSeenAt = now;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
        lastSeenAt = updatedAt;
    }

    public void setUser(UserEntity user) {
        this.user = user;
    }

    public void setPlatform(String platform) {
        this.platform = platform;
    }
}
