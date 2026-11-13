package br.com.casamento.domain.media;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "gallery_feed_sessions")
public class GalleryFeedSession extends PanacheEntityBase {
    @Id
    public UUID id;

    @Column(name = "event_id", nullable = false)
    public UUID eventId;

    @Column(name = "guest_id", nullable = false)
    public UUID guestId;

    @Column(nullable = false, length = 10)
    public String sort;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "media_ids", nullable = false, columnDefinition = "uuid[]")
    public List<UUID> mediaIds;

    @Column(name = "expires_at", nullable = false)
    public OffsetDateTime expiresAt;
}
