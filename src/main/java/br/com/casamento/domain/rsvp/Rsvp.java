package br.com.casamento.domain.rsvp;

import br.com.casamento.domain.event.Event;
import br.com.casamento.domain.guest.Guest;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "rsvps")
public class Rsvp extends PanacheEntityBase {

    @Id
    @GeneratedValue
    @Column(columnDefinition = "uuid")
    public UUID id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "guest_id", nullable = false, unique = true)
    public Guest guest;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "event_id", nullable = false)
    public Event event;

    @Column(nullable = false, length = 20)
    public String response;

    @Column(name = "dietary_restrictions", columnDefinition = "TEXT")
    public String dietaryRestrictions;

    @Column(columnDefinition = "TEXT")
    public String allergies;

    @Column(name = "additional_info", columnDefinition = "TEXT")
    public String additionalInfo;

    @Column(name = "responded_at", nullable = false)
    public OffsetDateTime respondedAt;

    @Column(name = "updated_at", nullable = false)
    public OffsetDateTime updatedAt;

    @PrePersist
    void prePersist() {
        respondedAt = OffsetDateTime.now();
        updatedAt = OffsetDateTime.now();
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = OffsetDateTime.now();
    }

    public static Rsvp findByGuest(Guest guest) {
        return find("guest", guest).firstResult();
    }
}
