package br.com.casamento.domain.wall;

import br.com.casamento.domain.event.Event;
import br.com.casamento.domain.guest.Guest;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "wall_posts")
public class WallPost extends PanacheEntityBase {

    @Id
    @GeneratedValue
    @Column(columnDefinition = "uuid")
    public UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "event_id", nullable = false)
    public Event event;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "guest_id", nullable = false)
    public Guest guest;

    @Column(name = "post_type", nullable = false, length = 10)
    public String postType; // TEXT | AUDIO

    @Column(columnDefinition = "TEXT")
    public String content;

    @Column(name = "r2_audio_key", length = 500)
    public String r2AudioKey;

    @Column(nullable = false, length = 10)
    public String status = "ACTIVE"; // ACTIVE | REMOVED

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
