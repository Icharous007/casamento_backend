package br.com.casamento.domain.media;

import br.com.casamento.domain.guest.Guest;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "media_likes")
public class MediaLike extends PanacheEntityBase {

    @Id
    @GeneratedValue
    @Column(columnDefinition = "uuid")
    public UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "media_id", nullable = false)
    public MediaAsset media;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "guest_id", nullable = false)
    public Guest guest;

    @Column(name = "created_at", nullable = false, updatable = false)
    public OffsetDateTime createdAt;

    @PrePersist
    void prePersist() {
        createdAt = OffsetDateTime.now();
    }

    public static boolean existsByMediaAndGuest(MediaAsset media, Guest guest) {
        return count("media = ?1 AND guest = ?2", media, guest) > 0;
    }

    public static MediaLike findByMediaAndGuest(MediaAsset media, Guest guest) {
        return find("media = ?1 AND guest = ?2", media, guest).firstResult();
    }
}
