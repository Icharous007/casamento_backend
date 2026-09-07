package br.com.casamento.domain.media;

import br.com.casamento.domain.guest.Guest;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "media_upload_intents")
public class MediaUploadIntent extends PanacheEntityBase {

    @Id
    @Column(columnDefinition = "uuid")
    public UUID id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId
    @JoinColumn(name = "id", nullable = false)
    public MediaAsset media;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "guest_id", nullable = false)
    public Guest guest;

    @Column(name = "idempotency_key", nullable = false, length = 128)
    public String idempotencyKey;

    @Column(name = "expected_content_type", nullable = false, length = 100)
    public String expectedContentType;

    @Column(name = "expected_file_size", nullable = false)
    public long expectedFileSize;

    @Column(nullable = false, length = 20)
    public String status = "PENDING";

    @Column(name = "expires_at", nullable = false)
    public OffsetDateTime expiresAt;

    @Column(name = "completed_at")
    public OffsetDateTime completedAt;

    @Column(name = "failure_code", length = 80)
    public String failureCode;

    @Column(name = "created_at", nullable = false, updatable = false)
    public OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    public OffsetDateTime updatedAt;

    public static MediaUploadIntent findByGuestAndIdempotencyKey(UUID guestId, String idempotencyKey) {
        return find("guest.id = ?1 AND idempotencyKey = ?2", guestId, idempotencyKey).firstResult();
    }

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