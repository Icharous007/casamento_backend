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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "gift_item_id", nullable = false)
    public GiftItem giftItem;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "guest_id")
    public Guest guest;

    @Column(name = "giver_name", length = 255)
    public String giverName;

    @Column(name = "giver_phone", length = 30)
    public String giverPhone;

    @Column(name = "giver_message", columnDefinition = "TEXT")
    public String giverMessage;

    @Column(name = "payment_method", nullable = false, length = 20)
    public String paymentMethod = "MANUAL";

    @Column(name = "paid_amount", precision = 10, scale = 2)
    public java.math.BigDecimal paidAmount;

    @Column(name = "payment_status", nullable = false, length = 20)
    public String paymentStatus = "UNVERIFIED";

    @Column(name = "purchased_at", nullable = false, updatable = false)
    public OffsetDateTime purchasedAt;

    @PrePersist
    void prePersist() {
        purchasedAt = OffsetDateTime.now();
    }

    public static java.util.List<GiftPurchaseMark> listByGiftItem(GiftItem item) {
        return list("giftItem = ?1 ORDER BY purchasedAt DESC", item);
    }

    public static GiftPurchaseMark findByGiftItem(GiftItem item) {
        return find("giftItem = ?1 ORDER BY purchasedAt DESC", item).firstResult();
    }
}
