package br.com.casamento.domain.gift;

import br.com.casamento.domain.guest.Guest;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "gift_purchase_marks")
public class GiftPurchaseMark extends PanacheEntityBase {

    @Id
    @GeneratedValue
    @Column(columnDefinition = "uuid")
    public UUID id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "gift_item_id", nullable = false, unique = true)
    public GiftItem giftItem;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "guest_id", nullable = false)
    public Guest guest;

    @Column(name = "purchased_at", nullable = false, updatable = false)
    public OffsetDateTime purchasedAt;

    @PrePersist
    void prePersist() {
        purchasedAt = OffsetDateTime.now();
    }

    public static GiftPurchaseMark findByGiftItem(GiftItem item) {
        return find("giftItem", item).firstResult();
    }
}
