package br.com.casamento.domain.media;

import br.com.casamento.domain.event.Event;
import br.com.casamento.domain.guest.Guest;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "media_assets")
public class MediaAsset extends PanacheEntityBase {

    @Id
    @Column(columnDefinition = "uuid")
    public UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "event_id", nullable = false)
    public Event event;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "guest_id")
    public Guest guest;

    @Column(name = "media_type", nullable = false, length = 10)
    public String mediaType; // PHOTO | VIDEO

    @Column(nullable = false, length = 20)
    public String status = "PROCESSING"; // PROCESSING | ACTIVE | HIDDEN | DELETED

    @Column(name = "r2_key", nullable = false, length = 500)
    public String r2Key;

    @Column(name = "r2_thumb_key", length = 500)
    public String r2ThumbKey;

    @Column(name = "r2_display_key", length = 500)
    public String r2DisplayKey;

    @Column(name = "r2_compressed_key", length = 500)
    public String r2CompressedKey; // re-encoded H.264/AAC video, smaller than the raw original in r2Key

    @Column(name = "original_filename", length = 255)
    public String originalFilename;

    @Column(name = "content_type", length = 100)
    public String contentType;

    @Column(length = 500)
    public String caption;

    @Column(name = "file_size_bytes")
    public Long fileSizeBytes;

    @Column(name = "like_count", nullable = false)
    public int likeCount = 0;

    @Column(name = "comment_count", nullable = false)
    public int commentCount = 0;

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
