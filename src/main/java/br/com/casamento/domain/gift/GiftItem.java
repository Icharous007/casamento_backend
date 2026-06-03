package br.com.casamento.domain.gift;

import br.com.casamento.domain.event.Event;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "gift_items")
public class GiftItem extends PanacheEntityBase {

    @Id
    @GeneratedValue
    @Column(columnDefinition = "uuid")
    public UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "event_id", nullable = false)
    public Event event;

    @Column(nullable = false, length = 255)
    public String title;

    @Column(columnDefinition = "TEXT")
    public String description;

    @Column(name = "external_url", columnDefinition = "TEXT")
    public String externalUrl;

    @Column(name = "image_url", columnDefinition = "TEXT")
    public String imageUrl;

    @Column(name = "price_range", length = 50)
    public String priceRange;

    @Column(name = "display_order", nullable = false)
    public int displayOrder = 0;

    @Column(nullable = false, length = 20)
    public String status = "AVAILABLE";

    @Column(name = "visible_to_guests", nullable = false)
    public boolean visibleToGuests = true;

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

    public static List<GiftItem> listVisibleByEvent(UUID eventId) {
        return list("event.id = ?1 AND visibleToGuests = true AND status <> 'UNAVAILABLE' ORDER BY displayOrder ASC, createdAt ASC", eventId);
    }

    public static List<GiftItem> listByEvent(UUID eventId) {
        return list("event.id = ?1 ORDER BY displayOrder ASC, createdAt ASC", eventId);
    }
}
