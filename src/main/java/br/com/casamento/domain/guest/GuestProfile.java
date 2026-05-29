package br.com.casamento.domain.guest;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "guest_profiles")
public class GuestProfile extends PanacheEntityBase {

    @Id
    @GeneratedValue
    @Column(columnDefinition = "uuid")
    public UUID id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "guest_id", nullable = false, unique = true)
    public Guest guest;

    @Column(name = "display_name", length = 255)
    public String displayName;

    @Column(length = 255)
    public String email;

    @Column(length = 30)
    public String phone;

    @Column(name = "dietary_restrictions", columnDefinition = "TEXT")
    public String dietaryRestrictions;

    @Column(columnDefinition = "TEXT")
    public String allergies;

    @Column(name = "accepted_terms", nullable = false)
    public boolean acceptedTerms = false;

    @Column(name = "accepted_terms_at")
    public OffsetDateTime acceptedTermsAt;

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

    public static GuestProfile findByGuest(Guest guest) {
        return find("guest", guest).firstResult();
    }
}
