package br.com.casamento.domain.media;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "media_variant_jobs")
public class MediaVariantJob extends PanacheEntityBase {

    @Id
    @Column(name = "media_id", columnDefinition = "uuid")
    public UUID mediaId;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId
    @JoinColumn(name = "media_id", nullable = false)
    public MediaAsset media;

    @Column(nullable = false, length = 20)
    public String status = "PENDING";

    @Column(name = "attempt_count", nullable = false)
    public int attemptCount;

    @Column(name = "available_at", nullable = false)
    public OffsetDateTime availableAt;

    @Column(name = "lease_expires_at")
    public OffsetDateTime leaseExpiresAt;

    @Column(name = "last_error", length = 500)
    public String lastError;

    @Column(name = "created_at", nullable = false, updatable = false)
    public OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    public OffsetDateTime updatedAt;

    @PrePersist
    void prePersist() {
        createdAt = OffsetDateTime.now();
        updatedAt = OffsetDateTime.now();
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = OffsetDateTime.now();
    }
}