package br.com.casamento.gift.dto;

import br.com.casamento.domain.gift.GiftItem;
import br.com.casamento.domain.gift.GiftPurchaseMark;

import java.time.OffsetDateTime;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record GiftItemAdminResponse(
        UUID id,
        String title,
        String description,
        String externalUrl,
        String imageUrl,
        String priceRange,
        BigDecimal priceAmount,
        String category,
        int displayOrder,
        String status,
        boolean visibleToGuests,
        PurchasedByInfo purchasedBy,
        OffsetDateTime purchasedAt,
        List<ContributionInfo> contributions,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
    public record PurchasedByInfo(UUID guestId, String displayName) {}
    public record ContributionInfo(UUID id, String giverName, String giverPhone, String message,
                                   String paymentMethod, String paymentStatus, BigDecimal amount,
                                   OffsetDateTime purchasedAt) {}

    public static GiftItemAdminResponse from(GiftItem item, List<GiftPurchaseMark> marks) {
        PurchasedByInfo purchasedBy = null;
        OffsetDateTime purchasedAt = null;
        if (marks != null && !marks.isEmpty()) {
            GiftPurchaseMark latest = marks.getFirst();
            String name = latest.giverName != null ? latest.giverName : latest.guest != null ? latest.guest.name : null;
            purchasedBy = new PurchasedByInfo(latest.guest != null ? latest.guest.id : null, name);
            purchasedAt = latest.purchasedAt;
        }
        return new GiftItemAdminResponse(
                item.id,
                item.title,
                item.description,
                item.externalUrl,
                item.imageUrl,
                item.priceRange,
                item.priceAmount,
                item.category,
                item.displayOrder,
                item.status,
                item.visibleToGuests,
                purchasedBy,
                purchasedAt,
                marks == null ? List.of() : marks.stream().map(mark -> new ContributionInfo(
                    mark.id,
                    mark.giverName != null ? mark.giverName : mark.guest != null ? mark.guest.name : "Convidado",
                    mark.giverPhone != null ? mark.giverPhone : mark.guest != null ? mark.guest.phoneE164 : null,
                    mark.giverMessage,
                    mark.paymentMethod,
                    mark.paymentStatus,
                    mark.paidAmount,
                    mark.purchasedAt
                )).toList(),
                item.createdAt,
                item.updatedAt
        );
    }
}
