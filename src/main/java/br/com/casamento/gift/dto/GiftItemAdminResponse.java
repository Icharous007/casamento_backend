package br.com.casamento.gift.dto;

import br.com.casamento.domain.gift.GiftItem;
import br.com.casamento.domain.gift.GiftPurchaseMark;

import java.time.OffsetDateTime;
import java.util.UUID;

public record GiftItemAdminResponse(
        UUID id,
        String title,
        String description,
        String externalUrl,
        String imageUrl,
        String priceRange,
        int displayOrder,
        String status,
        boolean visibleToGuests,
        PurchasedByInfo purchasedBy,
        OffsetDateTime purchasedAt,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
    public record PurchasedByInfo(UUID guestId, String displayName) {}

    public static GiftItemAdminResponse from(GiftItem item, GiftPurchaseMark mark) {
        PurchasedByInfo purchasedBy = null;
        OffsetDateTime purchasedAt = null;
        if (mark != null) {
            String name = mark.guest.name;
            purchasedBy = new PurchasedByInfo(mark.guest.id, name);
            purchasedAt = mark.purchasedAt;
        }
        return new GiftItemAdminResponse(
                item.id,
                item.title,
                item.description,
                item.externalUrl,
                item.imageUrl,
                item.priceRange,
                item.displayOrder,
                item.status,
                item.visibleToGuests,
                purchasedBy,
                purchasedAt,
                item.createdAt,
                item.updatedAt
        );
    }
}
