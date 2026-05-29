package br.com.casamento.auth.entity;

import br.com.casamento.domain.guest.Guest;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "guest_access_tokens")
public class GuestAccessToken extends PanacheEntityBase {

    @Id
    @GeneratedValue
    @Column(columnDefinition = "uuid")
    public UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "guest_id", nullable = false)
    public Guest guest;

    @Column(name = "token_hash", nullable = false, unique = true, length = 255)
    public String tokenHash;

    @Column(nullable = false)
    public boolean revoked = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    public OffsetDateTime createdAt;

    @PrePersist
    void prePersist() {
        createdAt = OffsetDateTime.now();
    }

    public static GuestAccessToken findByTokenHash(String tokenHash) {
        return find("tokenHash", tokenHash).firstResult();
    }
}
