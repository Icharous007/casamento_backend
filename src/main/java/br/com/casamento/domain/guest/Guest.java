package br.com.casamento.domain.guest;

import br.com.casamento.domain.event.Event;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "guests")
public class Guest extends PanacheEntityBase {

    @Id
    @GeneratedValue
    @Column(columnDefinition = "uuid")
    public UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "event_id", nullable = false)
    public Event event;

    @Column(nullable = false, length = 255)
    public String name;

    @Column(nullable = false, length = 20)
    public String status = "INVITED";

    @Column(name = "phone_e164", length = 20)
    public String phoneE164;

    /** IMPORTED | SELF_REGISTERED */
    @Column(nullable = false, length = 30)
    public String source = "IMPORTED";

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

    public static Guest findByEventAndPhone(java.util.UUID eventId, String phoneE164) {
        return find("event.id = ?1 AND phoneE164 = ?2", eventId, phoneE164).firstResult();
    }
}
