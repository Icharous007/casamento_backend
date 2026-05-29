package br.com.casamento.domain.event;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "events")
public class Event extends PanacheEntityBase {

    @Id
    @GeneratedValue
    @Column(columnDefinition = "uuid")
    public UUID id;

    @Column(nullable = false, unique = true, length = 100)
    public String slug;

    @Column(nullable = false, length = 255)
    public String title;

    @Column(name = "couple_names", nullable = false, length = 255)
    public String coupleNames;

    @Column(name = "event_date", nullable = false)
    public OffsetDateTime eventDate;

    @Column(name = "venue_name", length = 255)
    public String venueName;

    @Column(name = "venue_address", columnDefinition = "TEXT")
    public String venueAddress;

    @Column(name = "extra_info", columnDefinition = "TEXT")
    public String extraInfo;

    @Column(name = "rsvp_deadline_at")
    public OffsetDateTime rsvpDeadlineAt;

    @Column(name = "gallery_hide_at")
    public OffsetDateTime galleryHideAt;

    @Column(name = "deletion_eligible_at")
    public OffsetDateTime deletionEligibleAt;

    @Column(nullable = false, length = 30)
    public String status = "DRAFT";

    @Column(nullable = false, length = 50)
    public String timezone = "America/Sao_Paulo";

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

    public static Event findBySlug(String slug) {
        return find("slug", slug).firstResult();
    }
}
